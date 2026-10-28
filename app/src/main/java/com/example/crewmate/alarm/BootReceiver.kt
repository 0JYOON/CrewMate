package com.example.crewmate.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.crewmate.data.remote.xcrew.DiaDetailParser
import com.example.crewmate.data.remote.xcrew.DiaDetailSegment
import com.example.crewmate.database.CrewMateDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            Log.i("BootReceiver", "Device rebooted. Re-scheduling today's work alarms from local DB cache.")

            val settingsStore = WorkAlarmSettingsStore(context)
            val settings = settingsStore.loadSettings()
            val autoOfflineEnabled = AutoOfflineManager.isEnabled(context)
            val prefs = context.getSharedPreferences("crewmate_prefs", Context.MODE_PRIVATE)
            val midnightCaptureEnabled = prefs.getBoolean("midnight_auto_capture", false)
            if (midnightCaptureEnabled) {
                MidnightCaptureReceiver.scheduleMidnightCapture(context)
            }
            if (!settings.alarmEnabled && !autoOfflineEnabled) return

            val pendingResult = goAsync()
            CoroutineScope(Dispatchers.IO + SupervisorJob()).launch {
                try {
                    val db = CrewMateDatabase.getInstance(context)
                    val today = LocalDate.now()
                    val todayStr = today.format(DateTimeFormatter.ofPattern("yyyyMMdd", Locale.ROOT))
                    val monthStr = today.format(DateTimeFormatter.ofPattern("yyyyMM", Locale.ROOT))

                    val schedules = db.workScheduleDao().getSchedulesForMonth(monthStr)
                    val todaySchedules = schedules.filter { it.date == todayStr && !it.diaNo.isNullOrBlank() }

                    val parser = DiaDetailParser()
                    val allSegments = mutableListOf<DiaDetailSegment>()

                    todaySchedules.forEach { sched ->
                        val diaNo = sched.diaNo.orEmpty()
                        val key = "$todayStr|$diaNo"
                        val cachedDetail = db.diaDetailDao().getDiaDetail(key)
                        if (cachedDetail != null && cachedDetail.detailJson.isNotBlank()) {
                            runCatching {
                                val detailResponse = parser.parse(cachedDetail.detailJson)
                                allSegments.addAll(detailResponse.segments)
                            }
                        }
                    }

                    if (allSegments.isNotEmpty()) {
                        if (settings.alarmEnabled) {
                            val alarmItems = WorkAlarmRuleEvaluator.evaluate(allSegments, today, settings)
                            WorkAlarmScheduler.scheduleAlarms(context, alarmItems)
                            Log.i("BootReceiver", "Re-scheduled ${alarmItems.size} alarms on boot")
                        }
                        if (autoOfflineEnabled) {
                            AutoOfflineManager.scheduleAutoOffline(context, allSegments, today)
                            Log.i("BootReceiver", "Re-scheduled auto-offline transitions on boot")
                        }
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
