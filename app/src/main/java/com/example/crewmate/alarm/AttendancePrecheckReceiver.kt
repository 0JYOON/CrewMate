package com.example.crewmate.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.PowerManager
import android.util.Log
import androidx.core.content.ContextCompat

/**
 * 출무 전 알람 사전 확인 수신기 (Attendance Pre-check Receiver)
 * 
 * 출무 알람 사이렌 예정 시각 2분 전에 OS AlarmManager에 의해 기동되어,
 * 이미 출무가 완료되었는지 래치를 선검사하고, 미출무 상태인 경우
 * [AttendanceVerificationService]를 기동하여 최신 출무 상태를 백그라운드에서 확인합니다.
 */
class AttendancePrecheckReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        val wakeLock = powerManager?.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "CrewMate:AttendancePrecheckWakeLock"
        )
        runCatching { wakeLock?.acquire(5_000L) }

        try {
            val pjtDt = intent.getStringExtra("pjtDt")?.filter { it.isDigit() }
            val trainNo = intent.getStringExtra("trainNo") ?: "-"
            val dptTimeStr = intent.getStringExtra("dptTimeStr") ?: "-"

            val prefs = context.getSharedPreferences("crewmate_prefs", Context.MODE_PRIVATE)
            val isOffline = prefs.getBoolean("is_offline_mode", false)
            if (isOffline) {
                Log.i(TAG, "Offline mode enabled. Skipping attendance precheck.")
                return
            }

            // 이미 출무 완료 래치된 경우 네트워크 서비스 기동 불필요
            if (!pjtDt.isNullOrBlank() && AttendanceCheckInLatch.isCheckedIn(context, pjtDt, trainNo, dptTimeStr)) {
                Log.i(TAG, "Already confirmed check-in via latch for train=$trainNo on $pjtDt. Skipping service.")
                return
            }

            Log.i(TAG, "Triggering AttendanceVerificationService for pre-check: pjtDt=$pjtDt, train=$trainNo, time=$dptTimeStr")
            val serviceIntent = Intent(context, AttendanceVerificationService::class.java).apply {
                putExtra("pjtDt", pjtDt)
                putExtra("trainNo", trainNo)
                putExtra("dptTimeStr", dptTimeStr)
            }

            try {
                ContextCompat.startForegroundService(context, serviceIntent)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to start foreground service, attempting startService", e)
                context.startService(serviceIntent)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in AttendancePrecheckReceiver", e)
        } finally {
            try {
                wakeLock?.let { if (it.isHeld) it.release() }
            } catch (_: Exception) {}
        }
    }

    companion object {
        private const val TAG = "AttendancePrecheckRecv"
    }
}
