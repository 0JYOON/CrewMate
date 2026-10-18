package com.example.crewmate.schedule

data class WorkSchedule(
    val workDate: String? = null,
    val diaNo: String? = null,
    val trainNo: String? = null,
    val trainNo2: String? = null,
    val startTime: String? = null,
    val endTime: String? = null,
    val nonActiveType: String? = null,
    val isContinuation: Boolean = false,
    val isHoliday: Boolean = false,
    val alcSpecDvCd: String? = null,
    val hldyYn: String? = null,
    val isChanged: Boolean = false,
)

enum class ShiftType {
    DAYTIME,        // 주간(일근): 오늘 "11" && 내일 != "12"
    OVERNIGHT_DAY1,  // 야간(박) 1일 차: 오늘 "11" && 내일 "12"
    OVERNIGHT_DAY2   // 야간(박) 2일 차: 오늘 "12"
}

fun List<WorkSchedule>.determineShiftType(targetDateStr: String): ShiftType {
    val todaySched = find { it.workDate == targetDateStr }
    val todayAlc = todaySched?.alcSpecDvCd?.trim()

    if (todayAlc == "12") return ShiftType.OVERNIGHT_DAY2

    if (todayAlc == "11") {
        val date = parseDate(targetDateStr)
        if (date != null) {
            val tomorrowStr = date.plusDays(1).format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd", java.util.Locale.ROOT))
            val tomorrowSched = find { it.workDate == tomorrowStr }
            if (tomorrowSched?.alcSpecDvCd?.trim() == "12") {
                return ShiftType.OVERNIGHT_DAY1
            }
        }
    }

    return ShiftType.DAYTIME
}

private fun parseDate(dateStr: String): java.time.LocalDate? {
    val raw = dateStr.filter { it.isDigit() }
    if (raw.length != 8) return null
    return try {
        java.time.LocalDate.parse(raw, java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd", java.util.Locale.ROOT))
    } catch (_: Exception) {
        null
    }
}