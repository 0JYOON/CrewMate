package com.example.crewmate.labs.handovercrew

import com.example.crewmate.data.remote.xcrew.CrewAssignment
import com.example.crewmate.data.remote.xcrew.DiaDetailSegment
import com.example.crewmate.schedule.TrainCrewSearchResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class HandoverCrewResolverTest {

    private fun seg(
        trnNo: String,
        dptStn: String,
        dptTm: String,
        arvStn: String,
        arvTm: String,
        dvNm: String = "승무"
    ) = DiaDetailSegment(
        num = null,
        pdiaEfcStDt = null,
        pdiaEfcClsDt = null,
        trnNo = trnNo,
        dptStnNm = dptStn,
        arvStnNm = arvStn,
        dptTm = dptTm,
        arvTm = arvTm,
        pjtHrDvNm = dvNm,
        pjtTnum = null,
        pjtDst = null
    )

    private fun crew(pdiaNo: String, vararg names: String) = listOf(
        CrewAssignment(
            pdiaNo = pdiaNo,
            repTrn1No = null,
            repTrn2No = null,
            gwkTm = null,
            loiwTm = null,
            crewMembers = names.toList()
        )
    )

    @Test
    fun `resolveHandover identifies previous and next crew in 3-leg relay`() {
        val prevSeg = seg("101", "서울", "0800", "대전", "0900")
        val mySeg = seg("101", "대전", "0905", "동대구", "1000")
        val nextSeg = seg("101", "동대구", "1005", "부산", "1100")

        val results = listOf(
            TrainCrewSearchResult("20261115", "301", nextSeg, crew("301", "이교대")),
            TrainCrewSearchResult("20261115", "101", prevSeg, crew("101", "김직전")),
            TrainCrewSearchResult("20261115", "201", mySeg, crew("201", "나본인"))
        )

        val report = HandoverCrewResolver.resolveHandover(
            trainNumber = "101",
            targetDate = "20261115",
            currentDiaNo = "201",
            currentSegment = mySeg,
            results = results,
            fromOfflineCache = false
        )

        assertEquals("101", report.previousCrew?.pdiaNo)
        assertEquals(listOf("김직전"), report.previousCrew?.crewMembers)
        assertEquals("301", report.nextCrew?.pdiaNo)
        assertEquals(listOf("이교대"), report.nextCrew?.crewMembers)
        assertEquals(3, report.allRelays.size)
    }

    @Test
    fun `resolveHandover handles overnight midnight crossing`() {
        val prevSeg = seg("999", "수서", "2320", "천안아산", "2355")
        val mySeg = seg("999", "천안아산", "0002", "대전", "0035")

        val results = listOf(
            TrainCrewSearchResult("20261116", "502", mySeg, crew("502", "나본인")),
            TrainCrewSearchResult("20261115", "501", prevSeg, crew("501", "박야간"))
        )

        val report = HandoverCrewResolver.resolveHandover(
            trainNumber = "999",
            targetDate = "20261116",
            currentDiaNo = "~502",
            currentSegment = mySeg,
            results = results,
            fromOfflineCache = true
        )

        assertEquals("501", report.previousCrew?.pdiaNo)
        assertEquals(listOf("박야간"), report.previousCrew?.crewMembers)
        assertNull(report.nextCrew)
    }

    @Test
    fun `resolveSegmentDate advances date after midnight`() {
        val s1 = seg("101", "수서", "2230", "대전", "2340")
        val s2 = seg("103", "대전", "0020", "부산", "0200")
        val resolved = HandoverCrewResolver.resolveSegmentDate(
            baseDate = LocalDate.of(2026, 11, 15),
            segments = listOf(s1, s2),
            targetSegment = s2
        )
        assertEquals(LocalDate.of(2026, 11, 16), resolved)
    }
}
