package com.example.crewmate.labs.paystatement

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PayStatementParserTest {

    @Test
    fun parseXcrewSampleJson_extractsExpectedFieldsAndFormatsCorrectly() {
        val json = """
            {
              "data": [
                {
                  "num": 1,
                  "pjtDt": "20261101",
                  "offdDdFlg": "N",
                  "alcSpecDvCd": "21",
                  "pdiaNo": "연차",
                  "pdiaDvCd": null,
                  "pjtChgPrsFlg": "N",
                  "totPjtTnum": 0,
                  "totPjtDst": 0.0,
                  "hldyTnum": 0,
                  "extnTnum": 0,
                  "nihtTnum": 0
                },
                {
                  "num": 2,
                  "pjtDt": "20261102",
                  "offdDdFlg": "N",
                  "alcSpecDvCd": "11",
                  "pdiaNo": "16035",
                  "pdiaDvCd": "3",
                  "pjtChgPrsFlg": "N",
                  "totPjtTnum": 622,
                  "totPjtDst": 138.5,
                  "hldyTnum": 0,
                  "extnTnum": 10,
                  "nihtTnum": 0
                },
                {
                  "num": 3,
                  "pjtDt": "20261103",
                  "offdDdFlg": "N",
                  "alcSpecDvCd": "11",
                  "pdiaNo": "16315",
                  "pdiaDvCd": "3",
                  "pjtChgPrsFlg": "N",
                  "totPjtTnum": 674,
                  "totPjtDst": 116.1,
                  "hldyTnum": 0,
                  "extnTnum": 14,
                  "nihtTnum": 266
                }
              ]
            }
        """.trimIndent()

        val rows = PayStatementParser.parse(json)
        assertEquals(3, rows.size)
        assertEquals("10:22", PayStatementFormatter.formatMinutes(rows[1].totPjtTnum))
        assertEquals("00:10", PayStatementFormatter.formatMinutes(rows[1].extnTnum))
        assertEquals("11:14", PayStatementFormatter.formatMinutes(rows[2].totPjtTnum))
        assertEquals("04:26", PayStatementFormatter.formatMinutes(rows[2].nihtTnum))

        val report = PayStatementParser.buildReport(rows)
        assertEquals(1296, report.totals.totalMinutes)
        assertEquals("21:36", PayStatementFormatter.formatMinutes(report.totals.totalMinutes, blankIfZero = false))
        assertEquals(254.6, report.totals.totalDistance, 0.01)
        assertEquals(24, report.totals.overtimeMinutes)
        assertEquals(266, report.totals.nightMinutes)
    }

    @Test
    fun buildReport_evaluatesTravelPayAndAllowanceWithHolidayAndStandbyRules() {
        val rows = mutableListOf<PayStatementRow>()
        // 16 normal operating days
        for (day in 1..16) {
            rows.add(
                PayStatementRow(
                    num = day,
                    pjtDt = String.format("202611%02d", day),
                    pdiaNo = "160${day}",
                    offdDdFlg = "N",
                    alcSpecDvCd = "11",
                    pdiaDvCd = "3",
                    totPjtTnum = 540,
                    totPjtDst = 100.0,
                    hldyTnum = 0,
                    extnTnum = 0,
                    nihtTnum = 0
                )
            )
        }
        // 3 holiday operating days -> included in travel pay (16+3=19), excluded from allowance (16)
        for (day in 17..19) {
            rows.add(
                PayStatementRow(
                    num = day,
                    pjtDt = String.format("202611%02d", day),
                    pdiaNo = "163${day}",
                    offdDdFlg = "Y",
                    alcSpecDvCd = "11",
                    pdiaDvCd = "3",
                    totPjtTnum = 540,
                    totPjtDst = 95.0,
                    hldyTnum = 480,
                    extnTnum = 0,
                    nihtTnum = 0
                )
            )
        }
        // 1 standby (pdiaDvCd="4") with zero distance -> excluded from travel pay & allowance, tracked separately
        rows.add(
            PayStatementRow(
                num = 20,
                pjtDt = "20261120",
                pdiaNo = "901",
                offdDdFlg = "N",
                alcSpecDvCd = "11",
                pdiaDvCd = "4",
                totPjtTnum = 480,
                totPjtDst = 0.0,
                hldyTnum = 0,
                extnTnum = 0,
                nihtTnum = 0
            )
        )
        // 1 standby (pdiaDvCd="4") with actual operating distance -> confirmed in both
        rows.add(
            PayStatementRow(
                num = 21,
                pjtDt = "20261121",
                pdiaNo = "902",
                offdDdFlg = "N",
                alcSpecDvCd = "11",
                pdiaDvCd = "4",
                totPjtTnum = 510,
                totPjtDst = 45.2,
                hldyTnum = 0,
                extnTnum = 0,
                nihtTnum = 0
            )
        )
        // 1 planned normal duty (alcSpecDvCd="12", distance=0) -> planned in both travel pay & allowance
        rows.add(
            PayStatementRow(
                num = 22,
                pjtDt = "20261122",
                pdiaNo = "1607",
                offdDdFlg = "N",
                alcSpecDvCd = "12",
                pdiaDvCd = "3",
                totPjtTnum = 0,
                totPjtDst = 0.0,
                hldyTnum = 0,
                extnTnum = 0,
                nihtTnum = 0
            )
        )
        // 1 planned holiday duty (alcSpecDvCd="11", offdDdFlg="Y", distance=0) -> planned in travel pay, excluded from allowance
        rows.add(
            PayStatementRow(
                num = 23,
                pjtDt = "20261123",
                pdiaNo = "1639",
                offdDdFlg = "Y",
                alcSpecDvCd = "11",
                pdiaDvCd = "3",
                totPjtTnum = 0,
                totPjtDst = 0.0,
                hldyTnum = 0,
                extnTnum = 0,
                nihtTnum = 0
            )
        )

        val report = PayStatementParser.buildReport(rows)
        val el = report.eligibility

        // Confirmed(16 normal + 3 holiday + 1 standby) + Planned(1 normal + 1 holiday) = 22
        assertEquals(22, el.operatingDaysIncludingHoliday)
        assertEquals(20, el.confirmedTravelDays)
        assertEquals(2, el.plannedTravelDays)
        assertTrue(el.isTravelPayEligible)

        // Confirmed(16 normal + 1 standby) + Planned(1 normal) = 18 (holidays excluded)
        assertEquals(18, el.operatingDaysExcludingHoliday)
        assertEquals(17, el.confirmedAllowanceDays)
        assertEquals(1, el.plannedAllowanceDays)
        assertTrue(el.isAllowanceEligible)

        assertEquals(4, el.holidayOperatingDays)
        assertEquals(1, el.standbyZeroDistanceDays)
        assertEquals(1, el.standbyWithOperatingDays)
        assertTrue(el.hasStandbyZeroDistance)
        assertEquals(23, el.travelDaysIfStandbyIncluded)
        assertEquals(19, el.allowanceDaysIfStandbyIncluded)
    }

    @Test
    fun isStandbyDia_matchesPdiaDvCd4Only() {
        val standby = PayStatementRow(1, "20261101", "905", "N", "11", "4", 480, 0.0, 0, 0, 0)
        val regular900 = PayStatementRow(2, "20261102", "905", "N", "11", "3", 480, 50.0, 0, 0, 0)
        val leave = PayStatementRow(3, "20261103", "연차", "N", "21", "", 0, 0.0, 0, 0, 0)

        assertTrue(standby.isStandbyDia)
        assertFalse(regular900.isStandbyDia)
        assertFalse(leave.isStandbyDia)
    }
}
