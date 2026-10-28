package com.example.crewmate.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.example.crewmate.data.remote.xcrew.DiaDetailParser
import com.example.crewmate.data.remote.xcrew.DiaDetailSegment
import com.example.crewmate.database.CrewMateDatabase
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

data class AutoOfflineSchedule(
    val offlineTriggerTime: LocalDateTime,
    val onlineTriggerTime: LocalDateTime
)

object AutoOfflineManager {
    private const val TAG = "AutoOfflineManager"
    const val PREF_AUTO_OFFLINE = "auto_offline_mode"
    private const val LEGACY_PREF_AUTO_OFFLINE = "auto_offline_enabled"

    const val PREF_SCHEDULED_SUMMARY = "auto_offline_scheduled_summary"
    const val PREF_LAST_OFFLINE_TIME = "auto_offline_last_offline_time"
    const val PREF_LAST_ONLINE_TIME = "auto_offline_last_online_time"
    const val PREF_LAST_TRIGGERED_WINDOW = "auto_offline_last_window_key"
    const val PREF_RETURN_DELAY_MINUTES = "auto_offline_return_delay_minutes"
    const val PREF_RETURN_SKIPPED = "auto_offline_return_skipped"

    private val LOG_TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss", Locale.ROOT)
    private val SUMMARY_TIME_FORMAT = DateTimeFormatter.ofPattern("MM/dd HH:mm", Locale.ROOT)

    fun isEnabled(context: Context): Boolean {
        val prefs = context.getSharedPreferences("crewmate_prefs", Context.MODE_PRIVATE)
        if (prefs.contains(PREF_AUTO_OFFLINE)) {
            return prefs.getBoolean(PREF_AUTO_OFFLINE, false)
        }
        if (prefs.contains(LEGACY_PREF_AUTO_OFFLINE)) {
            val legacy = prefs.getBoolean(LEGACY_PREF_AUTO_OFFLINE, false)
            prefs.edit().putBoolean(PREF_AUTO_OFFLINE, legacy).apply()
            return legacy
        }
        return false
    }

    /**
     * Extracts all [Rank1 (or immediately preceding 감시) - 1min .. first 정리/입고정리 start] windows in a DIA.
     */
    fun calculateOfflineSchedules(
        segments: List<DiaDetailSegment>,
        workDate: LocalDate,
        returnDelayMinutes: Int = 0
    ): List<AutoOfflineSchedule> {
        if (segments.isEmpty()) return emptyList()

        fun parseTime(s: String?): LocalTime? {
            val raw = s?.trim()?.replace(":", "")?.filter { it.isDigit() } ?: return null
            if (raw.length < 4) return null
            val h = raw.substring(0, 2).toIntOrNull() ?: return null
            val m = raw.substring(2, 4).toIntOrNull() ?: return null
            return runCatching { LocalTime.of(if (h >= 24) h % 24 else h, m) }.getOrNull()
        }

        var currentDate = workDate
        var lastTime = LocalTime.MIN
        val dateTimeList = segments.map { seg ->
            val dpt = parseTime(seg.dptTm)
            val arv = parseTime(seg.arvTm)
            if (dpt != null && dpt < lastTime && lastTime.hour >= 18 && dpt.hour <= 12) {
                currentDate = currentDate.plusDays(1)
            }
            if (dpt != null) lastTime = dpt
            val dptDateTime = dpt?.let { currentDate.atTime(it) }
            val arvDateTime = arv?.let {
                val arvDate = if (dpt != null && arv < dpt) currentDate.plusDays(1) else currentDate
                arvDate.atTime(it)
            }
            Triple(seg, dptDateTime, arvDateTime)
        }

        fun isRank1(cat: String): Boolean =
            cat.contains("운전") || cat.contains("승무") || cat.contains("출고준비") || cat.contains("입환")

        fun isWrapUp(cat: String): Boolean =
            cat.contains("정리") || cat.contains("입고정리")

        val windows = mutableListOf<AutoOfflineSchedule>()
        var idx = 0
        while (idx < dateTimeList.size) {
            val rank1Idx = (idx until dateTimeList.size).firstOrNull { i ->
                isRank1(dateTimeList[i].first.pjtHrDvNm.orEmpty())
            } ?: break

            val firstRank1 = dateTimeList[rank1Idx]
            val rank1StartTime = firstRank1.second ?: firstRank1.third
            if (rank1StartTime == null) {
                idx = rank1Idx + 1
                continue
            }

            var offlineTime = rank1StartTime.minusMinutes(1)
            if (rank1Idx > 0) {
                val prevSeg = dateTimeList[rank1Idx - 1]
                if (prevSeg.first.pjtHrDvNm?.contains("감시") == true) {
                    val gamsiStart = prevSeg.second ?: prevSeg.third
                    if (gamsiStart != null) {
                        offlineTime = gamsiStart.minusMinutes(1)
                    }
                }
            }

            var onlineTime: LocalDateTime? = null
            var wrapUpIdx = -1
            for (i in (rank1Idx + 1) until dateTimeList.size) {
                val item = dateTimeList[i]
                if (isWrapUp(item.first.pjtHrDvNm.orEmpty())) {
                    val baseOnlineTime = item.second ?: item.third
                    if (baseOnlineTime != null) {
                        onlineTime = baseOnlineTime.plusMinutes(returnDelayMinutes.toLong())
                        wrapUpIdx = i
                        break
                    }
                }
            }

            if (onlineTime == null) {
                val lastItem = dateTimeList.lastOrNull()
                val baseOnlineTime = lastItem?.third ?: lastItem?.second ?: rank1StartTime.plusHours(4)
                onlineTime = baseOnlineTime.plusMinutes(returnDelayMinutes.toLong())
                wrapUpIdx = dateTimeList.lastIndex
            }

            windows.add(AutoOfflineSchedule(offlineTime, onlineTime))
            idx = wrapUpIdx + 1
        }

        return windows
    }

    /**
     * Backward-compatible single-schedule helper (returns the first window in the DIA).
     */
    fun calculateOfflineSchedule(segments: List<DiaDetailSegment>, workDate: LocalDate): AutoOfflineSchedule? {
        return calculateOfflineSchedules(segments, workDate).firstOrNull()
    }

    fun scheduleAutoOffline(context: Context, segments: List<DiaDetailSegment>, workDate: LocalDate) {
        val prefs = context.getSharedPreferences("crewmate_prefs", Context.MODE_PRIVATE)
        val enabled = isEnabled(context)
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return

        val cancelOfflineIntent = Intent(context, AutoOfflineReceiver::class.java).apply {
            action = AutoOfflineReceiver.ACTION_SET_OFFLINE
        }
        val cancelOnlineIntent = Intent(context, AutoOfflineReceiver::class.java).apply {
            action = AutoOfflineReceiver.ACTION_SET_ONLINE
        }
        val cancelPending1 = PendingIntent.getBroadcast(
            context,
            7701,
            cancelOfflineIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val cancelPending2 = PendingIntent.getBroadcast(
            context,
            7702,
            cancelOnlineIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.cancel(cancelPending1)
        alarmManager.cancel(cancelPending2)

        if (!enabled) {
            prefs.edit().putString(PREF_SCHEDULED_SUMMARY, "비활성화 상태 (스위치 OFF)").apply()
            return
        }

        val returnDelayMins = prefs.getInt(PREF_RETURN_DELAY_MINUTES, 10)
        val windows = calculateOfflineSchedules(segments, workDate, returnDelayMins)
        if (windows.isEmpty()) {
            prefs.edit().putString(PREF_SCHEDULED_SUMMARY, "예정된 1순위 운전 구간 없음 (${workDate})").apply()
            return
        }

        val isSkipped = prefs.getBoolean(PREF_RETURN_SKIPPED, false)
        val delayNote = if (returnDelayMins > 0) " (정리 +${returnDelayMins}분 복귀)" else ""
        val summaryStr = windows.mapIndexed { i, w ->
            val prefix = if (windows.size > 1) "[${i + 1}구간] " else ""
            "${prefix}OFF ${w.offlineTriggerTime.format(SUMMARY_TIME_FORMAT)} → ON ${w.onlineTriggerTime.format(SUMMARY_TIME_FORMAT)}$delayNote"
        }.joinToString("\n") + if (isSkipped) "\n(⚠️ 온라인 복귀 Skip 적용 중)" else ""
        prefs.edit().putString(PREF_SCHEDULED_SUMMARY, summaryStr).apply()

        val now = LocalDateTime.now()

        // Pick the currently active window or the next upcoming window
        val activeOrNext = windows.firstOrNull { it.onlineTriggerTime.isAfter(now) }
        if (activeOrNext == null) {
            Log.i(TAG, "All auto-offline windows for $workDate have already passed.")
            return
        }

        val windowKey = "${activeOrNext.offlineTriggerTime}_${activeOrNext.onlineTriggerTime}"

        // If we are currently inside the driving window [offlineTriggerTime .. onlineTriggerTime),
        // and we haven't auto-triggered for this window yet, switch to offline immediately!
        if (!now.isBefore(activeOrNext.offlineTriggerTime) && now.isBefore(activeOrNext.onlineTriggerTime)) {
            val lastTriggeredWindow = prefs.getString(PREF_LAST_TRIGGERED_WINDOW, "")
            if (lastTriggeredWindow != windowKey) {
                val nowFormatted = now.format(LOG_TIME_FORMAT)
                prefs.edit()
                    .putBoolean("is_offline_mode", true)
                    .putString(PREF_LAST_TRIGGERED_WINDOW, windowKey)
                    .putString(PREF_LAST_OFFLINE_TIME, "$nowFormatted (운전구간 진입 즉시전환)")
                    .apply()
                Log.i(TAG, "Immediately switched to OFFLINE mode inside active window: $windowKey")
            }
        } else if (activeOrNext.offlineTriggerTime.isAfter(now)) {
            // Schedule offline transition (RequestCode: 7701)
            val intent = Intent(context, AutoOfflineReceiver::class.java).apply {
                action = AutoOfflineReceiver.ACTION_SET_OFFLINE
                putExtra("window_key", windowKey)
            }
            val pending = PendingIntent.getBroadcast(
                context,
                7701,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val epochMilli = activeOrNext.offlineTriggerTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
            setExactAlarm(alarmManager, epochMilli, pending)
            Log.i(TAG, "Scheduled auto offline at ${activeOrNext.offlineTriggerTime}")
        }

        // Schedule online recovery (RequestCode: 7702)
        val returnSkipped = isReturnSkipped(context)
        if (!returnSkipped && activeOrNext.onlineTriggerTime.isAfter(now)) {
            val intent = Intent(context, AutoOfflineReceiver::class.java).apply {
                action = AutoOfflineReceiver.ACTION_SET_ONLINE
                putExtra("window_key", windowKey)
            }
            val pending = PendingIntent.getBroadcast(
                context,
                7702,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val epochMilli = activeOrNext.onlineTriggerTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
            setExactAlarm(alarmManager, epochMilli, pending)
            Log.i(TAG, "Scheduled auto online at ${activeOrNext.onlineTriggerTime}")
        }
    }

    fun isReturnSkipped(context: Context): Boolean {
        val prefs = context.getSharedPreferences("crewmate_prefs", Context.MODE_PRIVATE)
        return prefs.getBoolean(PREF_RETURN_SKIPPED, false)
    }

    fun skipOnlineReturn(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager
        val cancelOnlineIntent = Intent(context, AutoOfflineReceiver::class.java).apply {
            action = AutoOfflineReceiver.ACTION_SET_ONLINE
        }
        val pending = PendingIntent.getBroadcast(
            context,
            7702,
            cancelOnlineIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager?.cancel(pending)

        val prefs = context.getSharedPreferences("crewmate_prefs", Context.MODE_PRIVATE)
        val currentSummary = prefs.getString(PREF_SCHEDULED_SUMMARY, "") ?: ""
        val updatedSummary = if (!currentSummary.contains("온라인 복귀 Skip")) {
            "$currentSummary\n(⚠️ 온라인 복귀 Skip 적용 중)"
        } else currentSummary

        prefs.edit()
            .putBoolean(PREF_RETURN_SKIPPED, true)
            .putString(PREF_SCHEDULED_SUMMARY, updatedSummary)
            .apply()
        Log.i(TAG, "Online return was skipped by user.")
    }

    fun resetReturnSkipped(context: Context) {
        val prefs = context.getSharedPreferences("crewmate_prefs", Context.MODE_PRIVATE)
        prefs.edit().putBoolean(PREF_RETURN_SKIPPED, false).apply()
    }

    /**
     * Reads cached schedule and DIA detail from local Room DB (yesterday & today) to immediately
     * register or refresh auto-offline alarms.
     */
    suspend fun syncFromDatabase(context: Context) {
        try {
            val db = CrewMateDatabase.getInstance(context)
            val today = com.example.crewmate.util.AppDateProvider.today
            val parser = DiaDetailParser()

            // Check today and yesterday (for overnight shift still running)
            val candidateDates = listOf(today.minusDays(1), today)
            val now = LocalDateTime.now()

            var chosenDate: LocalDate? = null
            var chosenSegments: List<DiaDetailSegment> = emptyList()

            for (date in candidateDates) {
                val dateStr = date.format(DateTimeFormatter.ofPattern("yyyyMMdd", Locale.ROOT))
                val monthStr = date.format(DateTimeFormatter.ofPattern("yyyyMM", Locale.ROOT))
                val schedules = db.workScheduleDao().getSchedulesForMonth(monthStr)
                val daySchedules = schedules.filter {
                    it.date == dateStr && !it.diaNo.isNullOrBlank() && it.diaNo != "S" && it.diaNo != "연차" && it.diaNo != "~"
                }

                val segments = mutableListOf<DiaDetailSegment>()
                daySchedules.forEach { sched ->
                    val diaNo = sched.diaNo.orEmpty().trim()
                    val cached = db.diaDetailDao().getDiaDetail("$dateStr|$diaNo")
                    if (cached != null && cached.detailJson.isNotBlank()) {
                        runCatching {
                            segments.addAll(parser.parse(cached.detailJson).segments)
                        }
                    }
                }

                if (segments.isNotEmpty()) {
                    val windows = calculateOfflineSchedules(segments, date)
                    val hasActiveOrUpcoming = windows.any { it.onlineTriggerTime.isAfter(now) }
                    if (hasActiveOrUpcoming || date == today) {
                        chosenDate = date
                        chosenSegments = segments
                        if (hasActiveOrUpcoming && date == today.minusDays(1)) {
                            // Yesterday's overnight shift is still active right now
                            break
                        }
                    }
                }
            }

            if (chosenDate != null && chosenSegments.isNotEmpty()) {
                scheduleAutoOffline(context, chosenSegments, chosenDate)
            } else {
                val prefs = context.getSharedPreferences("crewmate_prefs", Context.MODE_PRIVATE)
                if (!isEnabled(context)) {
                    prefs.edit().putString(PREF_SCHEDULED_SUMMARY, "비활성화 상태 (스위치 OFF)").apply()
                } else {
                    prefs.edit().putString(PREF_SCHEDULED_SUMMARY, "오늘 저장된 다이아 상세 데이터 없음 (오늘의 근무 조회 시 자동 등록)").apply()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to sync auto-offline schedule from DB", e)
        }
    }

    private fun setExactAlarm(alarmManager: AlarmManager, epochMilli: Long, pendingIntent: PendingIntent) {
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
            Log.e(TAG, "Failed to set exact alarm", e)
        }
    }
}
