package com.example.crewmate.schedule

import android.content.ContentUris
import android.content.Context
import android.provider.CalendarContract
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.ZoneOffset

data class DeviceEvent(val title: String, val date: LocalDate, val color: Int)

/**
 * 캘린더 이벤트의 시작/종료 시각과 종일 여부를 기반으로 해당 월에 속하는 모든 일자 리스트를 계산합니다.
 * 다일(Multi-day) 일정 및 종일(All-day, UTC 자정 기준) 일정이 첫날뿐만 아니라 모든 날짜에 표시되도록 합니다.
 */
fun expandDeviceEventDates(
    beginMillis: Long,
    endMillis: Long,
    isAllDay: Boolean,
    month: YearMonth,
    zoneId: ZoneId = ZoneId.systemDefault()
): List<LocalDate> {
    val monthStart = month.atDay(1)
    val monthEnd = month.atEndOfMonth()

    val (rawStart, rawEnd) = if (isAllDay) {
        val start = Instant.ofEpochMilli(beginMillis).atZone(ZoneOffset.UTC).toLocalDate()
        val endExclusive = Instant.ofEpochMilli(endMillis).atZone(ZoneOffset.UTC).toLocalDate()
        val end = if (endExclusive.isAfter(start)) endExclusive.minusDays(1) else start
        start to end
    } else {
        val start = Instant.ofEpochMilli(beginMillis).atZone(zoneId).toLocalDate()
        // 종료 시각이 정확히 00:00:00인 경우 이전 날짜까지만 점유한 것으로 간주 (1ms 차감)
        val adjustedEndMillis = if (endMillis > beginMillis) endMillis - 1L else beginMillis
        val end = Instant.ofEpochMilli(adjustedEndMillis).atZone(zoneId).toLocalDate()
        val validEnd = if (end.isBefore(start)) start else end
        start to validEnd
    }

    val clampedStart = if (rawStart.isBefore(monthStart)) monthStart else rawStart
    val clampedEnd = if (rawEnd.isAfter(monthEnd)) monthEnd else rawEnd

    if (clampedStart.isAfter(clampedEnd)) return emptyList()

    val result = mutableListOf<LocalDate>()
    var cur = clampedStart
    while (!cur.isAfter(clampedEnd)) {
        result.add(cur)
        cur = cur.plusDays(1)
    }
    return result
}

fun getDeviceCalendarEvents(context: Context, yearMonth: YearMonth): Map<LocalDate, List<DeviceEvent>> {
    val events = mutableMapOf<LocalDate, MutableList<DeviceEvent>>()

    val startMillis = yearMonth.atDay(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
    val endMillis = yearMonth.atEndOfMonth().plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

    val builder = CalendarContract.Instances.CONTENT_URI.buildUpon()
    ContentUris.appendId(builder, startMillis)
    ContentUris.appendId(builder, endMillis)

    val projection = arrayOf(
        CalendarContract.Instances.TITLE,
        CalendarContract.Instances.BEGIN,
        CalendarContract.Instances.END,
        CalendarContract.Instances.ALL_DAY,
        CalendarContract.Instances.DISPLAY_COLOR,
        CalendarContract.Instances.VISIBLE
    )

    // 스마트폰 캘린더 앱에서 "표시(VISIBLE=1)"로 체크된 캘린더 일정만 필터링
    val selection = "${CalendarContract.Instances.VISIBLE} = ?"
    val selectionArgs = arrayOf("1")

    try {
        context.contentResolver.query(
            builder.build(),
            projection,
            selection,
            selectionArgs,
            "${CalendarContract.Instances.BEGIN} ASC"
        )?.use { cursor ->
            val titleIdx = cursor.getColumnIndexOrThrow(CalendarContract.Instances.TITLE)
            val beginIdx = cursor.getColumnIndexOrThrow(CalendarContract.Instances.BEGIN)
            val endIdx = cursor.getColumnIndexOrThrow(CalendarContract.Instances.END)
            val allDayIdx = cursor.getColumnIndexOrThrow(CalendarContract.Instances.ALL_DAY)
            val colorIdx = cursor.getColumnIndexOrThrow(CalendarContract.Instances.DISPLAY_COLOR)

            while (cursor.moveToNext()) {
                val title = cursor.getString(titleIdx) ?: "일정"
                val begin = cursor.getLong(beginIdx)
                val end = cursor.getLong(endIdx)
                val isAllDay = cursor.getInt(allDayIdx) == 1
                var color = cursor.getInt(colorIdx)
                if (color == 0) color = 0xFF059669.toInt()

                val dates = expandDeviceEventDates(begin, end, isAllDay, yearMonth)
                for (date in dates) {
                    events.getOrPut(date) { mutableListOf() }.add(DeviceEvent(title, date, color))
                }
            }
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }

    return events
}
