package com.example.crewmate.labs.paystatement

import java.util.Locale
import kotlin.math.roundToInt

/**
 * Xcrew 개인근무명세서(/extrCrewMg/searchExtrIndWrkSpec.do) 단일 일자 항목.
 */
data class PayStatementRow(
    val num: Int,
    val pjtDt: String,
    val pdiaNo: String,
    val offdDdFlg: String,
    val alcSpecDvCd: String,
    val pdiaDvCd: String,
    val totPjtTnum: Int,
    val totPjtDst: Double,
    val hldyTnum: Int,
    val extnTnum: Int,
    val nihtTnum: Int
) {
    /** 900번대 대기근무 여부 (예: 901, 912 등) */
    val isStandbyDia: Boolean
        get() {
            val trimmed = pdiaNo.trim()
            val numVal = trimmed.toIntOrNull()
            if (numVal != null) return numVal in 900..999
            return Regex("^9\\d{2}(?:[^\\d].*)?$").matches(trimmed)
        }

    /** 실제 승무실적 보유 여부 (거리 > 0) */
    val hasOperatingDistance: Boolean
        get() = totPjtDst > 0.0

    /** 휴일근무 여부 (휴일시간 > 0) */
    val isHolidayWork: Boolean
        get() = hldyTnum > 0
}

/**
 * 개인근무명세서 최하단 5개 핵심 항목(시간·거리·휴일·연장·야간) 합계.
 */
data class PayStatementTotals(
    val totalMinutes: Int,
    val totalDistance: Double,
    val holidayMinutes: Int,
    val overtimeMinutes: Int,
    val nightMinutes: Int
)

/**
 * 예상 승무여비 및 승무수당 근무일수 충족 여부 판정 결과.
 */
data class PayStatementEligibilitySummary(
    val totalRowCount: Int,
    /** 휴일 포함 승무실적(거리 > 0) 근무일수 -> 승무여비 실제 산정 일수 */
    val operatingDaysIncludingHoliday: Int,
    /** 휴일 제외 승무실적(거리 > 0) 근무일수 -> 승무수당 실제 산정 일수 */
    val operatingDaysExcludingHoliday: Int,
    /** 휴일 승무실적(거리 > 0) 근무일수 */
    val holidayOperatingDays: Int,
    /** 900번대 대기근무 중 거리 0인 날 수 (전체) */
    val standbyZeroDistanceDays: Int,
    /** 900번대 대기근무 중 거리 0이며 휴일이 아닌 날 수 */
    val standbyZeroDistanceNonHolidayDays: Int,
    /** 900번대 대기근무 중 실제 승무(거리 > 0)가 있었던 날 수 */
    val standbyWithOperatingDays: Int,
    val travelPayTargetDays: Int = 20,
    val allowanceTargetDays: Int = 17
) {
    val isTravelPayEligible: Boolean
        get() = operatingDaysIncludingHoliday >= travelPayTargetDays

    val travelPayRemainingDays: Int
        get() = (travelPayTargetDays - operatingDaysIncludingHoliday).coerceAtLeast(0)

    val isAllowanceEligible: Boolean
        get() = operatingDaysExcludingHoliday >= allowanceTargetDays

    val allowanceRemainingDays: Int
        get() = (allowanceTargetDays - operatingDaysExcludingHoliday).coerceAtLeast(0)

    val hasStandbyZeroDistance: Boolean
        get() = standbyZeroDistanceDays > 0

    /** 대기(거리 0) 포함 단순 출근 일수 (승무여비 참고용) */
    val travelDaysIfStandbyIncluded: Int
        get() = operatingDaysIncludingHoliday + standbyZeroDistanceDays

    /** 대기(거리 0) 포함 단순 근무 일수 (승무수당 참고용, 휴일 제외) */
    val allowanceDaysIfStandbyIncluded: Int
        get() = operatingDaysExcludingHoliday + standbyZeroDistanceNonHolidayDays
}

data class PayStatementReport(
    val rows: List<PayStatementRow>,
    val totals: PayStatementTotals,
    val eligibility: PayStatementEligibilitySummary
)

object PayStatementFormatter {
    fun formatMinutes(minutes: Int, blankIfZero: Boolean = true): String {
        if (minutes <= 0) return if (blankIfZero) "" else "00:00"
        val h = minutes / 60
        val m = minutes % 60
        return String.format(Locale.US, "%02d:%02d", h, m)
    }

    fun formatDistance(distance: Double, blankIfZero: Boolean = false): String {
        val rounded = (distance * 10.0).roundToInt() / 10.0
        if (rounded <= 0.0) return if (blankIfZero) "" else "0"
        return if (rounded % 1.0 == 0.0) {
            String.format(Locale.US, "%,.0f", rounded)
        } else {
            String.format(Locale.US, "%,.1f", rounded)
        }
    }
}
