package com.example.crewmate.alarm

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.crewmate.R
import com.example.crewmate.data.remote.xcrew.XcrewSessionRegistry
import com.example.crewmate.data.remote.xcrew.XcrewWebViewXhrDataSource
import com.example.crewmate.data.remote.xcrew.XcrewWorkScheduleParser
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * 출무 전 알람 사전 검증 포그라운드 서비스 (Attendance Verification Service)
 *
 * 사이렌 발동 15초 전([AttendancePrecheckCoordinator.PRECHECK_LEAD_SECONDS]) [AttendancePrecheckReceiver]에 의해 기동되어,
 * 백그라운드 환경에서 최신 출무 현황을 안전하게 확인하고 [AttendanceCheckInLatch]에 기록합니다.
 * 만약 사이렌 시각(T)에 확인이 아직 진행 중이어서 [WorkAlarmReceiver]가 발동을 위임한 경우,
 * 확인 완료 즉시(또는 최대 지연 한도 도달 시) 출무 여부에 따라 사이렌 취소 또는 발동을 수행합니다.
 */
class AttendanceVerificationService : Service() {

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val pjtDt = intent?.getStringExtra("pjtDt")?.filter { it.isDigit() }
        val trainNo = intent?.getStringExtra("trainNo") ?: "-"
        val dptTimeStr = intent?.getStringExtra("dptTimeStr") ?: "-"

        val coordKey = AttendancePrecheckCoordinator.keyOf(pjtDt, trainNo, dptTimeStr)
        if (!coordKey.isNullOrBlank()) {
            AttendancePrecheckCoordinator.markInFlight(coordKey)
        }

        startForegroundNotification()

        serviceScope.launch {
            try {
                if (!pjtDt.isNullOrBlank() && pjtDt.length == 8) {
                    val completedInTime = withTimeoutOrNull(AttendancePrecheckCoordinator.MAX_IN_FLIGHT_WINDOW_MILLIS) {
                        performVerification(pjtDt, trainNo, dptTimeStr)
                        true
                    } ?: false
                    if (!completedInTime) {
                        recordAudit(pjtDt, "사전확인 제한시간(25초) 초과")
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Error during attendance precheck service execution", e)
                recordAudit(pjtDt.orEmpty(), "사전확인 예외: ${e.javaClass.simpleName}")
            } finally {
                val deferredPayload = if (!coordKey.isNullOrBlank()) {
                    AttendancePrecheckCoordinator.complete(coordKey)
                } else {
                    null
                }

                val isCheckedIn = !pjtDt.isNullOrBlank() &&
                    AttendanceCheckInLatch.isCheckedIn(applicationContext, pjtDt, trainNo, dptTimeStr)

                if (isCheckedIn) {
                    if (deferredPayload != null) {
                        Log.i(TAG, "Deferred siren cancelled after pre-check confirmed attendance for $coordKey")
                        recordAudit(pjtDt.orEmpty(), "위임된 사이렌 취소: 출무 완료 확인됨")
                    } else {
                        AttendanceSirenDispatcher.stopRecentSirenIfMatches(
                            applicationContext,
                            pjtDt,
                            trainNo,
                            dptTimeStr
                        )
                    }
                } else if (deferredPayload != null) {
                    Log.i(TAG, "Firing deferred siren for $coordKey (attendance not confirmed)")
                    AttendanceSirenDispatcher.fire(applicationContext, deferredPayload)
                }

                stopForegroundSafely()
                stopSelf()
            }
        }

        return START_NOT_STICKY
    }

    private suspend fun performVerification(pjtDt: String, trainNo: String, dptTimeStr: String) {
        val prefs = getSharedPreferences("crewmate_prefs", Context.MODE_PRIVATE)
        val isOffline = prefs.getBoolean("is_offline_mode", false)
        if (isOffline) {
            recordAudit(pjtDt, "오프라인 모드 활성화로 사전확인 스킵")
            return
        }

        // 1. 이미 래치에 출무 완료로 기록되어 있는지 확인
        if (AttendanceCheckInLatch.isCheckedIn(applicationContext, pjtDt, trainNo, dptTimeStr)) {
            Log.i(TAG, "Already latched as checked-in for train=$trainNo on $pjtDt. Skipping network.")
            recordAudit(pjtDt, "이미 출무 완료 래치됨")
            return
        }

        // 2. 포그라운드 UI의 활성 웹뷰 세션이 존재하는 경우 우선 활용 (초고속 조회)
        val liveSession = XcrewSessionRegistry.current()
        if (liveSession != null) {
            Log.i(TAG, "Live UI session detected. Fetching attendance status via live session...")
            try {
                val empNm = liveSession.userInfo.value?.first.orEmpty()
                val dataSource = XcrewWebViewXhrDataSource(liveSession, XcrewWorkScheduleParser())
                val liveList = withTimeoutOrNull(5_000L) {
                    dataSource.fetchAttendanceStatus(empNm, pjtDt)
                }
                if (liveList != null && liveList.isNotEmpty()) {
                    AttendanceSnapshotStore.save(applicationContext, pjtDt, liveList)
                    val isNowChecked = AttendanceCheckInLatch.isCheckedIn(applicationContext, pjtDt, trainNo, dptTimeStr)
                    recordAudit(pjtDt, if (isNowChecked) "실시간 세션 조회 성공: 출무 완료 확인됨" else "실시간 세션 조회 성공: 아직 출무전")
                    return
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to fetch via live session, falling back to headless verifier", e)
            }
        }

        // 3. 라이브 세션이 없거나 실패한 경우: 화면 없는 HeadlessWebView를 통해 로그인 및 조회
        Log.i(TAG, "Running headless attendance verification for date $pjtDt...")
        val fetchedList = withTimeoutOrNull(22_000L) {
            HeadlessAttendanceVerifier.verifyAndSync(applicationContext, pjtDt)
        }

        if (fetchedList != null && fetchedList.isNotEmpty()) {
            val isCheckedNow = AttendanceCheckInLatch.isCheckedIn(applicationContext, pjtDt, trainNo, dptTimeStr)
            recordAudit(pjtDt, if (isCheckedNow) "헤드리스 조회 성공: 출무 완료 확인됨" else "헤드리스 조회 성공: 아직 출무전")
        } else {
            recordAudit(pjtDt, "헤드리스 조회 실패 또는 빈 응답")
        }
    }

    private fun recordAudit(pjtDt: String, message: String) {
        if (pjtDt.isBlank()) return
        val prefs = getSharedPreferences("crewmate_prefs", Context.MODE_PRIVATE)
        val nowStr = java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss"))
        prefs.edit().putString("attendance_precheck_audit_$pjtDt", "[$nowStr] $message").apply()
        Log.i(TAG, "Audit [$pjtDt]: $message")
    }

    private fun startForegroundNotification() {
        val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("CrewMate")
            .setContentText("출무 여부 확인 중…")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setOngoing(true)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun stopForegroundSafely() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = getSystemService(NotificationManager::class.java)
            val channel = NotificationChannel(
                CHANNEL_ID,
                "출무 사전 확인",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "출무 전 알람 오발동 방지를 위한 백그라운드 출무 여부 확인"
                setShowBadge(false)
                enableVibration(false)
                setSound(null, null)
            }
            notificationManager?.createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }

    companion object {
        private const val TAG = "AttVerificationService"
        private const val CHANNEL_ID = "crewmate_attendance_precheck_channel"
        private const val NOTIFICATION_ID = 8801
    }
}
