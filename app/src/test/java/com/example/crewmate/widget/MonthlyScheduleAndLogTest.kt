package com.example.crewmate.widget

import com.example.crewmate.data.remote.xcrew.XcrewNetworkLogEntry
import com.example.crewmate.data.remote.xcrew.XcrewNetworkLogStore
import com.example.crewmate.schedule.WorkSchedule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class MonthlyScheduleAndLogTest {

    @Test
    fun filterDisplaySchedules_preservesRestSWhenNoActiveDiaExists() {
        val restOnly = listOf(
            WorkSchedule(
                workDate = "20261114",
                diaNo = "S",
                startTime = null,
                endTime = null,
                trainNo = null,
                trainNo2 = null,
                alcSpecDvCd = "02",
                nonActiveType = "S",
                isHoliday = false,
                hldyYn = "N"
            )
        )
        val result = MonthlyScheduleRenderer.filterDisplaySchedules(restOnly)
        assertEquals(1, result.size)
        assertEquals("S", result.first().diaNo)
    }

    @Test
    fun filterDisplaySchedules_removesSWhenActualDutyCoexists() {
        val mixed = listOf(
            WorkSchedule(
                workDate = "20261114",
                diaNo = "S",
                startTime = null,
                endTime = null,
                trainNo = null,
                trainNo2 = null,
                alcSpecDvCd = "02",
                nonActiveType = "S",
                isHoliday = false,
                hldyYn = "N"
            ),
            WorkSchedule(
                workDate = "20261114",
                diaNo = "51",
                startTime = "0830",
                endTime = "1720",
                trainNo = null,
                trainNo2 = null,
                alcSpecDvCd = "11",
                nonActiveType = null,
                isHoliday = false,
                hldyYn = "N"
            )
        )
        val result = MonthlyScheduleRenderer.filterDisplaySchedules(mixed)
        assertEquals(1, result.size)
        assertEquals("51", result.first().diaNo)
    }

    @Test
    fun pruneEntries_keepsYesterdayTodayTomorrowAndDropsOlderOrFutureBeyondOneDay() {
        val zone = ZoneId.of("Asia/Seoul")
        val refDate = LocalDate.of(2026, 11, 14)

        fun ts(d: LocalDate, hour: Int): Long =
            d.atTime(hour, 0).atZone(zone).toInstant().toEpochMilli()

        val entries = listOf(
            XcrewNetworkLogEntry(ts(refDate.minusDays(2), 12), "오래된로그", "/old", true, "HTTP 200"),
            XcrewNetworkLogEntry(ts(refDate.minusDays(1), 9), "어제로그", "/yesterday", true, "HTTP 200"),
            XcrewNetworkLogEntry(ts(refDate, 14), "오늘로그", "/today", true, "HTTP 200"),
            XcrewNetworkLogEntry(ts(refDate.plusDays(1), 8), "내일로그", "/tomorrow", true, "HTTP 200"),
            XcrewNetworkLogEntry(ts(refDate.plusDays(2), 8), "미래로그", "/future", true, "HTTP 200")
        )

        val pruned = XcrewNetworkLogStore.pruneEntries(entries, referenceDate = refDate, zoneId = zone)
        assertEquals(3, pruned.size)
        assertEquals(listOf("내일로그", "오늘로그", "어제로그"), pruned.map { it.actionName })
        assertTrue(pruned.none { it.actionName == "오래된로그" || it.actionName == "미래로그" })
    }
}
