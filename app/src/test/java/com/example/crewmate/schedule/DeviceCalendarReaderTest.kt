package com.example.crewmate.schedule

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.ZoneId
import java.time.ZoneOffset

class DeviceCalendarReaderTest {

    private val seoulZone = ZoneId.of("Asia/Seoul")

    @Test
    fun expandDeviceEventDates_multiDayTimedEvent_returnsAllSpannedDays() {
        val month = YearMonth.of(2026, 11)
        val begin = LocalDateTime.of(2026, 11, 10, 14, 0).atZone(seoulZone).toInstant().toEpochMilli()
        val end = LocalDateTime.of(2026, 11, 12, 18, 0).atZone(seoulZone).toInstant().toEpochMilli()

        val dates = expandDeviceEventDates(begin, end, isAllDay = false, month = month, zoneId = seoulZone)

        assertEquals(
            listOf(
                LocalDate.of(2026, 11, 10),
                LocalDate.of(2026, 11, 11),
                LocalDate.of(2026, 11, 12)
            ),
            dates
        )
    }

    @Test
    fun expandDeviceEventDates_allDayMultiDayEvent_handlesUtcMidnightProperly() {
        val month = YearMonth.of(2026, 11)
        // Android CalendarContract ALL_DAY=1 stores UTC midnight boundaries (exclusive end)
        val begin = LocalDate.of(2026, 11, 10).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        val end = LocalDate.of(2026, 11, 13).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

        val dates = expandDeviceEventDates(begin, end, isAllDay = true, month = month, zoneId = seoulZone)

        assertEquals(
            listOf(
                LocalDate.of(2026, 11, 10),
                LocalDate.of(2026, 11, 11),
                LocalDate.of(2026, 11, 12)
            ),
            dates
        )
    }

    @Test
    fun expandDeviceEventDates_exactMidnightEndTimedEvent_doesNotBleedIntoNextDay() {
        val month = YearMonth.of(2026, 11)
        val begin = LocalDateTime.of(2026, 11, 10, 9, 0).atZone(seoulZone).toInstant().toEpochMilli()
        val end = LocalDateTime.of(2026, 11, 11, 0, 0).atZone(seoulZone).toInstant().toEpochMilli()

        val dates = expandDeviceEventDates(begin, end, isAllDay = false, month = month, zoneId = seoulZone)

        assertEquals(listOf(LocalDate.of(2026, 11, 10)), dates)
    }

    @Test
    fun expandDeviceEventDates_crossMonthEvent_clipsToRequestedMonth() {
        val month = YearMonth.of(2026, 11)
        val begin = LocalDate.of(2026, 10, 30).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        val end = LocalDate.of(2026, 11, 3).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

        val dates = expandDeviceEventDates(begin, end, isAllDay = true, month = month, zoneId = seoulZone)

        assertEquals(
            listOf(
                LocalDate.of(2026, 11, 1),
                LocalDate.of(2026, 11, 2)
            ),
            dates
        )
    }

    @Test
    fun holidayWorkManager_nightDay2Continuation_omitsRedundantIntegrationText() {
        val s1 = WorkSchedule(
            workDate = "20261107",
            diaNo = "16310",
            alcSpecDvCd = "11",
            hldyYn = "Y",
            isHoliday = true,
            startTime = "1651",
            endTime = "2310"
        )
        val s2 = WorkSchedule(
            workDate = "20261108",
            diaNo = "~16310",
            alcSpecDvCd = "12",
            hldyYn = "Y",
            isHoliday = true,
            isContinuation = true,
            startTime = "0533",
            endTime = "0727"
        )

        val report = HolidayWorkManager.calculateHolidayWork(2026, listOf(s1, s2))
        assertEquals(1, report.nightCount)
        assertEquals(2, report.items.size)
        val day2 = report.items[1]
        assertTrue(day2.isContinuation)
        assertFalse(day2.timeDesc.contains("전일 야간 1일차와 1개 단위 통합"))
        assertTrue(day2.timeDesc.startsWith("야간 2일차"))
    }
}
