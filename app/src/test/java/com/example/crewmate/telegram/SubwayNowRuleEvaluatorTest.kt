package com.example.crewmate.telegram

import com.example.crewmate.data.remote.xcrew.DiaDetailSegment
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime

class SubwayNowRuleEvaluatorTest {

    private fun createSegment(
        pjtHrDvNm: String,
        trnNo: String?,
        dptStnNm: String?,
        arrStnNm: String?,
        dptTm: String?,
        arvTm: String?
    ): DiaDetailSegment {
        return DiaDetailSegment(
            num = 1,
            pdiaEfcStDt = "20261028",
            pdiaEfcClsDt = "20261028",
            trnNo = trnNo,
            dptStnNm = dptStnNm,
            arvStnNm = arrStnNm,
            dptTm = dptTm,
            arvTm = arvTm,
            pjtHrDvNm = pjtHrDvNm,
            pjtTnum = 1,
            pjtDst = "10.5"
        )
    }

    @Test
    fun `test Rule 1 - normal Rank 1 driving duty is extracted`() {
        val segs = listOf(
            createSegment("출근", null, "구로", "구로", "08:00", "08:10"),
            createSegment("운전", "K507", "수원", "천안", "14:30", "15:15"),
            createSegment("정리", null, "천안", "천안", "15:15", "15:30")
        )

        val targets = SubwayNowRuleEvaluator.evaluateTargets(
            segments = segs,
            workDate = LocalDate.of(2026, 10, 28),
            sendLeadMinutes = 30,
            trackLeadMinutes = 15,
            trackInterval = 3
        )

        assertEquals(1, targets.size)
        val t = targets[0]
        assertEquals("K507", t.trainNo)
        assertEquals("수원", t.targetStation)
        assertEquals(LocalTime.of(14, 30), t.baseDepartureTime)
        assertEquals("14:15", t.trackStartTimeStr)
        assertEquals("K507 수원 3 14:15", t.rawCommand)
        assertFalse(t.hasPrecedingGamsi)
    }

    @Test
    fun `test Rule 3 - K prefix normalization for digits and other prefixes`() {
        assertEquals("K1601", SubwayNowRuleEvaluator.normalizeTrainNo("1601"))
        assertEquals("K507", SubwayNowRuleEvaluator.normalizeTrainNo("S507"))
        assertEquals("K507", SubwayNowRuleEvaluator.normalizeTrainNo("K507"))
        assertEquals("K507", SubwayNowRuleEvaluator.normalizeTrainNo("k507"))
    }

    @Test
    fun `test Rule 2 - Sandwiched duty (Rank 1 - 감시 - Rank 1) skips the second Rank 1`() {
        val segs = listOf(
            createSegment("운전", "1601", "구로", "용산", "09:00", "09:30"),
            createSegment("감시", null, "용산", "용산", "09:30", "09:50"),
            createSegment("운전", "1603", "용산", "동두천", "09:50", "11:00") // Sandwiched continuous work!
        )

        val targets = SubwayNowRuleEvaluator.evaluateTargets(
            segments = segs,
            workDate = LocalDate.of(2026, 10, 28),
            sendLeadMinutes = 30,
            trackLeadMinutes = 15,
            trackInterval = 3
        )

        // 두 번째 운전(1603)은 연속 업무이므로 건너뛰어야 함
        assertEquals(1, targets.size)
        assertEquals("K1601", targets[0].trainNo)
        assertEquals("구로", targets[0].targetStation)
    }

    @Test
    fun `test Rule 4 - Preceding 감시 departure time is inherited as base departure time`() {
        val segs = listOf(
            createSegment("준비", null, "구로", "구로", "13:30", "13:50"),
            createSegment("감시", null, "구로", "구로", "13:50", "14:10"),
            createSegment("승무", "S4594", "구로", "인천", "14:10", "15:00")
        )

        val targets = SubwayNowRuleEvaluator.evaluateTargets(
            segments = segs,
            workDate = LocalDate.of(2026, 10, 28),
            sendLeadMinutes = 30,
            trackLeadMinutes = 15,
            trackInterval = 3
        )

        assertEquals(1, targets.size)
        val t = targets[0]
        assertEquals("K4594", t.trainNo)
        assertEquals("구로", t.targetStation)
        assertTrue(t.hasPrecedingGamsi)
        // 승무의 원래 dptTm은 14:10이지만, 감시의 dptTm인 13:50이 기준 시각이 되어야 함
        assertEquals(LocalTime.of(13, 50), t.baseDepartureTime)
        // 13:50 - 15분 = 13:35 추적 시작
        assertEquals("13:35", t.trackStartTimeStr)
        assertEquals("K4594 구로 3 13:35", t.rawCommand)
    }
}
