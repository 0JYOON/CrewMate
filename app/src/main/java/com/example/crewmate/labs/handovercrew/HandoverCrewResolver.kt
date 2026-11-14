package com.example.crewmate.labs.handovercrew

import com.example.crewmate.data.remote.xcrew.DiaDetailSegment
import com.example.crewmate.schedule.TrainCrewSearchResult
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * 동일 열차의 릴레이 구간을 시간·역 순으로 정렬해 직전 승무원과 교대(후속) 승무원을 판정하는 순수 로직.
 */
object HandoverCrewResolver {

    private val apiDateFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMdd", Locale.ROOT)

    /**
     * 다이아 기준일([baseDate])과 구간 목록([segments])에서 [targetSegment]가 실제 운행하는 달력 날짜를 계산합니다.
     * 심야(18~23시 -> 00~12시) 자정 경과 시 +1일 보정합니다.
     */
    fun resolveSegmentDate(
        baseDate: LocalDate,
        segments: List<DiaDetailSegment>,
        targetSegment: DiaDetailSegment
    ): LocalDate {
        var currentDate = baseDate
        var lastHour = -1

        for (segment in segments) {
            val dptHour = segment.dptTm?.trim()?.takeIf { it.length == 4 }?.take(2)?.toIntOrNull()
            val arvHour = segment.arvTm?.trim()?.takeIf { it.length == 4 }?.take(2)?.toIntOrNull()

            if (dptHour != null) {
                if (lastHour in 18..23 && dptHour in 0..12) {
                    currentDate = currentDate.plusDays(1)
                }
                lastHour = dptHour
            }

            if (segment === targetSegment ||
                (segment.trnNo == targetSegment.trnNo &&
                    segment.dptTm == targetSegment.dptTm &&
                    segment.arvTm == targetSegment.arvTm &&
                    segment.dptStnNm == targetSegment.dptStnNm)
            ) {
                return currentDate
            }

            if (arvHour != null && lastHour != -1) {
                if (lastHour in 18..23 && arvHour in 0..12) {
                    currentDate = currentDate.plusDays(1)
                }
                lastHour = arvHour
            }
        }
        return baseDate
    }

    fun formatApiDate(date: LocalDate): String = date.format(apiDateFormatter)

    fun resolveHandover(
        trainNumber: String,
        targetDate: String,
        currentDiaNo: String,
        currentSegment: DiaDetailSegment,
        results: List<TrainCrewSearchResult>,
        fromOfflineCache: Boolean
    ): HandoverCrewReport {
        val cleanCurrentDia = currentDiaNo.trim().removePrefix("~")
        val curDptStn = currentSegment.dptStnNm?.trim().orEmpty()
        val curDptTm = currentSegment.dptTm?.trim().orEmpty()
        val curArvStn = currentSegment.arvStnNm?.trim().orEmpty()
        val curArvTm = currentSegment.arvTm?.trim().orEmpty()

        val rawRelays = results
            .distinctBy { "${it.pjtDt}|${it.pdiaNo}|${it.segment.dptTm}|${it.segment.arvTm}" }
            .map { result ->
                val seg = result.segment
                val diaClean = result.pdiaNo.trim()
                val isCurrent = diaClean.equals(cleanCurrentDia, ignoreCase = true) &&
                    (curDptTm.isEmpty() || seg.dptTm?.trim() == curDptTm || seg.dptStnNm?.trim() == curDptStn)
                HandoverRelayItem(
                    pjtDt = result.pjtDt,
                    pdiaNo = diaClean,
                    dptStnNm = seg.dptStnNm?.trim().orEmpty().ifEmpty { "-" },
                    dptTm = seg.dptTm?.trim().orEmpty(),
                    arvStnNm = seg.arvStnNm?.trim().orEmpty().ifEmpty { "-" },
                    arvTm = seg.arvTm?.trim().orEmpty(),
                    crewMembers = result.crewMembers,
                    isCurrentDia = isCurrent
                )
            }

        val hasNight = rawRelays.any { (parseHour(it.dptTm) ?: -1) in 18..23 }
        val hasEarlyMorning = rawRelays.any { (parseHour(it.dptTm) ?: -1) in 0..6 }
        val shiftEarlyMorning = hasNight && hasEarlyMorning

        val sortedRelays = rawRelays.sortedWith(
            compareBy<HandoverRelayItem>(
                { toComparableMinutes(it.dptTm, shiftEarlyMorning) },
                { toComparableMinutes(it.arvTm, shiftEarlyMorning) },
                { it.pdiaNo }
            )
        )

        val myIndex = sortedRelays.indexOfFirst { it.isCurrentDia }
            .takeIf { it >= 0 }
            ?: sortedRelays.indexOfFirst { it.pdiaNo.equals(cleanCurrentDia, ignoreCase = true) }

        val curDptMin = toComparableMinutes(curDptTm, shiftEarlyMorning)
        val curArvMin = toComparableMinutes(curArvTm, shiftEarlyMorning)

        val previousCrew = if (myIndex > 0) {
            sortedRelays[myIndex - 1]
        } else if (myIndex == -1) {
            sortedRelays.lastOrNull { relay ->
                !relay.pdiaNo.equals(cleanCurrentDia, ignoreCase = true) &&
                    ((curDptStn.isNotEmpty() && relay.arvStnNm == curDptStn) ||
                        toComparableMinutes(relay.arvTm, shiftEarlyMorning) <= curDptMin)
            }
        } else {
            null
        }

        val nextCrew = if (myIndex in 0 until sortedRelays.lastIndex) {
            sortedRelays[myIndex + 1]
        } else if (myIndex == -1) {
            sortedRelays.firstOrNull { relay ->
                !relay.pdiaNo.equals(cleanCurrentDia, ignoreCase = true) &&
                    ((curArvStn.isNotEmpty() && relay.dptStnNm == curArvStn) ||
                        toComparableMinutes(relay.dptTm, shiftEarlyMorning) >= curArvMin)
            }
        } else {
            null
        }

        return HandoverCrewReport(
            trainNumber = trainNumber,
            targetDate = targetDate,
            currentDiaNo = cleanCurrentDia,
            currentDptStnNm = curDptStn.ifEmpty { "-" },
            currentDptTm = curDptTm,
            currentArvStnNm = curArvStn.ifEmpty { "-" },
            currentArvTm = curArvTm,
            previousCrew = previousCrew,
            nextCrew = nextCrew,
            allRelays = sortedRelays,
            fromOfflineCache = fromOfflineCache
        )
    }

    private fun parseHour(hhmm: String): Int? =
        hhmm.trim().takeIf { it.length == 4 }?.take(2)?.toIntOrNull()

    private fun toComparableMinutes(hhmm: String, shiftEarlyMorning: Boolean): Int {
        val clean = hhmm.trim().filter(Char::isDigit)
        if (clean.length != 4) return Int.MAX_VALUE
        val h = clean.substring(0, 2).toIntOrNull() ?: return Int.MAX_VALUE
        val m = clean.substring(2, 4).toIntOrNull() ?: return Int.MAX_VALUE
        val base = h * 60 + m
        return if (shiftEarlyMorning && h in 0..6) base + 24 * 60 else base
    }
}
