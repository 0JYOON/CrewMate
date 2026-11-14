package com.example.crewmate.schedule

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

data class HolidayDutyItem(
    val dateStr: String, // "2026.10.03"
    val diaNo: String,
    val isNight: Boolean,
    val isContinuation: Boolean,
    val timeDesc: String
)

data class HolidayWorkReport(
    val year: Int,
    val totalCount: Int,
    val dayCount: Int,
    val nightCount: Int,
    val queriedDate: String, // "2026.10.29 11:30"
    val items: List<HolidayDutyItem> = emptyList(),
    val examinedDaysCount: Int = 0
)

object HolidayWorkManager {

    private const val PREF_NAME = "crewmate_holiday_work_prefs"
    private const val KEY_REPORT_PREFIX = "holiday_work_report_"

    fun getSavedReport(context: Context, year: Int): HolidayWorkReport? {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val jsonStr = prefs.getString(KEY_REPORT_PREFIX + year, null) ?: return null
        return try {
            val root = JSONObject(jsonStr)
            val itemsJson = root.optJSONArray("items") ?: JSONArray()
            val items = mutableListOf<HolidayDutyItem>()
            for (i in 0 until itemsJson.length()) {
                val obj = itemsJson.optJSONObject(i) ?: continue
                items.add(
                    HolidayDutyItem(
                        dateStr = obj.optString("dateStr"),
                        diaNo = obj.optString("diaNo"),
                        isNight = obj.optBoolean("isNight"),
                        isContinuation = obj.optBoolean("isContinuation"),
                        timeDesc = obj.optString("timeDesc")
                    )
                )
            }
            HolidayWorkReport(
                year = root.optInt("year", year),
                totalCount = root.optInt("totalCount", 0),
                dayCount = root.optInt("dayCount", 0),
                nightCount = root.optInt("nightCount", 0),
                queriedDate = root.optString("queriedDate", ""),
                items = items,
                examinedDaysCount = root.optInt("examinedDaysCount", 0)
            )
        } catch (_: Exception) {
            null
        }
    }

    fun saveReport(context: Context, report: HolidayWorkReport) {
        val root = JSONObject()
        root.put("year", report.year)
        root.put("totalCount", report.totalCount)
        root.put("dayCount", report.dayCount)
        root.put("nightCount", report.nightCount)
        root.put("queriedDate", report.queriedDate)
        root.put("examinedDaysCount", report.examinedDaysCount)

        val itemsArr = JSONArray()
        report.items.forEach { item ->
            val obj = JSONObject()
            obj.put("dateStr", item.dateStr)
            obj.put("diaNo", item.diaNo)
            obj.put("isNight", item.isNight)
            obj.put("isContinuation", item.isContinuation)
            obj.put("timeDesc", item.timeDesc)
            itemsArr.put(obj)
        }
        root.put("items", itemsArr)

        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_REPORT_PREFIX + report.year, root.toString())
            .apply()
    }

    fun calculateHolidayWork(year: Int, allSchedules: List<WorkSchedule>): HolidayWorkReport {
        val yearStr = year.toString()

        // 1. 날짜 정규화: 하이픈이나 공백 등 특수문자 제거 후 해당 연도 8자리 ("YYYYMMDD") 필터링
        val yearSchedules = allSchedules.filter {
            val d = it.workDate?.filter { c -> c.isDigit() }.orEmpty()
            d.startsWith(yearStr) && d.length == 8
        }

        // 2. 8자리 클린 날짜 기준으로 그룹핑 (동일 날짜의 모든 복수 행 보존)
        val grouped = yearSchedules.groupBy {
            it.workDate?.filter { c -> c.isDigit() }.orEmpty()
        }
        val sortedDates = grouped.keys.filter { it.length == 8 }.sorted()

        var dayCount = 0
        var nightCount = 0
        val holidayItems = mutableListOf<HolidayDutyItem>()
        var prevNightCountedDate: String? = null

        for (dateStr in sortedDates) {
            val daySchedules = grouped[dateStr].orEmpty()

            // 1. 비실동(alcSpecDvCd == "21" 또는 병가, 연차, 교육 등)일 완전 배제
            // 사유로 인해 승무업무를 하지 않는 날은 hldyYn 값과 상관없이 무조건 제외
            val isNonActiveDay = daySchedules.any { it.isNonActive() }
            if (isNonActiveDay) continue

            // 2. 휴일근무(hldyYn == "Y" && alcSpecDvCd in ("11", "12")) 대상 스케줄 탐색
            val dutySchedule = daySchedules.firstOrNull { it.isHolidayDutyCandidate() } ?: continue

            val currentAlc = dutySchedule.alcSpecDvCd?.trim()
            val st = dutySchedule.startTime?.trim().orEmpty()
            val et = dutySchedule.endTime?.trim().orEmpty()

            // 3. 야간근무 여부 판별 (승무 표준 공식):
            // 당일 "11" 이고 익일 "12" ➔ 야간 1일차
            // 당일 "12" ➔ 야간 2일차
            // 당일 "11" 이고 익일 != "12" ➔ 주간근무
            val nextDateStr = runCatching {
                LocalDate.parse(dateStr, DateTimeFormatter.ofPattern("yyyyMMdd", Locale.ROOT))
                    .plusDays(1).format(DateTimeFormatter.ofPattern("yyyyMMdd", Locale.ROOT))
            }.getOrDefault("")

            val nextDaySchedules = grouped[nextDateStr].orEmpty()
            val isNextDayOvernight2 = nextDaySchedules.any { it.alcSpecDvCd?.trim() == "12" }

            val isNightDay1 = (currentAlc == "11") && isNextDayOvernight2
            val isNightDay2 = (currentAlc == "12")

            fun formatDispDate(raw: String): String {
                return if (raw.length == 8) "${raw.substring(0, 4)}.${raw.substring(4, 6)}.${raw.substring(6, 8)}" else raw
            }

            fun formatDispTime(t: String): String {
                val clean = t.filter { it.isDigit() }
                return if (clean.length >= 4) "${clean.substring(0, 2)}:${clean.substring(2, 4)}" else t
            }

            val timeRange = if (st.isNotEmpty() && et.isNotEmpty()) {
                "${formatDispTime(st)} ~ ${formatDispTime(et)}"
            } else if (st.isNotEmpty()) {
                "${formatDispTime(st)} ~"
            } else ""

            if (isNightDay2) {
                // 전일 근무가 이미 야간근무로 카운트되었는지 확인 (2일 연속 근무 1개 단위 카운트)
                val prevDateStr = runCatching {
                    LocalDate.parse(dateStr, DateTimeFormatter.ofPattern("yyyyMMdd", Locale.ROOT))
                        .minusDays(1).format(DateTimeFormatter.ofPattern("yyyyMMdd", Locale.ROOT))
                }.getOrDefault("")

                if (prevNightCountedDate == prevDateStr) {
                    // 전일 야간 1일차에 합산되었으므로 신규 카운트 없이 내역만 보존
                    holidayItems.add(
                        HolidayDutyItem(
                            dateStr = formatDispDate(dateStr),
                            diaNo = dutySchedule.diaNo.orEmpty(),
                            isNight = true,
                            isContinuation = true,
                            timeDesc = if (timeRange.isNotEmpty()) "야간 2일차 ($timeRange)" else "야간 2일차"
                        )
                    )
                } else {
                    // 전일이 평일이고 오늘만 휴일인 야간 2일차 근무
                    nightCount++
                    prevNightCountedDate = dateStr
                    holidayItems.add(
                        HolidayDutyItem(
                            dateStr = formatDispDate(dateStr),
                            diaNo = dutySchedule.diaNo.orEmpty(),
                            isNight = true,
                            isContinuation = true,
                            timeDesc = if (timeRange.isNotEmpty()) "야간 2일차 ($timeRange)" else "야간 2일차"
                        )
                    )
                }
            } else if (isNightDay1) {
                nightCount++
                prevNightCountedDate = dateStr
                holidayItems.add(
                    HolidayDutyItem(
                        dateStr = formatDispDate(dateStr),
                        diaNo = dutySchedule.diaNo.orEmpty(),
                        isNight = true,
                        isContinuation = false,
                        timeDesc = if (timeRange.isNotEmpty()) "야간 1일차 ($timeRange)" else "야간 1일차"
                    )
                )
            } else {
                dayCount++
                holidayItems.add(
                    HolidayDutyItem(
                        dateStr = formatDispDate(dateStr),
                        diaNo = dutySchedule.diaNo.orEmpty(),
                        isNight = false,
                        isContinuation = false,
                        timeDesc = if (timeRange.isNotEmpty()) "주간 ($timeRange)" else "주간"
                    )
                )
            }
        }

        val queriedNow = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy.MM.dd HH:mm", Locale.ROOT))
        return HolidayWorkReport(
            year = year,
            totalCount = dayCount + nightCount,
            dayCount = dayCount,
            nightCount = nightCount,
            queriedDate = queriedNow,
            items = holidayItems,
            examinedDaysCount = sortedDates.size
        )
    }
}
