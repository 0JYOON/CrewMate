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
    /** 근무일 코드 여부 (alcSpecDvCd == "11" 또는 "12") */
    val isDutyCode: Boolean
        get() = alcSpecDvCd == "11" || alcSpecDvCd == "12"

    /** 대기근무 여부 (pdiaDvCd == "4") */
    val isStandbyDia: Boolean
        get() = pdiaDvCd == "4"

    /** 실제 승무실적 보유 여부 (거리 > 0, 확정) */
    val hasOperatingDistance: Boolean
        get() = totPjtDst > 0.0

    /** 휴일근무 여부 (offdDdFlg == "Y" 또는 휴일시간 > 0) */
    val isHolidayWork: Boolean
        get() = offdDdFlg.equals("Y", ignoreCase = true) || hldyTnum > 0

    /**
     * 단일 행 기준 확정 실적 보유 여부.
     * - 휴일근무는 거리 실적을 인정하지 않으므로 휴일시간(hldyTnum > 0)으로 확정 판단
     * - 비휴일은 운행거리(totPjtDst > 0.0)로 확정 판단
     */
    val hasConfirmedPerformance: Boolean
        get() = if (isHolidayWork) hldyTnum > 0 else totPjtDst > 0.0

    /** 승무여비 산정 포함 여부: 확정 실적 OR (근무일 코드이면서 비대기 예정근무) */
    val isTravelPayCounted: Boolean
        get() = hasConfirmedPerformance || (isDutyCode && !isStandbyDia)

    /** 승무수당 산정 포함 여부: 휴일이 아니며 (거리 실적 확정 OR 비대기 근무일) */
    val isAllowanceCounted: Boolean
        get() = !isHolidayWork && isTravelPayCounted
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
    /** 휴일 포함 예상 승무여비 산정 일수 (확정 + 비대기 예정) */
    val operatingDaysIncludingHoliday: Int,
    /** 휴일 제외 예상 승무수당 산정 일수 (확정 + 비대기 예정) */
    val operatingDaysExcludingHoliday: Int,
    /** 확정(거리 > 0) 승무여비 일수 */
    val confirmedTravelDays: Int = 0,
    /** 확정(거리 > 0, 비휴일) 승무수당 일수 */
    val confirmedAllowanceDays: Int = 0,
    /** 휴일 근무로 승무수당에서 제외된 일수 */
    val holidayOperatingDays: Int,
    /** 대기근무(pdiaDvCd == "4") 중 거리 0인 날 수 (전체) */
    val standbyZeroDistanceDays: Int,
    /** 대기근무(pdiaDvCd == "4") 중 거리 0이며 휴일이 아닌 날 수 */
    val standbyZeroDistanceNonHolidayDays: Int,
    /** 대기근무(pdiaDvCd == "4") 중 실제 승무(거리 > 0)가 있었던 날 수 */
    val standbyWithOperatingDays: Int,
    val travelPayTargetDays: Int = 20,
    val allowanceTargetDays: Int = 17
) {
    val plannedTravelDays: Int
        get() = (operatingDaysIncludingHoliday - confirmedTravelDays).coerceAtLeast(0)

    val plannedAllowanceDays: Int
        get() = (operatingDaysExcludingHoliday - confirmedAllowanceDays).coerceAtLeast(0)
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
