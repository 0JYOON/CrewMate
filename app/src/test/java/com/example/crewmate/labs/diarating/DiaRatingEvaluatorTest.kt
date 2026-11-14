package com.example.crewmate.labs.diarating

import com.example.crewmate.data.remote.xcrew.DiaDetailResponse
import com.example.crewmate.data.remote.xcrew.DiaDetailSegment
import com.example.crewmate.data.remote.xcrew.DiaDetailSummary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class DiaRatingEvaluatorTest {

    private fun detailWith(
        driveMinutes: Int,
        totalMinutes: Int,
        sleepStation: String? = null
    ): DiaDetailResponse {
        val segments = buildList {
            if (sleepStation != null) {
                add(
                    DiaDetailSegment(
                        num = 1,
                        pdiaEfcStDt = null,
                        pdiaEfcClsDt = null,
                        trnNo = null,
                        dptStnNm = sleepStation,
                        arvStnNm = sleepStation,
                        dptTm = "2300",
                        arvTm = "0500",
                        pjtHrDvNm = "휴게",
                        pjtTnum = 360,
                        pjtDst = null
                    )
                )
            }
        }
        return DiaDetailResponse(
            effectiveTerm = null,
            runDtDv = null,
            crewMeta = null,
            segments = segments,
            summaries = listOf(
                DiaDetailSummary(
                    repPjtHrDvCd = null,
                    pjtHrDvNm = "운전",
                    pjtTnum = driveMinutes,
                    pjtDst = null,
                    totWrkTnum = totalMinutes,
                    totPjtDst = null
                )
            )
        )
    }

    @Test
    fun `evaluates light, normal, heavy grades from drive and total minutes`() {
        val rules = DiaRatingRules(
            lightMaxDriveMinutes = 240,
            heavyMinDriveMinutes = 360,
            heavyMinTotalMinutes = 600
        )

        val light = DiaRatingEvaluator.evaluate("101", detailWith(200, 420), null, rules)
        assertEquals(DiaRatingGrade.LIGHT, light?.grade)

        val normal = DiaRatingEvaluator.evaluate("102", detailWith(300, 500), null, rules)
        assertEquals(DiaRatingGrade.NORMAL, normal?.grade)

        val heavyByDrive = DiaRatingEvaluator.evaluate("103", detailWith(380, 550), null, rules)
        assertEquals(DiaRatingGrade.HEAVY, heavyByDrive?.grade)

        val heavyByTotal = DiaRatingEvaluator.evaluate("104", detailWith(300, 620), null, rules)
        assertEquals(DiaRatingGrade.HEAVY, heavyByTotal?.grade)
    }

    @Test
    fun `preferred and heavy sleep stations override auto time grade`() {
        val rules = DiaRatingRules(
            preferredSleepStations = setOf("대전"),
            heavySleepStations = setOf("익산")
        )

        val preferred = DiaRatingEvaluator.evaluate("~201", detailWith(300, 500, "대전"), null, rules)
        assertNotNull(preferred)
        assertEquals(DiaRatingGrade.CUSTOM, preferred?.grade)
        assertEquals("대전", preferred?.sleepStation)

        val heavy = DiaRatingEvaluator.evaluate("~202", detailWith(180, 400, "익산"), null, rules)
        assertEquals(DiaRatingGrade.HEAVY, heavy?.grade)
    }

    @Test
    fun `manual override takes precedence and non-duty S returns null`() {
        val rules = DiaRatingRules(customOverrides = mapOf("16035" to DiaRatingGrade.CUSTOM))
        val overridden = DiaRatingEvaluator.evaluate("16035", detailWith(400, 700), null, rules)
        assertEquals(DiaRatingGrade.CUSTOM, overridden?.grade)
        assertEquals(true, overridden?.isManualOverride)

        val avoidRules = DiaRatingRules(customOverrides = mapOf("16315" to DiaRatingGrade.AVOID))
        val avoided = DiaRatingEvaluator.evaluate("16315", detailWith(200, 400), null, avoidRules)
        assertEquals(DiaRatingGrade.AVOID, avoided?.grade)
        assertEquals("⚠️", avoided?.grade?.shortBadge)

        assertNull(DiaRatingEvaluator.evaluate("S", null, null, rules))
        assertNull(DiaRatingEvaluator.evaluate("연차", null, null, rules))
    }
}
