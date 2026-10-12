package com.example.crewmate.schedule

import android.content.ContentUris
import android.content.Context
import android.provider.CalendarContract
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

data class DeviceEvent(val title: String, val date: LocalDate, val color: Int)

fun getDeviceCalendarEvents(context: Context, yearMonth: YearMonth): Map<LocalDate, List<DeviceEvent>> {
    val events = mutableMapOf<LocalDate, MutableList<DeviceEvent>>()

    val startMillis = yearMonth.atDay(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
    val endMillis = yearMonth.atEndOfMonth().plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

    val builder = CalendarContract.Instances.CONTENT_URI.buildUpon()
    ContentUris.appendId(builder, startMillis)
    ContentUris.appendId(builder, endMillis)

    // 읽어올 컬럼에 색상(DISPLAY_COLOR)과 표시여부(VISIBLE) 추가
    val projection = arrayOf(
        CalendarContract.Instances.TITLE,
        CalendarContract.Instances.BEGIN,
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
            val colorIdx = cursor.getColumnIndexOrThrow(CalendarContract.Instances.DISPLAY_COLOR)

            while (cursor.moveToNext()) {
                val title = cursor.getString(titleIdx) ?: "일정"
                val begin = cursor.getLong(beginIdx)
                // 기기에서 설정된 캘린더 색상 가져오기 (만약 0일 경우 대비 예비 색상)
                var color = cursor.getInt(colorIdx)
                if (color == 0) color = 0xFF059669.toInt()

                val date = Instant.ofEpochMilli(begin).atZone(ZoneId.systemDefault()).toLocalDate()
                events.getOrPut(date) { mutableListOf() }.add(DeviceEvent(title, date, color))
            }
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }

    return events
}