package com.example.crewmate.labs.paystatement

import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.format.DateTimeFormatter
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
        val sortedRows = rows.sortedBy { it.pjtDt }
        val totalMinutes = sortedRows.sumOf { it.totPjtTnum }
        val totalDistanceRaw = sortedRows.sumOf { it.totPjtDst }
        val totalDistance = (totalDistanceRaw * 10.0).roundToInt() / 10.0
        val holidayMinutes = sortedRows.sumOf { it.hldyTnum }
        val overtimeMinutes = sortedRows.sumOf { it.extnTnum }
        val nightMinutes = sortedRows.sumOf { it.nihtTnum }

        // 야간 2일차(alcSpecDvCd == "12")는 시간/거리 등이 0이더라도 전날 근무가 확정이면 확정으로 판별
        val confirmedByIndex = BooleanArray(sortedRows.size)
        for (i in sortedRows.indices) {
            val row = sortedRows[i]
            val prevConfirmed = if (i > 0 && row.alcSpecDvCd == "12") {
                confirmedByIndex[i - 1] && isConsecutiveDate(sortedRows[i - 1].pjtDt, row.pjtDt)
            } else {
                false
            }
            confirmedByIndex[i] = row.hasOperatingDistance || prevConfirmed
        }

        val operatingIncludingHoliday = sortedRows.count { it.isTravelPayCounted }
        val operatingExcludingHoliday = sortedRows.count { it.isAllowanceCounted }
        var confirmedTravelDays = 0
        var confirmedAllowanceDays = 0
        for (i in sortedRows.indices) {
            val row = sortedRows[i]
            if (confirmedByIndex[i] && row.isTravelPayCounted) {
                confirmedTravelDays++
                if (!row.isHolidayWork) {
                    confirmedAllowanceDays++
                }
            }
        }
        val holidayOperatingDays = sortedRows.count { it.isTravelPayCounted && it.isHolidayWork }

        val standbyZeroDays = sortedRows.count { it.isStandbyDia && !it.hasOperatingDistance }
        val standbyZeroNonHolidayDays = sortedRows.count { it.isStandbyDia && !it.hasOperatingDistance && !it.isHolidayWork }
        val standbyWithOperatingDays = sortedRows.count { it.isStandbyDia && it.hasOperatingDistance }

        return PayStatementReport(
            rows = sortedRows,
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
                confirmedTravelDays = confirmedTravelDays,
                confirmedAllowanceDays = confirmedAllowanceDays,
                holidayOperatingDays = holidayOperatingDays,
                standbyZeroDistanceDays = standbyZeroDays,
                standbyZeroDistanceNonHolidayDays = standbyZeroNonHolidayDays,
                standbyWithOperatingDays = standbyWithOperatingDays
            )
        )
    }

    private fun isConsecutiveDate(prevYmd: String, currYmd: String): Boolean {
        val p = prevYmd.filter { it.isDigit() }
        val c = currYmd.filter { it.isDigit() }
        if (p.length != 8 || c.length != 8) return true
        return runCatching {
            val prevDate = LocalDate.parse(p, DateTimeFormatter.BASIC_ISO_DATE)
            val currDate = LocalDate.parse(c, DateTimeFormatter.BASIC_ISO_DATE)
            prevDate.plusDays(1) == currDate
        }.getOrDefault(false)
    }
}
