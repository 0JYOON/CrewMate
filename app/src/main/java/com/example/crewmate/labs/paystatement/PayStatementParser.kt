package com.example.crewmate.labs.paystatement

import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.roundToInt

/**
 * Xcrew 개인근무명세서 JSON 파서 및 승무여비·승무수당 충족 여부 판정기.
 */
object PayStatementParser {

    fun parse(jsonText: String): List<PayStatementRow> {
        val trimmed = jsonText.trim()
        if (trimmed.isEmpty()) return emptyList()

        val dataArray: JSONArray = if (trimmed.startsWith("[")) {
            JSONArray(trimmed)
        } else {
            val root = JSONObject(trimmed)
            root.optJSONArray("data")
                ?: root.optJSONArray("list")
                ?: root.optJSONArray("result")
                ?: JSONArray()
        }

        val rows = ArrayList<PayStatementRow>(dataArray.length())
        for (i in 0 until dataArray.length()) {
            val obj = dataArray.optJSONObject(i) ?: continue
            rows.add(
                PayStatementRow(
                    num = obj.optInt("num", i + 1),
                    pjtDt = obj.optString("pjtDt", "").trim(),
                    pdiaNo = obj.optString("pdiaNo", "").trim(),
                    offdDdFlg = obj.optString("offdDdFlg", "N").trim(),
                    alcSpecDvCd = obj.optString("alcSpecDvCd", "").trim(),
                    pdiaDvCd = obj.optString("pdiaDvCd", "").let { if (it == "null") "" else it.trim() },
                    totPjtTnum = obj.optInt("totPjtTnum", 0),
                    totPjtDst = obj.optDouble("totPjtDst", 0.0).let { if (it.isNaN()) 0.0 else it },
                    hldyTnum = obj.optInt("hldyTnum", 0),
                    extnTnum = obj.optInt("extnTnum", 0),
                    nihtTnum = obj.optInt("nihtTnum", 0)
                )
            )
        }
        return rows.sortedBy { it.pjtDt }
    }

    fun buildReport(rows: List<PayStatementRow>): PayStatementReport {
        val totalMinutes = rows.sumOf { it.totPjtTnum }
        val totalDistanceRaw = rows.sumOf { it.totPjtDst }
        val totalDistance = (totalDistanceRaw * 10.0).roundToInt() / 10.0
        val holidayMinutes = rows.sumOf { it.hldyTnum }
        val overtimeMinutes = rows.sumOf { it.extnTnum }
        val nightMinutes = rows.sumOf { it.nihtTnum }

        val operatingIncludingHoliday = rows.count { it.hasOperatingDistance }
        val operatingExcludingHoliday = rows.count { it.hasOperatingDistance && !it.isHolidayWork }
        val holidayOperatingDays = rows.count { it.hasOperatingDistance && it.isHolidayWork }

        val standbyZeroDays = rows.count { it.isStandbyDia && !it.hasOperatingDistance }
        val standbyZeroNonHolidayDays = rows.count { it.isStandbyDia && !it.hasOperatingDistance && !it.isHolidayWork }
        val standbyWithOperatingDays = rows.count { it.isStandbyDia && it.hasOperatingDistance }

        return PayStatementReport(
            rows = rows,
            totals = PayStatementTotals(
                totalMinutes = totalMinutes,
                totalDistance = totalDistance,
                holidayMinutes = holidayMinutes,
                overtimeMinutes = overtimeMinutes,
                nightMinutes = nightMinutes
            ),
            eligibility = PayStatementEligibilitySummary(
                totalRowCount = rows.size,
                operatingDaysIncludingHoliday = operatingIncludingHoliday,
                operatingDaysExcludingHoliday = operatingExcludingHoliday,
                holidayOperatingDays = holidayOperatingDays,
                standbyZeroDistanceDays = standbyZeroDays,
                standbyZeroDistanceNonHolidayDays = standbyZeroNonHolidayDays,
                standbyWithOperatingDays = standbyWithOperatingDays
            )
        )
    }
}
