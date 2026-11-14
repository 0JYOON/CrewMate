package com.example.crewmate.labs.routediagram

import com.example.crewmate.data.remote.xcrew.DiaDetailResponse
import com.example.crewmate.data.remote.xcrew.DiaDetailSegment
import com.example.crewmate.data.remote.xcrew.DiaDetailSummary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RouteDiagramBuilderTest {

    private fun seg(
        pjtHrDvNm: String,
        trnNo: String? = null,
        dptStnNm: String,
        arvStnNm: String,
        dptTm: String,
        arvTm: String,
        pjtTnum: Int
    ) = DiaDetailSegment(
        num = null,
        pdiaEfcStDt = null,
        pdiaEfcClsDt = null,
        trnNo = trnNo,
        dptStnNm = dptStnNm,
        arvStnNm = arvStnNm,
        dptTm = dptTm,
        arvTm = arvTm,
        pjtHrDvNm = pjtHrDvNm,
        pjtTnum = pjtTnum,
        pjtDst = null
    )

    @Test
    fun `consecutive non-watch dwells merge into single step while blue core stays separate`() {
        val segments = listOf(
            seg("출고준비", null, "안산", "안산", "0943", "1023", 40),
            seg("운전", "K4550", "안산", "4서울", "1023", "1209", 106),
            seg("감시", null, "4서울", "4서울", "1209", "1220", 11),
            seg("운전", "K4575", "4서울", "안산", "1220", "1257", 37),
            seg("정리", null, "안산", "안산", "1257", "1307", 10),
            seg("휴게", null, "안산", "안산", "1307", "1407", 60),
            seg("승계", null, "안산", "안산", "1407", "1532", 85),
            seg("준비", null, "안산", "안산", "1532", "1547", 15),
            seg("운전", "K4589", "안산", "오이도", "1547", "1605", 18)
        )
        val detail = DiaDetailResponse(
            effectiveTerm = null,
            runDtDv = null,
            segments = segments,
            summaries = listOf(
                DiaDetailSummary(
                    repPjtHrDvCd = null,
                    pjtHrDvNm = "계",
                    pjtTnum = 382,
                    pjtDst = 158.7,
                    totWrkTnum = 382,
                    totPjtDst = 158.7
                )
            ),
            crewMeta = null
        )

        val model = RouteDiagramBuilder.build(detail)
        val dwells = model.steps.filterIsInstance<RouteStep.Dwell>()

        assertEquals(3, dwells.size)
        assertEquals("(출고40)", dwells[0].label)
        assertTrue(dwells[0].isBlueCore)

        assertEquals("(감11)", dwells[1].label)
        assertTrue(dwells[1].isBlueCore)

        assertEquals("(정10 휴01:00 승01:25 준15)", dwells[2].label)
        assertFalse(dwells[2].isBlueCore)
        assertEquals(170, dwells[2].durationMinutes)
    }

    @Test
    fun `master cross-check compares version and updatedAt and merges custom depots`() {
        val v1 = RailwayMasterData(
            version = 1,
            updatedAt = "2026-11-14",
            lines = listOf(RailwayLineDef("line4", "4호선", listOf("불암산", "서울", "안산", "오이도")))
        )
        val v2 = v1.copy(version = 2, updatedAt = "2026-11-15")
        assertTrue(RailwayMasterEngine.isNewerThan(v2, v1))
        assertFalse(RailwayMasterEngine.isNewerThan(v1, v2))

        val custom = RailwayMasterData(
            depots = mapOf("시흥기지" to DepotPlacement(parent = "오이도", position = "after"))
        )
        val merged = RailwayMasterEngine.merge(v2, custom)
        val sorted = RailwayMasterEngine.sortStationsByLineMaster(
            listOf("시흥기지", "안산", "불암산", "오이도"),
            merged
        )
        assertEquals(listOf("불암산", "안산", "오이도", "시흥기지"), sorted)
    }
}
