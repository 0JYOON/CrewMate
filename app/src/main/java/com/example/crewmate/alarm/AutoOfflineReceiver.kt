package com.example.crewmate.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import android.widget.Toast
import com.example.crewmate.schedule.OfflineCrewPrefetcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

class AutoOfflineReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val prefs = context.getSharedPreferences("crewmate_prefs", Context.MODE_PRIVATE)
        val action = intent.action ?: return
        val windowKey = intent.getStringExtra("window_key").orEmpty()
        val nowStr = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss", Locale.ROOT))

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO + SupervisorJob()).launch {
            try {
                when (action) {
                    ACTION_SET_OFFLINE -> {
                        Log.i(TAG, "Triggering automatic OFFLINE mode switch at $nowStr")
                        AutoOfflineManager.resetReturnSkipped(context)
                        // 오프라인 전환 직전 당일 4일치 승무원 현황 저장 여부 확인 및 미저장 시 통신 저장
                        runCatching {
                            withTimeoutOrNull(8_500L) {
                                OfflineCrewPrefetcher.prefetchWindowForAutoOffline(
                                    context.applicationContext,
                                    LocalDate.now()
                                )
                            }
                        }
                        val editor = prefs.edit()
                            .putBoolean("is_offline_mode", true)
                            .putString(AutoOfflineManager.PREF_LAST_OFFLINE_TIME, "$nowStr (알람 자동전환)")
                        if (windowKey.isNotEmpty()) {
                            editor.putString(AutoOfflineManager.PREF_LAST_TRIGGERED_WINDOW, windowKey)
                        }
                        editor.apply()
                        withContext(Dispatchers.Main) {
                            Toast.makeText(context, "주요 업무 시작: 오프라인 모드로 자동 전환되었습니다.", Toast.LENGTH_SHORT).show()
                        }
                    }
                    ACTION_SET_ONLINE -> {
                        Log.i(TAG, "Triggering automatic ONLINE mode switch at $nowStr")
                        AutoOfflineManager.resetReturnSkipped(context)
                        prefs.edit()
                            .putBoolean("is_offline_mode", false)
                            .putString(AutoOfflineManager.PREF_LAST_ONLINE_TIME, "$nowStr (알람 자동복귀)")
                            .apply()
                        withContext(Dispatchers.Main) {
                            Toast.makeText(context, "업무 정리 완료: 온라인 모드로 자동 복귀되었습니다.", Toast.LENGTH_SHORT).show()
                        }
                    }
                    ACTION_SKIP_ONLINE_RETURN -> {
                        Log.i(TAG, "Triggering online return skip at $nowStr")
                        AutoOfflineManager.skipOnlineReturn(context)
                        withContext(Dispatchers.Main) {
                            Toast.makeText(context, "온라인 자동 복귀가 스킵되었습니다 (오프라인 모드 유지).", Toast.LENGTH_SHORT).show()
                        }
                    }
                }

                // Schedule any subsequent driving window in the same DIA
                AutoOfflineManager.syncFromDatabase(context.applicationContext)
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        private const val TAG = "AutoOfflineReceiver"
        const val ACTION_SET_OFFLINE = "com.example.crewmate.alarm.ACTION_SET_OFFLINE"
        const val ACTION_SET_ONLINE = "com.example.crewmate.alarm.ACTION_SET_ONLINE"
        const val ACTION_SKIP_ONLINE_RETURN = "com.example.crewmate.alarm.ACTION_SKIP_ONLINE_RETURN"
    }
}
