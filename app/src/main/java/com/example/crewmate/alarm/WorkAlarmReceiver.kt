package com.example.crewmate.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.PowerManager
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.time.format.DateTimeFormatter
import java.util.Locale

class WorkAlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        val wakeLock = powerManager?.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "CrewMate:AlarmWakeLock"
        )
        runCatching { wakeLock?.acquire(20_000L) }

        val ruleTitle = intent.getStringExtra("ruleTitle") ?: intent.getStringExtra("title") ?: "오늘의 근무 알람"
        val cat = intent.getStringExtra("cat") ?: "운전"
        val trainNo = intent.getStringExtra("trainNo") ?: "-"
        val dptStn = intent.getStringExtra("dptStn") ?: "-"
        val dptTimeStr = intent.getStringExtra("dptTimeStr") ?: "-"
        val isTest = intent.getBooleanExtra("isTest", false)
        val targetPjtDt = intent.getStringExtra("pjtDt")?.filter { it.isDigit() }

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO + SupervisorJob()).launch {
            try {
                val settingsStore = WorkAlarmSettingsStore(context)
                val settings = settingsStore.loadSettings()

                if (!settings.alarmEnabled && !isTest) return@launch

                val payload = AttendancePrecheckCoordinator.SirenPayload(
                    ruleTitle = ruleTitle,
                    cat = cat,
                    trainNo = trainNo,
                    dptStn = dptStn,
                    dptTimeStr = dptTimeStr,
                    pjtDt = targetPjtDt,
                    soundMode = settings.soundMode
                )

                // 출무 알람인 경우: 사전확인 조율기 위임 + 3단계 방어 로직으로 실제 출무 여부 정밀 확인 및 오발동 차단
                if (cat == "출무" && !isTest) {
                    try {
                        val targetTrainNo = trainNo.trim()
                        val targetTime = dptTimeStr.replace(":", "").filter { it.isDigit() }

                        // 1단계: 출무 완료 래치 검사 (이미 한번이라도 출무 완료로 확인된 건은 무조건 사이렌 취소)
                        if (AttendanceCheckInLatch.isCheckedIn(context, targetPjtDt, targetTrainNo, targetTime)) {
                            Log.i("WorkAlarmReceiver", "AttendanceCheckInLatch: already checked in for train=$targetTrainNo, time=$targetTime on date=$targetPjtDt. Cancelling 출무 siren.")
                            return@launch
                        }

                        // 2단계: T-15초 사전확인 서비스가 현재 서버 조회 진행 중이면 사이렌 발동을 서비스에 위임하고 즉시 종료
                        val coordKey = AttendancePrecheckCoordinator.keyOf(targetPjtDt, targetTrainNo, targetTime)
                        if (!coordKey.isNullOrBlank() && AttendancePrecheckCoordinator.tryDefer(coordKey, payload)) {
                            Log.i("WorkAlarmReceiver", "Deferred 출무 siren for $coordKey to in-flight AttendanceVerificationService.")
                            return@launch
                        }

                        // 3단계: 앱 세션이 살아있는 경우 실시간 초고속 재확인 (최대 5초)
                        val prefs = context.getSharedPreferences("crewmate_prefs", Context.MODE_PRIVATE)
                        val isOffline = prefs.getBoolean("is_offline_mode", false)
                        if (!isOffline && !targetPjtDt.isNullOrBlank() && targetPjtDt.length == 8) {
                            val liveSession = com.example.crewmate.data.remote.xcrew.XcrewSessionRegistry.current()
                            if (liveSession != null) {
                                try {
                                    val empNm = liveSession.userInfo.value?.first.orEmpty()
                                    val dataSource = com.example.crewmate.data.remote.xcrew.XcrewWebViewXhrDataSource(
                                        liveSession,
                                        com.example.crewmate.data.remote.xcrew.XcrewWorkScheduleParser()
                                    )
                                    val liveList = withTimeoutOrNull(5_000L) {
                                        dataSource.fetchAttendanceStatus(empNm, targetPjtDt)
                                    }
                                    if (liveList != null && liveList.isNotEmpty()) {
                                        AttendanceSnapshotStore.save(context, targetPjtDt, liveList)
                                        if (AttendanceCheckInLatch.isCheckedIn(context, targetPjtDt, targetTrainNo, targetTime)) {
                                            Log.i("WorkAlarmReceiver", "Live session re-check: actual attendance confirmed for train=$targetTrainNo, time=$targetTime on date=$targetPjtDt. Cancelling 출무 siren.")
                                            return@launch
                                        }
                                    }
                                } catch (e: Exception) {
                                    Log.w("WorkAlarmReceiver", "Live session re-check failed", e)
                                }
                            }
                        }

                        // 4단계: 로컬 스냅샷 기반 검사
                        val datesToCheck = if (!targetPjtDt.isNullOrBlank() && targetPjtDt.length == 8) {
                            listOf(targetPjtDt)
                        } else {
                            val today = java.time.LocalDate.now()
                            listOf(
                                today.format(DateTimeFormatter.ofPattern("yyyyMMdd", Locale.ROOT)),
                                today.minusDays(1).format(DateTimeFormatter.ofPattern("yyyyMMdd", Locale.ROOT)),
                                today.plusDays(1).format(DateTimeFormatter.ofPattern("yyyyMMdd", Locale.ROOT))
                            )
                        }

                        val statusMap = mutableMapOf<String, List<com.example.crewmate.data.remote.xcrew.AttendanceStatusDto>>()
                        for (dStr in datesToCheck) {
                            val snapshot = AttendanceSnapshotStore.load(context, dStr)
                            if (snapshot != null) {
                                statusMap[dStr] = snapshot.statuses
                            }
                        }

                        val shouldCancel = AttendanceAlarmCancellationChecker.shouldCancelSiren(
                            cachedStatusesByDate = statusMap,
                            targetPjtDt = targetPjtDt,
                            targetTrainNo = targetTrainNo,
                            targetTime = targetTime
                        )

                        if (shouldCancel) {
                            Log.i("WorkAlarmReceiver", "Actual attendance confirmed from DB snapshot for train=$targetTrainNo, time=$targetTime on date=$targetPjtDt. Cancelling 출무 siren.")
                            return@launch
                        }
                    } catch (e: Exception) {
                        Log.w("WorkAlarmReceiver", "Failed to check attendance status before siren", e)
                    }
                }

                AttendanceSirenDispatcher.fire(context, payload)
            } catch (e: Exception) {
                Log.e("WorkAlarmReceiver", "Unhandled exception in WorkAlarmReceiver", e)
            } finally {
                try {
                    wakeLock?.let { if (it.isHeld) it.release() }
                } catch (_: Exception) {}
                pendingResult.finish()
            }
        }
    }
}

object AttendanceAlarmCancellationChecker {
    fun shouldCancelSiren(
        cachedStatusesByDate: Map<String, List<com.example.crewmate.data.remote.xcrew.AttendanceStatusDto>>,
        targetPjtDt: String?,
        targetTrainNo: String?,
        targetTime: String?
    ): Boolean {
        val cleanTargetTrain = targetTrainNo?.trim()?.replace("-", "").orEmpty()
        val cleanTargetTime = targetTime?.replace(":", "")?.filter { it.isDigit() }.orEmpty()
        val cleanTargetDate = targetPjtDt?.filter { it.isDigit() }

        val datesToCheck = if (!cleanTargetDate.isNullOrBlank() && cleanTargetDate.length == 8) {
            listOf(cleanTargetDate)
        } else {
            cachedStatusesByDate.keys.toList()
        }

        for (dStr in datesToCheck) {
            val statuses = cachedStatusesByDate[dStr] ?: continue
            val matchedItem = statuses.firstOrNull { dto ->
                val dtoTrain = dto.trnNo?.trim()?.replace("-", "").orEmpty()
                val dtoTime = dto.gtwcEpctTm?.replace(":", "")?.filter { it.isDigit() }.orEmpty()

                // dtoTrain이 비어있을 때 endsWith("")가 true로 오판정되는 버그 차단
                val matchesTrain = cleanTargetTrain.isNotEmpty() && dtoTrain.isNotEmpty() && (dtoTrain == cleanTargetTrain || dtoTrain.endsWith(cleanTargetTrain) || cleanTargetTrain.endsWith(dtoTrain))
                val matchesTime = cleanTargetTime.isNotEmpty() && dtoTime == cleanTargetTime

                if (matchesTrain && matchesTime) true
                else if (matchesTrain && cleanTargetTime.isEmpty()) true
                else if (matchesTime && cleanTargetTrain.isEmpty()) true
                else false
            }

            if (matchedItem != null && matchedItem.isCheckedIn) {
                return true
            }
        }

        return false
    }
}
