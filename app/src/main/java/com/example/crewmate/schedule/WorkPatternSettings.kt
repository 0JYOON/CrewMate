package com.example.crewmate.schedule

import android.content.Context
import android.content.SharedPreferences
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

data class WorkPatternData(
    val enabled: Boolean,
    val anchorDate: LocalDate,
    val patternList: List<String>
)

object WorkPatternSettings {
    private const val PREFS_NAME = "crewmate_prefs"
    private const val KEY_PATTERN_ENABLED = "work_pattern_enabled"
    private const val KEY_ANCHOR_DATE = "work_pattern_anchor_date"
    private const val KEY_PATTERN_SEQUENCE = "work_pattern_sequence"

    val AVAILABLE_BADGES = listOf("주간", "야간", "~", "S")

    fun load(context: Context): WorkPatternData {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val enabled = prefs.getBoolean(KEY_PATTERN_ENABLED, false)
        val anchorStr = prefs.getString(KEY_ANCHOR_DATE, null)
        val anchorDate = if (!anchorStr.isNullOrBlank()) {
            runCatching { LocalDate.parse(anchorStr, DateTimeFormatter.ISO_LOCAL_DATE) }.getOrDefault(LocalDate.now())
        } else {
            LocalDate.now()
        }

        val seqStr = prefs.getString(KEY_PATTERN_SEQUENCE, "") ?: ""
        val list = if (seqStr.isNotBlank()) seqStr.split(",").map { it.trim() }.filter { it.isNotEmpty() } else emptyList()

        return WorkPatternData(enabled, anchorDate, list)
    }

    fun save(context: Context, data: WorkPatternData) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().apply {
            putBoolean(KEY_PATTERN_ENABLED, data.enabled)
            putString(KEY_ANCHOR_DATE, data.anchorDate.format(DateTimeFormatter.ISO_LOCAL_DATE))
            putString(KEY_PATTERN_SEQUENCE, data.patternList.joinToString(","))
        }.apply()
    }

    fun getPatternForDate(data: WorkPatternData, targetDate: LocalDate): String? {
        if (!data.enabled || data.patternList.isEmpty()) return null
        val diff = ChronoUnit.DAYS.between(data.anchorDate, targetDate)
        val size = data.patternList.size
        val index = (((diff % size) + size) % size).toInt()
        return data.patternList[index]
    }
}
