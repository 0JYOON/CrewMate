package com.example.crewmate.labs.colleaguecompare

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.YearMonth

class ColleagueScheduleImportParserTest {

    @Test
    fun parseBatchScheduleText_parsesMultiplePeopleAndCommuteTimes() {
        val raw = """
            [이름: 윤영준 | 월: 2026-11]
            01: 연차
            02: 16035 | 09:53 | 21:05
            04: ~(16315) | 18:51 | 10:07
            11: S

            [이름: 김철수 | 월: 2026-11]
            01: 16010 | 05:45 | 16:23
            02: S
        """.trimIndent()

        val result = ColleagueScheduleImportParser.parseBatchScheduleText(raw, YearMonth.of(2026, 11))
        assertEquals(2, result.size)

        val p1 = result[0]
        assertEquals("윤영준", p1.name)
        assertEquals("202611", p1.ymKey)
        assertEquals("연차", p1.cells["2026-11-01"]?.diaNo)
        assertEquals("16035", p1.cells["2026-11-02"]?.diaNo)
        assertEquals("09:53", p1.cells["2026-11-02"]?.startTime)
        assertEquals("21:05", p1.cells["2026-11-02"]?.endTime)
        assertEquals("~(16315)", p1.cells["2026-11-04"]?.diaNo)
        assertEquals("S", p1.cells["2026-11-11"]?.diaNo)

        val p2 = result[1]
        assertEquals("김철수", p2.name)
        assertEquals("16010", p2.cells["2026-11-01"]?.diaNo)
        assertEquals("S", p2.cells["2026-11-02"]?.diaNo)
    }

    @Test
    fun parseBatchScheduleText_handlesUnknownMonthAndMultipleExcelRowsWithoutTimes() {
        val raw = """
            [이름: 테스트1 | 월: 미상]
            01: 16037
            02: 16507
            03: ~
            04: S

            **[이름: 테스트2 | 월: 없음]**
            01: ~
            02: S

            [이름: 테스트11]
            01: S
            02: 16211
        """.trimIndent()

        val result = ColleagueScheduleImportParser.parseBatchScheduleText(raw, YearMonth.of(2026, 11))
        assertEquals(3, result.size)

        assertEquals("테스트1", result[0].name)
        assertEquals("202611", result[0].ymKey)
        assertEquals("16037", result[0].cells["2026-11-01"]?.diaNo)
        assertEquals("", result[0].cells["2026-11-01"]?.startTime)

        assertEquals("테스트2", result[1].name)
        assertEquals("202611", result[1].ymKey)
        assertEquals("~", result[1].cells["2026-11-01"]?.diaNo)

        assertEquals("테스트11", result[2].name)
        assertEquals("202611", result[2].ymKey)
        assertEquals("16211", result[2].cells["2026-11-02"]?.diaNo)
    }
}
