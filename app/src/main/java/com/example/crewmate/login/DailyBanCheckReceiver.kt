package com.example.crewmate.login

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

/**
 * 매일 새벽 03:00 경 백그라운드에서 차단 목록 업데이트 여부를 확인하는 알람 리시버.
 * - 실행 시 오프라인 모드이면 네트워크 요청 없이 pending 플래그만 기록하여,
 *   이후 사용자가 온라인 모드로 전환하는 순간 즉시 체크하도록 연계합니다.
 */
class DailyBanCheckReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        val appContext = context.applicationContext
        scheduleNextDailyCheck(appContext)

        val prefs = appContext.getSharedPreferences("crewmate_prefs", Context.MODE_PRIVATE)
        val isOfflineMode = prefs.getBoolean("is_offline_mode", false)
        if (isOfflineMode) {
            EmployeeBanGuard.setPendingOnlineCheck(appContext, true)
            return
        }

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO + SupervisorJob()).launch {
            try {
                val creds = runCatching { SecureCredentialStore(appContext).read() }.getOrNull()
                val empNo = (creds as? CredentialReadResult.Available)?.credentials?.employeeNumber
                if (!empNo.isNullOrBlank()) {
                    EmployeeBanGuard.checkAndCacheBanStatus(appContext, empNo)
                }
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        private const val REQUEST_CODE = 9300

        fun scheduleNextDailyCheck(context: Context) {
            val appContext = context.applicationContext
            val alarmManager = appContext.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
            val intent = Intent(appContext, DailyBanCheckReceiver::class.java)
            val pendingIntent = PendingIntent.getBroadcast(
                appContext,
                REQUEST_CODE,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val now = LocalDateTime.now()
            val targetToday = now.toLocalDate().atTime(LocalTime.of(3, 0))
            val nextTrigger = if (now.isBefore(targetToday)) targetToday else targetToday.plusDays(1)
            val triggerMillis = nextTrigger.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerMillis, pendingIntent)
            } else {
                alarmManager.set(AlarmManager.RTC_WAKEUP, triggerMillis, pendingIntent)
            }
        }
    }
}
