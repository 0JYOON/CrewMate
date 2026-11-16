package com.example.crewmate.labs.diarating

import com.example.crewmate.data.remote.xcrew.DiaDetailResponse
import com.example.crewmate.schedule.WorkSchedule

/**
 * 다이아 상세(운전시간, 총사업시간, 야간 취침역) 및 사용자 규칙을 바탕으로 다이아 등급을 산출합니다.
 */
object DiaRatingEvaluator {

    fun evaluate(
        diaNo: String?,
        detail: DiaDetailResponse?,
        schedule: WorkSchedule?,
        rules: DiaRatingRules
    ): DiaRatingResult? {
        val cleanDia = diaNo?.trim()?.removePrefix("~").orEmpty()
        if (cleanDia.isEmpty() || cleanDia == "S" || cleanDia == "연차") return null

        val manual = rules.customOverrides[cleanDia]
        val imported = rules.importedRatings[cleanDia]
        val driveMinutes = extractDriveMinutes(detail)
        val totalMinutes = extractTotalMinutes(detail, schedule)
        val sleepStation = extractSleepStation(detail)

        if (manual != null) {
            return DiaRatingResult(
                grade = manual,
                reasons = listOf("사용자 수동 지정 (${manual.label})"),
                sleepStation = sleepStation,
                driveMinutes = driveMinutes,
                totalMinutes = totalMinutes,
                isManualOverride = true,
                customLabel = imported?.customLabel?.takeIf { it.isNotEmpty() }
            )
        }

        if (imported != null) {
            val impReasons = buildList {
                add("편집기 평가 자료 매칭 (${imported.customLabel.ifEmpty { imported.grade.label }})")
                if (imported.pattern.isNotEmpty()) add("패턴: ${imported.pattern}")
                if (imported.driveRatioPct > 0.0) add("운전 비율: ${imported.driveRatioPct}%")
                if (imported.note.isNotEmpty()) add("메모: ${imported.note}")
            }
            return DiaRatingResult(
                grade = imported.grade,
                reasons = impReasons,
                sleepStation = sleepStation,
                driveMinutes = driveMinutes,
                totalMinutes = totalMinutes,
                isManualOverride = false,
                customLabel = imported.customLabel.takeIf { it.isNotEmpty() }
            )
        }

        if (driveMinutes == 0 && totalMinutes == 0 && sleepStation == null) {
            return null
        }

        val reasons = mutableListOf<String>()
        if (driveMinutes > 0) {
            reasons.add("운전 ${formatMinutes(driveMinutes)}")
        }
        if (totalMinutes > 0) {
            reasons.add("총사업 ${formatMinutes(totalMinutes)}")
        }
        if (sleepStation != null) {
            reasons.add("취침역 $sleepStation")
        }

        val isHeavyStation = sleepStation != null && rules.heavySleepStations.any {
            it.equals(sleepStation, ignoreCase = true)
        }
        val isPreferredStation = sleepStation != null && rules.preferredSleepStations.any {
            it.equals(sleepStation, ignoreCase = true)
        }

        val grade = when {
            isHeavyStation -> {
                reasons.add("비선호 취침역 기준 충족")
                DiaRatingGrade.HEAVY
            }
            driveMinutes >= rules.heavyMinDriveMinutes -> {
                reasons.add("운전 ≥ ${formatMinutes(rules.heavyMinDriveMinutes)}")
                DiaRatingGrade.HEAVY
            }
            totalMinutes >= rules.heavyMinTotalMinutes -> {
                reasons.add("총사업 ≥ ${formatMinutes(rules.heavyMinTotalMinutes)}")
                DiaRatingGrade.HEAVY
            }
            isPreferredStation -> {
                reasons.add("선호 취침역 기준 충족")
                DiaRatingGrade.CUSTOM
            }
            driveMinutes in 1..rules.lightMaxDriveMinutes -> {
                reasons.add("운전 ≤ ${formatMinutes(rules.lightMaxDriveMinutes)}")
                DiaRatingGrade.LIGHT
            }
            driveMinutes == 0 && totalMinutes in 1..(rules.lightMaxDriveMinutes + 120) -> {
                reasons.add("단시간 사업")
                DiaRatingGrade.LIGHT
            }
            else -> DiaRatingGrade.NORMAL
        }

        return DiaRatingResult(
            grade = grade,
            reasons = reasons,
            sleepStation = sleepStation,
            driveMinutes = driveMinutes,
            totalMinutes = totalMinutes,
            isManualOverride = false
        )
    }

    private fun extractDriveMinutes(detail: DiaDetailResponse?): Int {
        if (detail == null) return 0
        val fromSummary = detail.summaries
            .filter {
                val nm = it.pjtHrDvNm?.trim().orEmpty()
                nm.contains("운전") || nm.contains("승무")
            }
            .sumOf { it.pjtTnum ?: 0 }
        if (fromSummary > 0) return fromSummary

        return detail.segments
            .filter {
                val nm = it.pjtHrDvNm?.trim().orEmpty()
                nm.contains("운전") || nm.contains("승무")
            }
            .sumOf { it.pjtTnum ?: 0 }
    }

    private fun extractTotalMinutes(detail: DiaDetailResponse?, schedule: WorkSchedule?): Int {
        val fromSummary = detail?.summaries?.firstOrNull()?.totWrkTnum ?: 0
        if (fromSummary > 0) return fromSummary

        val fromSegments = detail?.segments?.sumOf { it.pjtTnum ?: 0 } ?: 0
        if (fromSegments > 0) return fromSegments

        val st = parseHHmm(schedule?.startTime)
        val et = parseHHmm(schedule?.endTime)
        if (st != null && et != null) {
            return if (et >= st) et - st else (24 * 60 - st) + et
        }
        return 0
    }

    private fun extractSleepStation(detail: DiaDetailResponse?): String? {
        if (detail == null || detail.segments.isEmpty()) return null
        val restSeg = detail.segments.firstOrNull { seg ->
            val nm = seg.pjtHrDvNm?.trim().orEmpty()
            (nm.contains("휴게") || nm.contains("취침") || nm.contains("숙박")) && (seg.pjtTnum ?: 0) >= 120
        }
        return restSeg?.dptStnNm?.trim()?.takeIf { it.isNotEmpty() }
            ?: restSeg?.arvStnNm?.trim()?.takeIf { it.isNotEmpty() }
    }

    private fun parseHHmm(raw: String?): Int? {
        val digits = raw?.trim()?.filter(Char::isDigit).orEmpty()
        if (digits.length != 4) return null
        val h = digits.substring(0, 2).toIntOrNull() ?: return null
        val m = digits.substring(2, 4).toIntOrNull() ?: return null
        if (h !in 0..23 || m !in 0..59) return null
        return h * 60 + m
    }

    fun formatMinutes(minutes: Int): String {
        val h = minutes / 60
        val m = minutes % 60
        return "${h}시간 ${m.toString().padStart(2, '0')}분"
    }
}
