package com.example.crewmate.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED ||
            intent.action == "android.intent.action.QUICKBOOT_POWERON" ||
            intent.action == Intent.ACTION_MY_PACKAGE_REPLACED
        ) {
            Log.i("BootReceiver", "Device rebooted/package updated. Re-scheduling duty schedules from local DB cache.")
            com.example.crewmate.login.DailyBanCheckReceiver.scheduleNextDailyCheck(context)

            val prefs = context.getSharedPreferences("crewmate_prefs", Context.MODE_PRIVATE)
            val midnightCaptureEnabled = prefs.getBoolean("midnight_auto_capture", false)
            val settingsStore = WorkAlarmSettingsStore(context)
            val settings = settingsStore.loadSettings()
            val autoOfflineEnabled = AutoOfflineManager.isEnabled(context)
            val telegramConfigured = com.example.crewmate.telegram.SubwayNowManager.isConfigured(context)
            val hasWidgets = runCatching {
                android.appwidget.AppWidgetManager.getInstance(context)
                    .getAppWidgetIds(android.content.ComponentName(context, com.example.crewmate.widget.TodayDiaWidgetProvider::class.java))
                    .isNotEmpty()
            }.getOrDefault(false)

            if (midnightCaptureEnabled || settings.alarmEnabled || autoOfflineEnabled || telegramConfigured || hasWidgets) {
                MidnightCaptureReceiver.scheduleNextMidnight(context)
            }

            if (!settings.alarmEnabled && !autoOfflineEnabled && !telegramConfigured && !hasWidgets) return

            val pendingResult = goAsync()
            CoroutineScope(Dispatchers.IO + SupervisorJob()).launch {
                try {
                    val result = WorkDutySyncCoordinator.syncFromLocalDatabase(context)
                    Log.i("BootReceiver", "Successfully restored duty schedules on boot: activeAlarms=${result?.activeAlarms?.size ?: 0}")

                    // 홈 화면 위젯도 로컬 DB로부터 즉시 복구
                    runCatching {
                        com.example.crewmate.widget.TodayDiaWidgetUpdater.updateAllWidgets(context)
                        com.example.crewmate.widget.MonthlyScheduleWidgetUpdater.updateAllWidgets(context)
                    }
                } catch (e: Exception) {
                    Log.e("BootReceiver", "Failed to reschedule alarms on boot", e)
                } finally {
                    pendingResult.finish()
                }
            }
        }
    }
}
