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
        // 1 standby 901 with zero distance -> excluded from actual operating days, tracked separately
        rows.add(
            PayStatementRow(
                num = 20,
                pjtDt = "20261120",
                pdiaNo = "901",
                offdDdFlg = "N",
                alcSpecDvCd = "11",
                pdiaDvCd = "3",
                totPjtTnum = 480,
                totPjtDst = 0.0,
                hldyTnum = 0,
                extnTnum = 0,
                nihtTnum = 0
            )
        )
        // 1 standby 902 with actual operating distance -> counted in both
        rows.add(
            PayStatementRow(
                num = 21,
                pjtDt = "20261121",
                pdiaNo = "902",
                offdDdFlg = "N",
                alcSpecDvCd = "11",
                pdiaDvCd = "3",
                totPjtTnum = 510,
                totPjtDst = 45.2,
                hldyTnum = 0,
                extnTnum = 0,
                nihtTnum = 0
            )
        )

        val report = PayStatementParser.buildReport(rows)
        val el = report.eligibility

        // 16 normal + 3 holiday + 1 standby(operating) = 20
        assertEquals(20, el.operatingDaysIncludingHoliday)
        assertTrue(el.isTravelPayEligible)

        // 16 normal + 1 standby(operating) = 17 (holidays excluded)
        assertEquals(17, el.operatingDaysExcludingHoliday)
        assertTrue(el.isAllowanceEligible)

        assertEquals(3, el.holidayOperatingDays)
        assertEquals(1, el.standbyZeroDistanceDays)
        assertEquals(1, el.standbyWithOperatingDays)
        assertTrue(el.hasStandbyZeroDistance)
        assertEquals(21, el.travelDaysIfStandbyIncluded)
        assertEquals(18, el.allowanceDaysIfStandbyIncluded)
    }

    @Test
    fun isStandbyDia_matchesThreeDigit900sOnly() {
        val standby = PayStatementRow(1, "20261101", "905", "N", "11", "3", 480, 0.0, 0, 0, 0)
        val regular = PayStatementRow(2, "20261102", "1905", "N", "11", "3", 480, 50.0, 0, 0, 0)
        val leave = PayStatementRow(3, "20261103", "연차", "N", "21", "", 0, 0.0, 0, 0, 0)

        assertTrue(standby.isStandbyDia)
        assertFalse(regular.isStandbyDia)
        assertFalse(leave.isStandbyDia)
    }
}
