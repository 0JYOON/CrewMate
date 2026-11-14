package com.example.crewmate.schedule

import com.example.crewmate.database.WorkScheduleEntity

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

/**
 * [승무근무표 표준 근무 판정 규칙]
 * 1. 휴일근무 : 승무근무표의 `hldyYn` 변수값이 `Y` 이면서 `alcSpecDvCd` 변수값이 `11` 또는 `12` 일 것.
 *    - `alcSpecDvCd` 값이 `21` 이면 `hldyYn` 값과 상관없이 비실동(연차, 교육, 병가 등 승무 미수행)이므로 무조건 제외.
 *    - `hldyYn` 값이 `Y` 인 날 중에서 비실동(연차, 병가, 공가, 교육 등) 처리가 된 날은 실제 근무한 날이 아니므로 제외.
 *    - `alcSpecDvCd` 값이 `02` (휴일/휴무)인 날도 제외.
 * 2. 야간근무 : 승무근무표의 `alcSpecDvCd` 변수값이 `11` 이고, 그 다음날의 `alcSpecDvCd` 변수값이 `12` 이면,
 *    - `11` 인 날: 야간근무 1일차
 *    - `12` 인 날: 야간근무 2일차
 *    - 당일 `11` 이고 다음날 `12` 가 아니면: 주간근무(일근)
 */
enum class ShiftType {
    DAYTIME,        // 주간(일근): 오늘 "11" && 내일 != "12"
    OVERNIGHT_DAY1,  // 야간(박) 1일 차: 오늘 "11" && 내일 "12"
    OVERNIGHT_DAY2   // 야간(박) 2일 차: 오늘 "12"
}

fun WorkSchedule.isNonActive(): Boolean {
    val alc = alcSpecDvCd?.trim()
    if (alc == "21") return true

    val nonActive = nonActiveType?.trim().orEmpty()
    if (nonActive.isNotEmpty() && nonActive != "null") {
        if (nonActive in setOf("연차", "병가", "교육", "공가", "청원", "출장", "휴가", "정기교육", "생리", "경조") ||
            nonActive.contains("연차") || nonActive.contains("병가") || nonActive.contains("교육") ||
            nonActive.contains("공가") || nonActive.contains("청원") || nonActive.contains("출장") ||
            nonActive.contains("휴가")
        ) {
            return true
        }
    }

    val dia = diaNo?.trim().orEmpty()
    if (dia in setOf("연차", "병가", "교육", "공가", "청원", "출장", "휴가", "정기교육", "S", "휴일", "휴무") ||
        dia.contains("연차") || dia.contains("병가") || dia.contains("교육") ||
        dia.contains("공가") || dia.contains("청원") || dia.contains("출장")
    ) {
        return true
    }

    return false
}

fun WorkSchedule.isHolidayDutyCandidate(): Boolean {
    val isHldy = hldyYn?.trim()?.uppercase() == "Y"
    val alc = alcSpecDvCd?.trim()
    val isDutyCode = alc == "11" || alc == "12"
    return isHldy && isDutyCode && !isNonActive()
}

/**
 * 실제 열차를 승무하는 유효한 실동 다이아인지 여부를 판정합니다.
 * - 비실동(isNonActive == true)인 경우 제외 (alcSpecDvCd == "21", 연차, 교육, 병가 등)
 * - 휴무/비번(alcSpecDvCd == "02" 또는 diaNo == "S", "휴일", "휴무") 제외
 * - 다이아 번호(diaNo)가 공백이거나 유효하지 않은 특수문자("~", "-", "null", "0")인 경우 제외
 */
fun WorkSchedule.isValidActiveDia(): Boolean {
    if (isNonActive()) return false
    val alc = alcSpecDvCd?.trim()
    if (alc == "02") return false
    val dia = diaNo?.trim().orEmpty()
    if (dia.isEmpty() || dia == "null" || dia == "-" || dia == "0" || dia == "S" || dia == "휴일" || dia == "휴무" || dia == "~") {
        return false
    }
    return dia.matches(Regex("[A-Za-z0-9]+"))
}

fun WorkScheduleEntity.toDomain(): WorkSchedule = WorkSchedule(
    workDate = date,
    diaNo = diaNo,
    trainNo = trainNo,
    trainNo2 = trainNo2,
    startTime = startTime,
    endTime = endTime,
    nonActiveType = nonActiveType,
    isContinuation = isContinuation,
    isHoliday = isHoliday,
    alcSpecDvCd = alcSpecDvCd,
    hldyYn = hldyYn,
    isChanged = isChanged
)

fun WorkScheduleEntity.isValidActiveDia(): Boolean = toDomain().isValidActiveDia()

fun WorkScheduleEntity.isNonActive(): Boolean = toDomain().isNonActive()

fun List<WorkSchedule>.determineShiftType(targetDateStr: String): ShiftType {
    val cleanTarget = targetDateStr.filter { it.isDigit() }
    val todaySched = find { it.workDate?.filter { c -> c.isDigit() } == cleanTarget }
    val todayAlc = todaySched?.alcSpecDvCd?.trim()

    if (todayAlc == "12") return ShiftType.OVERNIGHT_DAY2

    val date = parseDate(cleanTarget)
    if (date != null) {
        val tomorrowStr = date.plusDays(1).format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd", java.util.Locale.ROOT))
        val tomorrowSched = find { it.workDate?.filter { c -> c.isDigit() } == tomorrowStr }
        if (todayAlc == "11" && tomorrowSched?.alcSpecDvCd?.trim() == "12") {
            return ShiftType.OVERNIGHT_DAY1
        }
        val yesterdayStr = date.minusDays(1).format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd", java.util.Locale.ROOT))
        val yesterdaySched = find { it.workDate?.filter { c -> c.isDigit() } == yesterdayStr }
        if (yesterdaySched?.alcSpecDvCd?.trim() == "11" && (todaySched?.isContinuation == true || todaySched?.diaNo?.trim()?.startsWith("~") == true)) {
            return ShiftType.OVERNIGHT_DAY2
        }
        if (todaySched?.isContinuation == true || todaySched?.diaNo?.trim()?.startsWith("~") == true) {
            return ShiftType.OVERNIGHT_DAY2
        }
        val st = todaySched?.startTime
        val et = todaySched?.endTime
        if (st != null && et != null && st > et) {
            return ShiftType.OVERNIGHT_DAY1
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