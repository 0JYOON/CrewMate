package com.example.crewmate.labs.routediagram

import com.example.crewmate.data.remote.xcrew.DiaDetailResponse
import com.example.crewmate.data.remote.xcrew.DiaDetailSegment
import com.example.crewmate.schedule.WorkSchedule

sealed interface RouteStep {
    val id: String

    data class Start(
        override val id: String,
        val station: String,
        val dptTm: String
    ) : RouteStep

    data class Run(
        override val id: String,
        val trnNo: String,
        val fromStation: String,
        val toStation: String,
        val dptTm: String,
        val arvTm: String,
        val durationMinutes: Int,
        val isDeadhead: Boolean,
        val isWatch: Boolean,
        val isShunt: Boolean
    ) : RouteStep

    data class Dwell(
        override val id: String,
        val station: String,
        val label: String,
        val durationMinutes: Int,
        val isBlueCore: Boolean,
        val dptTm: String,
        val arvTm: String,
        val isWatchOrPrepNode: Boolean = false,
        val displayStartTime: String = ""
    ) : RouteStep

    data class End(
        override val id: String,
        val station: String,
        val arvTm: String
    ) : RouteStep
}

data class RouteDiagramModel(
    val diaNo: String,
    val startTime: String,
    val endTime: String,
    val totalTimeMinutes: Int,
    val totalDistanceKm: Double,
    val stations: List<String>,
    val steps: List<RouteStep>
)

object RouteDiagramBuilder {

    fun formatMinutesForDwell(minutes: Int): String {
        if (minutes < 60) return minutes.toString()
        val h = minutes / 60
        val m = minutes % 60
        return "%02d:%02d".format(h, m)
    }

    fun formatTimeDisplay(raw: String?): String {
        val digits = raw.orEmpty().filter { it.isDigit() }
        return when {
            digits.length >= 4 -> "${digits.substring(0, 2)}:${digits.substring(2, 4)}"
            else -> raw.orEmpty().trim()
        }
    }

    fun formatDwellBadgeLabel(segments: List<DiaDetailSegment>): String {
        if (segments.isEmpty()) return ""
        val parts = mutableListOf<String>()
        for (s in segments) {
            val name = s.pjtHrDvNm.orEmpty().trim()
            val mins = s.pjtTnum ?: 0
            if (mins <= 0 && name.isEmpty()) continue

            val tag = when {
                name.contains("출고") -> "출고"
                name.contains("정리") || name.contains("입고") -> "정"
                name.contains("준비") -> "준"
                name.contains("입환") -> "환"
                name.contains("감시") -> "감"
                name.contains("휴게") || name.contains("식사") -> "휴"
                name.contains("승계") || name.contains("대기") -> "승"
                name.contains("편승") -> "편)"
                else -> name.take(1).ifEmpty { "업" }
            }
            parts.add("$tag${formatMinutesForDwell(mins)}")
        }
        return if (parts.isEmpty()) "" else "(${parts.joinToString(" ")})"
    }

    fun deriveStationOrder(segments: List<DiaDetailSegment>, master: RailwayMasterData): List<String> {
        val trainRuns = segments.filter { s ->
            val hasTrn = !s.trnNo.isNullOrBlank()
            val dpt = s.dptStnNm.orEmpty().trim()
            val arv = s.arvStnNm.orEmpty().trim()
            val cat = s.pjtHrDvNm.orEmpty()
            (hasTrn || cat.contains("본무") || cat.contains("운전") || cat.contains("승무")) &&
                dpt.isNotEmpty() && arv.isNotEmpty() && dpt != arv
        }

        if (trainRuns.isEmpty()) {
            val all = linkedSetOf<String>()
            segments.forEach { s ->
                s.dptStnNm?.trim()?.takeIf { it.isNotEmpty() }?.let { all.add(it) }
                s.arvStnNm?.trim()?.takeIf { it.isNotEmpty() }?.let { all.add(it) }
            }
            return RailwayMasterEngine.sortStationsByLineMaster(all.toList(), master)
        }

        val ordered = mutableListOf<String>()
        fun addUnique(stn: String) {
            if (stn.isNotEmpty() && !ordered.contains(stn)) ordered.add(stn)
        }

        trainRuns.forEach { run ->
            addUnique(run.dptStnNm.orEmpty().trim())
            addUnique(run.arvStnNm.orEmpty().trim())
        }
        segments.forEach { s ->
            s.dptStnNm?.trim()?.takeIf { it.isNotEmpty() }?.let(::addUnique)
            s.arvStnNm?.trim()?.takeIf { it.isNotEmpty() }?.let(::addUnique)
        }
        return RailwayMasterEngine.sortStationsByLineMaster(ordered, master)
    }

    fun build(
        detail: DiaDetailResponse,
        schedule: WorkSchedule? = null,
        master: RailwayMasterData = RailwayMasterData()
    ): RouteDiagramModel {
        val diaNo = (schedule?.diaNo ?: detail.effectiveTerm?.pdiaNo ?: detail.crewMeta?.pdiaNo ?: "-")
            .replace(Regex("^~\\(?|\\)?$"), "")
            .trim()
        val rawSegments = detail.segments
        val stations = deriveStationOrder(rawSegments, master)

        val steps = mutableListOf<RouteStep>()
        var dwellGroup = mutableListOf<DiaDetailSegment>()
        var dwellGroupIsBlueCore: Boolean? = null
        var lastStation = ""
        var stepIdx = 0

        fun flushDwell(stationName: String, arvTime: String? = null, dptTime: String? = null) {
            if (dwellGroup.isEmpty()) return
            val label = formatDwellBadgeLabel(dwellGroup)
            val totalMins = dwellGroup.sumOf { it.pjtTnum ?: 0 }
            val isBlueCore = dwellGroupIsBlueCore == true
            val hasWatch = dwellGroup.any { it.pjtHrDvNm.orEmpty().contains("감시") }
            val hasPrep = dwellGroup.any { it.pjtHrDvNm.orEmpty().contains("출고") }
            val isWatchOrPrepNode = hasWatch || hasPrep
            val rawDpt = dptTime ?: dwellGroup.first().dptTm.orEmpty()
            val rawArv = arvTime ?: dwellGroup.last().arvTm ?: rawDpt
            val dispTime = if (hasPrep) {
                formatTimeDisplay(rawDpt)
            } else if (hasWatch) {
                // 감시 시각이 직전 도착과 같으면 일단 마킹해두고 아래 후처리에서 직후 출발과도 비교
                formatTimeDisplay(rawDpt)
            } else ""
            if (label.isNotEmpty() || totalMins > 0) {
                steps.add(
                    RouteStep.Dwell(
                        id = "step-dwell-${stepIdx++}",
                        station = stationName.ifEmpty { lastStation },
                        label = label,
                        durationMinutes = totalMins,
                        isBlueCore = isBlueCore,
                        arvTm = rawArv,
                        dptTm = rawDpt,
                        isWatchOrPrepNode = isWatchOrPrepNode,
                        displayStartTime = dispTime
                    )
                )
            }
            dwellGroup = mutableListOf()
            dwellGroupIsBlueCore = null
        }

        val startStation = rawSegments.firstOrNull { !it.dptStnNm.isNullOrBlank() }?.dptStnNm?.trim()
            ?: stations.firstOrNull().orEmpty()
        val initialStartTime = schedule?.startTime?.takeIf { it.isNotBlank() }
            ?: rawSegments.firstOrNull { !it.dptTm.isNullOrBlank() }?.dptTm.orEmpty()

        steps.add(
            RouteStep.Start(
                id = "step-start-${stepIdx++}",
                station = startStation,
                dptTm = initialStartTime
            )
        )
        lastStation = startStation

        for (seg in rawSegments) {
            val cat = seg.pjtHrDvNm.orEmpty().trim()
            val trn = seg.trnNo.orEmpty().trim()
            val dptStn = seg.dptStnNm.orEmpty().trim()
            val arvStn = seg.arvStnNm.orEmpty().trim()
            val isWatch = cat.contains("감시")
            val isShunt = cat.contains("입환")
            val isDeadhead = cat.contains("편승")
            val isMove = dptStn.isNotEmpty() && arvStn.isNotEmpty() && dptStn != arvStn &&
                (trn.isNotEmpty() || cat.contains("본무") || cat.contains("운전") || cat.contains("승무") || isWatch || isShunt || isDeadhead)

            if (isMove) {
                flushDwell(dptStn, dptTime = seg.dptTm)

                val cleanTrn = when {
                    trn == "도" -> "도보"
                    trn.isNotEmpty() -> trn
                    isWatch || isShunt || isDeadhead -> ""
                    else -> cat
                }

                steps.add(
                    RouteStep.Run(
                        id = "step-run-${stepIdx++}",
                        trnNo = cleanTrn,
                        fromStation = dptStn,
                        toStation = arvStn,
                        dptTm = seg.dptTm.orEmpty(),
                        arvTm = seg.arvTm.orEmpty(),
                        durationMinutes = seg.pjtTnum ?: 0,
                        isDeadhead = isDeadhead,
                        isWatch = isWatch,
                        isShunt = isShunt
                    )
                )
                lastStation = arvStn
            } else {
                // 감시·출고준비·입환은 파란 실선(isBlueCore=true)으로 구분하고, 정리·승계·준비·휴게·입고정리는 1개 구간으로 통합
                val isBlueCore = isWatch || isShunt || cat.contains("출고")
                val segStation = dptStn.ifEmpty { arvStn }.ifEmpty { lastStation }
                if (dwellGroupIsBlueCore != null && (dwellGroupIsBlueCore != isBlueCore || isBlueCore)) {
                    flushDwell(segStation)
                }
                dwellGroupIsBlueCore = isBlueCore
                dwellGroup.add(seg)
                if (segStation.isNotEmpty()) lastStation = segStation
            }
        }

        flushDwell(lastStation)

        val lastEndTime = schedule?.endTime?.takeIf { it.isNotBlank() }
            ?: rawSegments.lastOrNull { !it.arvTm.isNullOrBlank() }?.arvTm.orEmpty()
        steps.add(
            RouteStep.End(
                id = "step-end-${stepIdx++}",
                station = lastStation.ifEmpty { startStation },
                arvTm = lastEndTime
            )
        )

        // 감시(감) 시각이 앞뒤 운행 구간 시각과 겹치면 시각 표기 생략
        val finalSteps = steps.mapIndexed { idx, step ->
            if (step is RouteStep.Dwell && step.displayStartTime.isNotEmpty() && step.label.contains("감") && !step.label.contains("출고")) {
                val prevRun = steps.subList(0, idx).lastOrNull { it is RouteStep.Run } as? RouteStep.Run
                val nextRun = steps.subList(idx + 1, steps.size).firstOrNull { it is RouteStep.Run } as? RouteStep.Run
                val dptFmt = formatTimeDisplay(step.dptTm)
                val arvFmt = formatTimeDisplay(step.arvTm)
                val prevArvFmt = formatTimeDisplay(prevRun?.arvTm)
                val nextDptFmt = formatTimeDisplay(nextRun?.dptTm)
                val overlaps = (dptFmt.isNotEmpty() && (dptFmt == prevArvFmt || dptFmt == nextDptFmt)) ||
                    (arvFmt.isNotEmpty() && arvFmt == nextDptFmt)
                if (overlaps) step.copy(displayStartTime = "") else step
            } else {
                step
            }
        }

        val totalMinutes = detail.summaries.firstOrNull()?.totWrkTnum
            ?: rawSegments.sumOf { it.pjtTnum ?: 0 }
        val totalDist = detail.summaries.firstOrNull()?.totPjtDst ?: 0.0

        return RouteDiagramModel(
            diaNo = diaNo,
            startTime = formatTimeDisplay(initialStartTime),
            endTime = formatTimeDisplay(lastEndTime),
            totalTimeMinutes = totalMinutes,
            totalDistanceKm = totalDist,
            stations = stations,
            steps = finalSteps
        )
    }
}
