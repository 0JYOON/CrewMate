package com.example.crewmate.telegram

import com.example.crewmate.data.remote.xcrew.DiaDetailSegment
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

data class SubwayNowTrackTarget(
    val trainNo: String,              // K Prefix 정규화된 열차번호 (e.g. "K507")
    val originalTrainNo: String,      // 원본 열차번호 (e.g. "507", "S507")
    val targetStation: String,        // 시작역 (e.g. "수원")
    val baseDepartureTime: LocalTime, // 기준 출발시각 (선행 감시 존재 시 감시 시작시각)
    val baseDateTime: LocalDateTime,  // 기준 일시
    val originalSegment: DiaDetailSegment,
    val hasPrecedingGamsi: Boolean,
    val precedingGamsiTime: LocalTime? = null,
    val rawCommand: String,           // 봇 전송용 명령어 (e.g. "K507 수원 3 14:15")
    val trackStartTimeStr: String,    // 추적 개시 시각 문자열 (e.g. "14:15")
    val trackStartDateTime: LocalDateTime,
    val sendDateTime: LocalDateTime   // 예약 전송 시각 (e.g. 출발 30분 전)
)

object SubwayNowRuleEvaluator {

    private val TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm", Locale.ROOT)

    fun isRank1(pjtName: String?): Boolean {
        val name = pjtName?.trim().orEmpty()
        return name.contains("운전") || name.contains("승무") || name.contains("출고준비") || name.contains("입환")
    }

    fun isGamsi(pjtName: String?): Boolean {
        return pjtName?.trim()?.contains("감시") == true
    }

    fun normalizeTrainNo(raw: String?): String {
        val trimmed = raw?.trim().orEmpty()
        val digits = trimmed.filter { it.isDigit() }
        return if (digits.isNotEmpty()) "K$digits" else trimmed.uppercase()
    }

    fun parseTime(s: String?): LocalTime? {
        val raw = s?.trim()?.replace(":", "")?.filter { it.isDigit() } ?: return null
        if (raw.length < 4) return null
        val h = raw.substring(0, 2).toIntOrNull() ?: return null
        val m = raw.substring(2, 4).toIntOrNull() ?: return null
        return runCatching { LocalTime.of(if (h >= 24) h % 24 else h, m) }.getOrNull()
    }

    /**
     * 당일 다이아 세그먼트 목록을 4대 필터링 규칙에 따라 평가하여
     * 텔레그램 추적 대상 열차 목록을 산출합니다.
     *
     * 규칙 1: 1순위 업무(운전/승무/출고준비/입환) & 열차번호 보유 구간 대상
     * 규칙 2: 1순위 -> 감시 -> 1순위 (Sandwiched) 패턴인 경우 두 번째 1순위 업무는 연속 업무이므로 제외
     * 규칙 3: 열차번호는 무조건 K Prefix로 정규화 (1601 -> K1601, S507 -> K507)
     * 규칙 4: 1순위 직전에 감시가 있는 경우, 출발시각은 해당 감시의 시작시각(depTm)을 채택
     */
    fun evaluateTargets(
        segments: List<DiaDetailSegment>,
        workDate: LocalDate,
        sendLeadMinutes: Int = 30,
        trackLeadMinutes: Int = 15,
        trackInterval: Int = 3
    ): List<SubwayNowTrackTarget> {
        if (segments.isEmpty()) return emptyList()

        // 1. 자정 교차를 고려한 일시 매핑
        var currentDate = workDate
        var lastTime = LocalTime.MIN
        val mappedSegments = segments.map { seg ->
            val dpt = parseTime(seg.dptTm)
            val arv = parseTime(seg.arvTm)
            if (dpt != null && dpt < lastTime && lastTime.hour >= 18 && dpt.hour <= 12) {
                currentDate = currentDate.plusDays(1)
            }
            if (dpt != null) lastTime = dpt
            val dptDateTime = dpt?.let { currentDate.atTime(it) }
            val arvDateTime = arv?.let {
                val arvDate = if (dpt != null && arv < dpt) currentDate.plusDays(1) else currentDate
                arvDate.atTime(it)
            }
            Triple(seg, dptDateTime, arvDateTime)
        }

        val targets = mutableListOf<SubwayNowTrackTarget>()

        for (i in mappedSegments.indices) {
            val (seg, dptDt, _) = mappedSegments[i]
            val pjtName = seg.pjtHrDvNm.orEmpty()
            val rawTrainNo = seg.trnNo?.trim().orEmpty()

            // 규칙 1: 1순위 업무이고 열차번호가 존재하는 구간
            if (!isRank1(pjtName) || rawTrainNo.isEmpty()) {
                continue
            }

            // 시작역 검증 (열차가 진입하여 승무원이 타야 하는 역)
            val startStation = seg.dptStnNm?.trim().orEmpty().ifEmpty {
                seg.arvStnNm?.trim().orEmpty()
            }
            if (startStation.isEmpty()) {
                continue
            }

            // 직전 업무 검증 (감시 여부 및 Sandwiched Duty 판별)
            var hasPrecedingGamsi = false
            var precedingGamsiTime: LocalTime? = null
            var precedingGamsiDt: LocalDateTime? = null
            var isSandwiched = false

            if (i > 0) {
                val (prevSeg, prevDptDt, _) = mappedSegments[i - 1]
                if (isGamsi(prevSeg.pjtHrDvNm)) {
                    hasPrecedingGamsi = true
                    precedingGamsiTime = parseTime(prevSeg.dptTm)
                    precedingGamsiDt = prevDptDt

                    // 규칙 2: 감시 직전의 유효 업무가 1순위였는지 검사 (Rank 1 -> 감시 -> Rank 1)
                    for (j in i - 2 downTo 0) {
                        val (earlierSeg, _, _) = mappedSegments[j]
                        val earlierName = earlierSeg.pjtHrDvNm.orEmpty()
                        if (isRank1(earlierName)) {
                            isSandwiched = true
                            break
                        } else if (earlierName.contains("휴게") || earlierName.contains("대기") || earlierName.contains("정리")) {
                            // 중간에 휴게/정리 등이 있다면 Sandwiched가 아님
                            break
                        }
                    }
                }
            }

            // 규칙 2 적용: Sandwiched Duty는 연속 업무이므로 추적 대상에서 제외
            if (isSandwiched) {
                continue
            }

            // 규칙 4 적용: 선행 감시가 있으면 감시 시작시각을 기준시각으로 간주
            val baseTime = if (hasPrecedingGamsi && precedingGamsiTime != null) {
                precedingGamsiTime
            } else {
                parseTime(seg.dptTm) ?: continue
            }

            val baseDt = if (hasPrecedingGamsi && precedingGamsiDt != null) {
                precedingGamsiDt
            } else {
                dptDt ?: continue
            }

            // 규칙 3 적용: K-Prefix 열차번호 정규화
            val normalizedTrainNo = normalizeTrainNo(rawTrainNo)

            // 추적 시작시각 및 예약 전송시각 계산
            val trackStartDt = baseDt.minusMinutes(trackLeadMinutes.toLong())
            val trackStartTimeStr = trackStartDt.format(TIME_FORMATTER)

            val sendLead = if (sendLeadMinutes < 0) 30 else sendLeadMinutes
            val sendDt = baseDt.minusMinutes(sendLead.toLong())

            // SubwayNowBot Case 1-3 규격: [열번] [목표역] [주기] [시작시각]
            val rawCommand = "$normalizedTrainNo $startStation $trackInterval $trackStartTimeStr"

            targets.add(
                SubwayNowTrackTarget(
                    trainNo = normalizedTrainNo,
                    originalTrainNo = rawTrainNo,
                    targetStation = startStation,
                    baseDepartureTime = baseTime,
                    baseDateTime = baseDt,
                    originalSegment = seg,
                    hasPrecedingGamsi = hasPrecedingGamsi,
                    precedingGamsiTime = precedingGamsiTime,
                    rawCommand = rawCommand,
                    trackStartTimeStr = trackStartTimeStr,
                    trackStartDateTime = trackStartDt,
                    sendDateTime = sendDt
                )
            )
        }

        return targets
    }
}
