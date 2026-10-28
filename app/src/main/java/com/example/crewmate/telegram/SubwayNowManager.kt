package com.example.crewmate.telegram

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.example.crewmate.data.remote.xcrew.DiaDetailSegment
import com.example.crewmate.database.BotCommandHistoryEntity
import com.example.crewmate.database.CrewMateDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

object SubwayNowManager {

    private const val TAG = "SubwayNowManager"
    private val DT_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss", Locale.ROOT)

    fun isConfigured(context: Context): Boolean {
        val config = SubwayNowSettings.getConfig(context)
        return config.isEnabled && config.isVerified && config.chatId.isNotBlank()
    }

    suspend fun dispatchTarget(
        context: Context,
        target: SubwayNowTrackTarget,
        isManual: Boolean = false
    ): Result<TrackResponse> = withContext(Dispatchers.IO) {
        val config = SubwayNowSettings.getConfig(context)
        if (!config.isEnabled) {
            return@withContext Result.failure(IllegalStateException("텔레그램 봇 연동 기능이 비활성화되어 있습니다."))
        }
        if (!config.isVerified || config.chatId.isBlank()) {
            return@withContext Result.failure(IllegalStateException("텔레그램 계정 PIN 인증이 완료되지 않았습니다."))
        }

        val db = CrewMateDatabase.getInstance(context)
        val nowStr = LocalDateTime.now().format(DT_FORMATTER)
        val triggerType = if (isManual) "MANUAL" else "AUTO"

        val historyId = db.botCommandDao().insertHistory(
            BotCommandHistoryEntity(
                sentAt = nowStr,
                trainNo = target.trainNo,
                targetStation = target.targetStation,
                interval = config.trackInterval,
                startTime = target.trackStartTimeStr,
                rawCommand = target.rawCommand,
                triggerType = triggerType,
                status = "PENDING"
            )
        )

        val result = SubwayNowApiClient.sendTrack(config.serverUrl, config.chatId, target.rawCommand)
        if (result.isSuccess) {
            val resp = result.getOrThrow()
            db.botCommandDao().updateStatus(
                id = historyId,
                status = "SUCCESS",
                responseMessage = "추적 상태: ${resp.trackingStatus ?: resp.status}"
            )
            Log.i(TAG, "열차 추적 등록 성공: ${target.rawCommand}")
            Result.success(resp)
        } else {
            val err = result.exceptionOrNull()?.message ?: "알 수 없는 오류"
            db.botCommandDao().updateStatus(
                id = historyId,
                status = "FAILED",
                responseMessage = err
            )
            Log.e(TAG, "열차 추적 등록 실패: $err")
            Result.failure(result.exceptionOrNull() ?: Exception(err))
        }
    }

    suspend fun dispatchScheduledCommand(
        context: Context,
        trainNo: String,
        targetStation: String,
        interval: Int,
        startTime: String,
        rawCommand: String
    ): Result<TrackResponse> = withContext(Dispatchers.IO) {
        val config = SubwayNowSettings.getConfig(context)
        if (!config.isEnabled || !config.isVerified || config.chatId.isBlank()) {
            return@withContext Result.failure(IllegalStateException("설정 미비로 자동 발송이 취소되었습니다."))
        }

        val db = CrewMateDatabase.getInstance(context)
        val nowStr = LocalDateTime.now().format(DT_FORMATTER)

        val historyId = db.botCommandDao().insertHistory(
            BotCommandHistoryEntity(
                sentAt = nowStr,
                trainNo = trainNo,
                targetStation = targetStation,
                interval = interval,
                startTime = startTime,
                rawCommand = rawCommand,
                triggerType = "AUTO",
                status = "PENDING"
            )
        )

        val result = SubwayNowApiClient.sendTrack(config.serverUrl, config.chatId, rawCommand)
        if (result.isSuccess) {
            val resp = result.getOrThrow()
            db.botCommandDao().updateStatus(
                id = historyId,
                status = "SUCCESS",
                responseMessage = "자동 예약 발송 성공"
            )
            Result.success(resp)
        } else {
            val err = result.exceptionOrNull()?.message ?: "서버 응답 오류"
            db.botCommandDao().updateStatus(
                id = historyId,
                status = "FAILED",
                responseMessage = err
            )
            Result.failure(result.exceptionOrNull() ?: Exception(err))
        }
    }

    suspend fun cancelTarget(
        context: Context,
        trainNo: String
    ): Result<Boolean> = withContext(Dispatchers.IO) {
        val config = SubwayNowSettings.getConfig(context)
        if (config.chatId.isBlank()) {
            return@withContext Result.failure(IllegalStateException("Chat ID가 설정되지 않았습니다."))
        }

        val result = SubwayNowApiClient.cancelTrack(config.serverUrl, config.chatId, trainNo)
        if (result.isSuccess) {
            val db = CrewMateDatabase.getInstance(context)
            db.botCommandDao().markCancelledByTrainNo(trainNo)
            Result.success(true)
        } else {
            Result.failure(result.exceptionOrNull() ?: Exception("취소 요청 실패"))
        }
    }

    suspend fun syncTodayDiaSchedule(
        context: Context,
        segments: List<DiaDetailSegment>,
        workDate: LocalDate
    ) = withContext(Dispatchers.IO) {
        val config = SubwayNowSettings.getConfig(context)
        if (!config.isEnabled || !config.isVerified || config.chatId.isBlank()) {
            return@withContext
        }

        val targets = SubwayNowRuleEvaluator.evaluateTargets(
            segments = segments,
            workDate = workDate,
            sendLeadMinutes = config.sendLeadMinutes,
            trackLeadMinutes = config.trackLeadMinutes,
            trackInterval = config.trackInterval
        )

        if (targets.isEmpty()) return@withContext

        val now = LocalDateTime.now()

        // 1. 출근 시 일괄 전송 모드 (sendLeadMinutes < 0)
        if (config.sendLeadMinutes < 0) {
            val db = CrewMateDatabase.getInstance(context)
            for (target in targets) {
                // 이미 성공적으로 등록된 적이 없거나 실패한 건만 발송
                val latest = db.botCommandDao().getLatestByTrainNo(target.trainNo)
                if (latest == null || latest.status != "SUCCESS") {
                    dispatchTarget(context, target, isManual = false)
                }
            }
            return@withContext
        }

        // 2. 예약 전송 시각(N분 전) 스케줄링 모드
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return@withContext

        targets.forEachIndexed { index, target ->
            // 전송 시각이 이미 지난 경우
            if (target.sendDateTime.isBefore(now)) {
                // 아직 출발 전이라면 즉시 전송 시도
                if (target.baseDateTime.isAfter(now)) {
                    val db = CrewMateDatabase.getInstance(context)
                    val latest = runCatching { db.botCommandDao().getLatestByTrainNo(target.trainNo) }.getOrNull()
                    if (latest == null || latest.status != "SUCCESS") {
                        dispatchTarget(context, target, isManual = false)
                    }
                }
                return@forEachIndexed
            }

            // 미래 시각에 AlarmManager 등록
            val intent = Intent(context, SubwayNowReceiver::class.java).apply {
                action = SubwayNowReceiver.ACTION_DISPATCH_TRACK
                putExtra(SubwayNowReceiver.EXTRA_TRAIN_NO, target.trainNo)
                putExtra(SubwayNowReceiver.EXTRA_TARGET_STATION, target.targetStation)
                putExtra(SubwayNowReceiver.EXTRA_INTERVAL, config.trackInterval)
                putExtra(SubwayNowReceiver.EXTRA_START_TIME, target.trackStartTimeStr)
                putExtra(SubwayNowReceiver.EXTRA_RAW_COMMAND, target.rawCommand)
            }

            val requestCode = 9000 + (target.trainNo.filter { it.isDigit() }.takeLast(4).toIntOrNull() ?: index)
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                requestCode,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val triggerEpochMillis = target.sendDateTime
                .atZone(ZoneId.systemDefault())
                .toInstant()
                .toEpochMilli()

            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    alarmManager.setExactAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        triggerEpochMillis,
                        pendingIntent
                    )
                } else {
                    alarmManager.setExact(
                        AlarmManager.RTC_WAKEUP,
                        triggerEpochMillis,
                        pendingIntent
                    )
                }
                Log.i(TAG, "열차 ${target.trainNo} 자동 전송 스케줄 등록: ${target.sendDateTime}")
            } catch (e: Exception) {
                Log.e(TAG, "스케줄 등록 실패: ${e.message}")
            }
        }
    }
}
