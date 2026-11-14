package com.example.crewmate.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.RectF
import android.os.Build
import android.util.Log
import com.example.crewmate.database.CrewMateDatabase
import com.example.crewmate.data.remote.xcrew.DiaDetailParser
import com.example.crewmate.schedule.isValidActiveDia
import com.example.crewmate.schedule.isNonActive
import com.example.crewmate.schedule.categorizeSegments
import com.example.crewmate.schedule.CategorizedSegment
import com.example.crewmate.schedule.WorkSchedule
import com.example.crewmate.ui.theme.getAppThemeMode
import com.example.crewmate.ui.theme.isDarkThemeActive
import com.example.crewmate.util.DiaCaptureItem
import com.example.crewmate.util.DiaCaptureOption
import com.example.crewmate.util.DiaCaptureRenderer
import com.example.crewmate.util.ScreenshotHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

class MidnightCaptureReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val prefs = context.getSharedPreferences("crewmate_prefs", Context.MODE_PRIVATE)
        val enabled = prefs.getBoolean("midnight_auto_capture", false)
        val alarmEnabled = WorkAlarmSettingsStore(context).loadSettings().alarmEnabled
        val autoOfflineEnabled = AutoOfflineManager.isEnabled(context)
        val telegramConfigured = com.example.crewmate.telegram.SubwayNowManager.isConfigured(context)
        val hasWidgets = runCatching {
            android.appwidget.AppWidgetManager.getInstance(context)
                .getAppWidgetIds(android.content.ComponentName(context, com.example.crewmate.widget.TodayDiaWidgetProvider::class.java))
                .isNotEmpty()
        }.getOrDefault(false)

        if (!enabled && !alarmEnabled && !autoOfflineEnabled && !telegramConfigured && !hasWidgets) return

        Log.i("MidnightCaptureReceiver", "Running midnight duty sync & auto capture check...")
        val pendingResult = goAsync()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                // 0. 자정(00:01) 전환 시 당일 및 익일 알람/자동오프라인/텔레그램 스케줄을 로컬 DB로부터 항상 최신화
                runCatching {
                    WorkDutySyncCoordinator.syncFromLocalDatabase(context)
                }.onFailure { e ->
                    Log.e("MidnightCaptureReceiver", "Failed to sync duty schedules at midnight", e)
                }

                // 0-1. 자정 전환 시 홈 화면 위젯도 익일(오늘) 로컬 DB 기반으로 즉시 갱신
                runCatching {
                    com.example.crewmate.widget.TodayDiaWidgetUpdater.updateAllWidgets(context)
                    com.example.crewmate.widget.MonthlyScheduleWidgetUpdater.updateAllWidgets(context)
                }.onFailure { e ->
                    Log.e("MidnightCaptureReceiver", "Failed to update widgets at midnight", e)
                }

                if (!enabled) return@launch

                val today = LocalDate.now()
                val todayStr = today.format(DateTimeFormatter.BASIC_ISO_DATE)
                val monthStr = today.format(DateTimeFormatter.ofPattern("yyyyMM", Locale.ROOT))
                val db = CrewMateDatabase.getInstance(context)

                // 1. Look up today's work schedule from monthly schedules
                val monthSchedules = db.workScheduleDao().getSchedulesForMonth(monthStr)

                // 야간근무 2일차(12/박 둘째날) 판별
                val yesterday = today.minusDays(1)
                val yesterdayStr = yesterday.format(DateTimeFormatter.BASIC_ISO_DATE)
                val yesterdayMonthStr = yesterday.format(DateTimeFormatter.ofPattern("yyyyMM", Locale.ROOT))
                val yesterdaySchedules = db.workScheduleDao().getSchedulesForMonth(yesterdayMonthStr).filter {
                    it.date == yesterdayStr
                }

                val isNightDay2 = monthSchedules.any { sched ->
                    sched.date == todayStr && (
                        sched.alcSpecDvCd?.trim() == "12" ||
                        sched.isContinuation ||
                        sched.diaNo?.startsWith("~") == true
                    )
                } || (
                    yesterdaySchedules.any { it.alcSpecDvCd?.trim() == "11" } &&
                    monthSchedules.any { it.date == todayStr && it.alcSpecDvCd?.trim() == "12" }
                )

                if (isNightDay2) {
                    Log.i("MidnightCaptureReceiver", "Today ($todayStr) is Night Shift Day 2 (12/박 둘째날). Auto capture skipped as it was captured on Day 1.")
                    return@launch
                }

                val todaySchedules = monthSchedules.filter {
                    it.date == todayStr && it.isValidActiveDia()
                }

                // Check if there is an explicit rest day for today
                val isTodayRest = monthSchedules.any {
                    it.date == todayStr && (it.isNonActive() || it.alcSpecDvCd?.trim() == "02")
                }
                if (isTodayRest || todaySchedules.isEmpty()) {
                    Log.i("MidnightCaptureReceiver", "Today ($todayStr) is a scheduled rest/non-active day or has no active dia. Auto capture skipped.")
                    return@launch
                }

                val targetSchedules = todaySchedules.toMutableList()
                val entities = mutableListOf<com.example.crewmate.database.DiaDetailEntity>()

                if (targetSchedules.isNotEmpty()) {
                    targetSchedules.forEach { sched ->
                        val diaNo = sched.diaNo.orEmpty().trim()
                        val cleanDiaNo = diaNo.replace("~", "").trim()
                        val entity = db.diaDetailDao().getDiaDetail("${sched.date}|$diaNo")
                            ?: db.diaDetailDao().getDiaDetail("${sched.date}|$cleanDiaNo")
                            ?: db.diaDetailDao().getDiaDetail("$todayStr|$diaNo")
                            ?: db.diaDetailDao().getDiaDetail("$todayStr|$cleanDiaNo")
                            ?: db.diaDetailDao().getLatestDiaDetailByPdiaNo(diaNo)
                            ?: db.diaDetailDao().getLatestDiaDetailByPdiaNo(cleanDiaNo)
                        if (entity != null && entities.none { it.diaKey == entity.diaKey }) {
                            entities.add(entity)
                        }
                    }
                } else {
                    // Direct lookup fallback if work_schedules table is empty but dia_details has today's key
                    entities.addAll(db.diaDetailDao().getDiaDetailsForDate(todayStr))
                }

                if (entities.isNotEmpty()) {
                    val optionName = prefs.getString("midnight_auto_capture_option", DiaCaptureOption.TIMELINE_SUMMARY.name)
                        ?: DiaCaptureOption.TIMELINE_SUMMARY.name
                    val option = DiaCaptureOption.entries.find { it.name == optionName } ?: DiaCaptureOption.TIMELINE_SUMMARY
                    val hideSandwichedGamsi = prefs.getBoolean("hide_sandwiched_gamsi", true)

                    val parser = DiaDetailParser()
                    val captureItems = mutableListOf<DiaCaptureItem>()

                    entities.forEach { entity ->
                        val parsed = parser.parse(entity.detailJson)
                        if (parsed.segments.isNotEmpty()) {
                            val matchedEntity = targetSchedules.find {
                                val clean = it.diaNo?.replace("~", "")?.trim().orEmpty()
                                clean == entity.pdiaNo.trim()
                            }
                            val sched = if (matchedEntity != null) {
                                WorkSchedule(
                                    workDate = matchedEntity.date,
                                    diaNo = matchedEntity.diaNo,
                                    trainNo = matchedEntity.trainNo,
                                    trainNo2 = matchedEntity.trainNo2,
                                    startTime = matchedEntity.startTime ?: parsed.segments.firstOrNull { !it.dptTm.isNullOrBlank() }?.dptTm,
                                    endTime = matchedEntity.endTime ?: parsed.segments.lastOrNull { !it.arvTm.isNullOrBlank() }?.arvTm,
                                    nonActiveType = matchedEntity.nonActiveType,
                                    isContinuation = matchedEntity.isContinuation,
                                    isHoliday = matchedEntity.isHoliday,
                                    alcSpecDvCd = matchedEntity.alcSpecDvCd,
                                    hldyYn = matchedEntity.hldyYn,
                                    isChanged = matchedEntity.isChanged
                                )
                            } else {
                                WorkSchedule(
                                    workDate = todayStr,
                                    diaNo = entity.pdiaNo.trim(),
                                    startTime = parsed.segments.firstOrNull { !it.dptTm.isNullOrBlank() }?.dptTm,
                                    endTime = parsed.segments.lastOrNull { !it.arvTm.isNullOrBlank() }?.arvTm
                                )
                            }

                            captureItems.add(
                                DiaCaptureItem(
                                    schedule = sched,
                                    detail = parsed
                                )
                            )
                        }
                    }

                    if (captureItems.isNotEmpty()) {
                        val showPrepDivider = prefs.getBoolean("show_prep_divider", true)
                        val appThemeMode = context.getAppThemeMode()
                        val isDark = context.isDarkThemeActive(appThemeMode)
                        val bitmap = DiaCaptureRenderer.renderToBitmap(
                            context = context,
                            headerTitle = "승무 다이아 상세",
                            workDate = today,
                            dias = captureItems,
                            option = option,
                            isDark = isDark,
                            showPrepDivider = showPrepDivider
                        )
                        if (bitmap != null) {
                            ScreenshotHelper.saveBitmapToGallery(context, bitmap, option.filePrefix)
                            Log.i("MidnightCaptureReceiver", "Auto capture saved with option: ${option.name}")
                        } else {
                            Log.e("MidnightCaptureReceiver", "Failed to render DIA bitmap for $todayStr")
                        }
                    } else {
                        Log.i("MidnightCaptureReceiver", "No valid DIA segments to capture for $todayStr")
                    }
                } else {
                    Log.i("MidnightCaptureReceiver", "No DIA details cached for $todayStr. Auto capture skipped.")
                }
            } catch (e: Exception) {
                Log.e("MidnightCaptureReceiver", "Auto capture failed", e)
            } finally {
                scheduleNextMidnight(context)
                pendingResult.finish()
            }
        }
    }

    companion object {
        fun scheduleMidnightCapture(context: Context) {
            scheduleNextMidnight(context)
        }

        fun cancelMidnightCapture(context: Context) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
            val intent = Intent(context, MidnightCaptureReceiver::class.java)
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                9922,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            alarmManager.cancel(pendingIntent)
        }

        fun scheduleNextMidnight(context: Context) {
            val prefs = context.getSharedPreferences("crewmate_prefs", Context.MODE_PRIVATE)
            val enabled = prefs.getBoolean("midnight_auto_capture", false)
            val alarmEnabled = WorkAlarmSettingsStore(context).loadSettings().alarmEnabled
            val autoOfflineEnabled = AutoOfflineManager.isEnabled(context)
            val telegramConfigured = com.example.crewmate.telegram.SubwayNowManager.isConfigured(context)
            val shouldKeepScheduled = enabled || alarmEnabled || autoOfflineEnabled || telegramConfigured
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return

            val intent = Intent(context, MidnightCaptureReceiver::class.java)
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                9922,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            if (!shouldKeepScheduled) {
                alarmManager.cancel(pendingIntent)
                return
            }

            val tomorrowMidnight = LocalDate.now().plusDays(1).atTime(0, 1)
            val epochMilli = tomorrowMidnight.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    if (alarmManager.canScheduleExactAlarms()) {
                        alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, epochMilli, pendingIntent)
                    } else {
                        alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, epochMilli, pendingIntent)
                    }
                } else {
                    alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, epochMilli, pendingIntent)
                }
            } catch (e: Exception) {
                Log.e("MidnightCaptureReceiver", "Failed to schedule next midnight", e)
            }
        }
    }
}
