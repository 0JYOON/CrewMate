package com.example.crewmate.alarm

import com.example.crewmate.data.remote.xcrew.DiaDetailSegment
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter

data class WorkAlarmItem(
    val id: Int,
    val triggerDateTime: LocalDateTime,
    val ruleTitle: String,
    val cat: String,
    val title: String,
    val message: String,
    val trainNo: String,
    val dptStnNm: String,
    val dptTimeStr: String
)

object WorkAlarmRuleEvaluator {

    private fun isRank1Cat(cat: String): Boolean {
        return cat.contains("운전") || cat.contains("승무") || cat.contains("출고준비") || cat.contains("입환")
    }

    private fun parseTimeSafe(timeStr: String?): LocalTime? {
        val raw = timeStr?.trim()?.replace(":", "")?.filter { it.isDigit() } ?: return null
        if (raw.isEmpty()) return null
        val t = raw.padStart(4, '0')
        if (t.length > 4) return null
        val h = t.substring(0, 2).toIntOrNull() ?: return null
        val m = t.substring(2, 4).toIntOrNull() ?: return null
        return try {
            if (h == 24 && m == 0) LocalTime.MIDNIGHT
            else if (h >= 24) LocalTime.of(h % 24, m)
            else LocalTime.of(h, m)
        } catch (_: Exception) {
            null
        }
    }

    private data class SegmentMeta(
        val index: Int,
        val segment: DiaDetailSegment,
        val cat: String,
        val dptDateTime: LocalDateTime,
        val arvDateTime: LocalDateTime
    )

    fun evaluate(
        segments: List<DiaDetailSegment>,
        workDate: LocalDate,
        settings: WorkAlarmSettings,
        attendanceStatuses: List<com.example.crewmate.data.remote.xcrew.AttendanceStatusDto> = emptyList()
    ): List<WorkAlarmItem> {
        if (!settings.alarmEnabled || (segments.isEmpty() && attendanceStatuses.isEmpty())) return emptyList()

        // 1. Compute LocalDateTime for all segments
        var currentDate = workDate
        var lastTime = LocalTime.MIN

        val metaList = segments.mapIndexed { idx, seg ->
            val dptStr = seg.dptTm?.trim()?.replace(":", "")
            val arvStr = seg.arvTm?.trim()?.replace(":", "")

            val dptTime = if (!dptStr.isNullOrEmpty()) parseTimeSafe(dptStr) else null
            val arvTime = if (!arvStr.isNullOrEmpty()) parseTimeSafe(arvStr) else null

            var dptDateTime: LocalDateTime? = null
            var arvDateTime: LocalDateTime? = null

            if (dptTime != null) {
                if (dptTime < lastTime && lastTime.hour >= 18 && dptTime.hour <= 12) {
                    currentDate = currentDate.plusDays(1)
                }
                dptDateTime = currentDate.atTime(dptTime)
                lastTime = dptTime
            }

            if (arvTime != null) {
                if (arvTime < lastTime && lastTime.hour >= 18 && arvTime.hour <= 12) {
                    currentDate = currentDate.plusDays(1)
                }
                arvDateTime = currentDate.atTime(arvTime)
                lastTime = arvTime
            }

            val fallback = dptDateTime ?: arvDateTime ?: currentDate.atTime(lastTime)
            val cat = seg.pjtHrDvNm?.trim().orEmpty()

            SegmentMeta(
                index = idx,
                segment = seg,
                cat = cat,
                dptDateTime = dptDateTime ?: fallback,
                arvDateTime = arvDateTime ?: fallback
            )
        }

        val alarms = mutableListOf<WorkAlarmItem>()
        val dateHash = workDate.hashCode()

        // --- Rule 0-A: Scheduled Attendance (출무 N분 전) ---
        if (settings.attendanceEnabled && attendanceStatuses.isNotEmpty()) {
            val now = LocalDateTime.now()
            attendanceStatuses.forEachIndexed { attIdx, att ->
                val actualTime = att.actualTime
                val isAlreadyAttended = !actualTime.isNullOrBlank()

                // If actual attendance time is present (completed), auto-skip alarm!
                if (!isAlreadyAttended) {
                    val scheduledTime = parseTimeSafe(att.gtwcEpctTm)
                    if (scheduledTime != null) {
                        // 1) Use AttendanceStatus's own pjtDt ("YYYYMMDD") if present
                        val attPjtDt = att.pjtDt?.trim()?.replace("-", "")?.filter { it.isDigit() }
                        val attendanceDate = if (!attPjtDt.isNullOrEmpty() && attPjtDt.length == 8) {
                            try { LocalDate.parse(attPjtDt, DateTimeFormatter.ofPattern("yyyyMMdd")) } catch (_: Exception) { workDate }
                        } else {
                            workDate
                        }

                        val attendanceDateTime = attendanceDate.atTime(scheduledTime)
                        val leadMins = settings.attendanceLeadMinutes
                        val triggerTime = attendanceDateTime.minusMinutes(leadMins.toLong())

                        // 2) If trigger time is in the past, DO NOT push to tomorrow with plusDays(1). Simply skip!
                        if (!triggerTime.isBefore(now)) {
                            val ruleTitle = "출무 ${leadMins}분 전 알람"
                            val trnNo = att.trnNo?.trim()?.takeIf { it.isNotEmpty() } ?: "-"
                            val blgNm = att.gtwcEpctBlgNm?.trim()?.takeIf { it.isNotEmpty() } ?: "출무소"
                            val dptTimeStr = att.gtwcEpctTm?.trim() ?: "-"

                            alarms.add(
                                WorkAlarmItem(
                                    id = (dateHash * 31 + attIdx * 100 + 99) and 0x7FFFFFFF,
                                    triggerDateTime = triggerTime,
                                    ruleTitle = ruleTitle,
                                    cat = "출무",
                                    title = "출무 ${leadMins}분 전",
                                    message = "[$trnNo] $blgNm 예정 출무 ($dptTimeStr)",
                                    trainNo = trnNo,
                                    dptStnNm = blgNm,
                                    dptTimeStr = dptTimeStr
                                )
                            )
                        }
                    }
                }
            }
        }

        // --- Rule 0: Commute Start (출근 N분 전) ---
        if (settings.commuteEnabled) {
            val earliestMeta = metaList.minByOrNull { it.dptDateTime }
            if (earliestMeta != null) {
                val leadMins = settings.commuteLeadMinutes
                val triggerTime = earliestMeta.dptDateTime.minusMinutes(leadMins.toLong())
                val ruleTitle = "출근 ${leadMins}분 전 알람"
                val trnNo = earliestMeta.segment.trnNo?.trim()?.takeIf { it.isNotEmpty() } ?: "-"
                val dptStn = earliestMeta.segment.dptStnNm?.trim()?.takeIf { it.isNotEmpty() } ?: "-"
                val dptTimeStr = earliestMeta.segment.dptTm?.trim() ?: "-"

                alarms.add(
                    WorkAlarmItem(
                        id = (dateHash * 31 + 0) and 0x7FFFFFFF,
                        triggerDateTime = triggerTime,
                        ruleTitle = ruleTitle,
                        cat = "출근",
                        title = "출근 ${leadMins}분 전",
                        message = "오늘의 근무 출근 준비 ($dptStn 출발 $dptTimeStr)",
                        trainNo = trnNo,
                        dptStnNm = dptStn,
                        dptTimeStr = dptTimeStr
                    )
                )
            }
        }

        for (i in metaList.indices) {
            val curr = metaList[i]
            val cat = curr.cat
            val isDrive = cat.contains("운전") || cat.contains("승무")
            val isPrep = cat.contains("출고준비")
            val isDeadhead = cat.contains("편승") || curr.segment.trnNo?.trim() == "도" || curr.segment.trnNo?.trim() == "도보"
            val isShunting = cat.contains("입환")

            val trnNo = curr.segment.trnNo?.trim()?.takeIf { it.isNotEmpty() } ?: "-"
            val dptStn = curr.segment.dptStnNm?.trim()?.takeIf { it.isNotEmpty() } ?: "-"
            val dptTimeStr = curr.segment.dptTm?.trim() ?: "-"

            // --- Rule 1: Driving (운전 / 승무) ---
            if (isDrive && settings.driveEnabled) {
                // Rule 5 (Always Active): Sandwiched Duty (1순위-감시-1순위)
                var prevValid: SegmentMeta? = null
                for (j in i - 1 downTo 0) {
                    val c = metaList[j].cat
                    if (!c.contains("휴게") && !c.contains("정리") && !c.contains("승계")) {
                        prevValid = metaList[j]
                        break
                    }
                }
                var nextValid: SegmentMeta? = null
                for (j in i + 1 until metaList.size) {
                    val c = metaList[j].cat
                    if (!c.contains("휴게") && !c.contains("정리") && !c.contains("승계")) {
                        nextValid = metaList[j]
                        break
                    }
                }
                val prevIsRank1 = prevValid != null && isRank1Cat(prevValid.cat)
                val nextIsRank1 = nextValid != null && isRank1Cat(nextValid.cat)

                if (prevIsRank1 && nextIsRank1) {
                    // Sandwiched second segment between Rank 1 items -> skip!
                    continue
                }

                // Check if preceding valid segment is Gamsi ("감시") which was preceded by Rank 1 item (Rank1 -> Gamsi -> Drive)
                if (prevValid != null && prevValid.cat.contains("감시")) {
                    var prevOfGamsi: SegmentMeta? = null
                    for (j in prevValid.index - 1 downTo 0) {
                        val c = metaList[j].cat
                        if (!c.contains("휴게") && !c.contains("정리") && !c.contains("승계")) {
                            prevOfGamsi = metaList[j]
                            break
                        }
                    }
                    if (prevOfGamsi != null && isRank1Cat(prevOfGamsi.cat)) {
                        // Preceded by Sandwiched Gamsi -> Part of continuous duty block -> Skip drive alarm!
                        continue
                    }
                }

                // Check Exception 1.2: Skip if Prep (출고준비) or Shunting (입환) within N mins before
                if (settings.driveSkipIfPrepWithinMinutes) {
                    val hasPrepOrShuntingPrev = metaList.take(i).any { prev ->
                        (prev.cat.contains("출고준비") || prev.cat.contains("입환")) &&
                                Duration.between(prev.arvDateTime, curr.dptDateTime).toMinutes() in 0..settings.drivePrepThresholdMinutes.toLong()
                    }
                    if (hasPrepOrShuntingPrev) continue
                }

                // Check Exception 1.3: Skip if Deadhead (편승) within N mins before
                if (settings.driveSkipIfDeadheadWithinMinutes) {
                    val hasDeadheadPrev = metaList.take(i).any { prev ->
                        (prev.cat.contains("편승") || prev.segment.trnNo?.trim() == "도" || prev.segment.trnNo?.trim() == "도보") &&
                                Duration.between(prev.arvDateTime, curr.dptDateTime).toMinutes() in 0..settings.driveDeadheadThresholdMinutes.toLong()
                    }
                    if (hasDeadheadPrev) continue
                }

                // Check Exception 1.1: Check if preceding Gamsi (감시) exists
                var gamsiMeta: SegmentMeta? = null
                if (settings.driveCheckGamsi && i > 0) {
                    val prevSeg = metaList[i - 1]
                    if (prevSeg.cat.contains("감시")) {
                        gamsiMeta = prevSeg
                    }
                }

                if (gamsiMeta != null) {
                    val gamsiTrnNo = gamsiMeta.segment.trnNo?.trim()?.takeIf { it.isNotEmpty() } ?: trnNo
                    val gamsiDptStn = gamsiMeta.segment.dptStnNm?.trim()?.takeIf { it.isNotEmpty() } ?: dptStn
                    val gamsiDptTimeStr = gamsiMeta.segment.dptTm?.trim() ?: dptTimeStr

                    val leadMins = settings.driveLeadMinutes
                    val triggerTime = gamsiMeta.dptDateTime.minusMinutes(leadMins.toLong())
                    val ruleTitle = "운전 ${leadMins}분 전 알람"
                    alarms.add(
                        WorkAlarmItem(
                            id = (dateHash * 31 + i * 10 + 1) and 0x7FFFFFFF,
                            triggerDateTime = triggerTime,
                            ruleTitle = ruleTitle,
                            cat = "감시",
                            title = "감시 ${leadMins}분 전",
                            message = "[$gamsiTrnNo] $gamsiDptStn 출발 ($gamsiDptTimeStr)",
                            trainNo = gamsiTrnNo,
                            dptStnNm = gamsiDptStn,
                            dptTimeStr = gamsiDptTimeStr
                        )
                    )
                } else {
                    val leadMins = settings.driveLeadMinutes
                    val triggerTime = curr.dptDateTime.minusMinutes(leadMins.toLong())
                    val label = if (cat.contains("승무")) "승무" else "운전"
                    val ruleTitle = "$label ${leadMins}분 전 알람"
                    alarms.add(
                        WorkAlarmItem(
                            id = (dateHash * 31 + i * 10 + 1) and 0x7FFFFFFF,
                            triggerDateTime = triggerTime,
                            ruleTitle = ruleTitle,
                            cat = label,
                            title = "$label ${leadMins}분 전",
                            message = "[$trnNo] $dptStn 출발 ($dptTimeStr)",
                            trainNo = trnNo,
                            dptStnNm = dptStn,
                            dptTimeStr = dptTimeStr
                        )
                    )
                }
            }

            // --- Rule 2: Prep (출고준비) ---
            if (isPrep && settings.prepEnabled) {
                val leadMins = settings.prepLeadMinutes
                val triggerTime = curr.dptDateTime.minusMinutes(leadMins.toLong())
                val ruleTitle = "출고준비 ${leadMins}분 전 알람"
                alarms.add(
                    WorkAlarmItem(
                        id = (dateHash * 31 + i * 10 + 2) and 0x7FFFFFFF,
                        triggerDateTime = triggerTime,
                        ruleTitle = ruleTitle,
                        cat = "출고준비",
                        title = "출고준비 ${leadMins}분 전",
                        message = "[$trnNo] $dptStn 출발 ($dptTimeStr)",
                        trainNo = trnNo,
                        dptStnNm = dptStn,
                        dptTimeStr = dptTimeStr
                    )
                )
            }

            // --- Rule 3: Deadhead (편승) ---
            if (isDeadhead && settings.deadheadEnabled) {
                // Exception 3.1: Prev Drive/Shunting/Deadhead within N mins
                if (settings.deadheadSkipIfPrevDuty) {
                    val hasPrevDuty = metaList.take(i).any { prev ->
                        (prev.cat.contains("운전") || prev.cat.contains("승무") || prev.cat.contains("입환") || prev.cat.contains("편승")) &&
                                Duration.between(prev.arvDateTime, curr.dptDateTime).toMinutes() in 0..settings.deadheadPrevThresholdMinutes.toLong()
                    }
                    if (hasPrevDuty) continue
                }

                // Exception 3.2: Next Drive/Shunting/Prep within N mins
                if (settings.deadheadSkipIfNextDuty) {
                    val hasNextDuty = metaList.drop(i + 1).any { next ->
                        (next.cat.contains("운전") || next.cat.contains("승무") || next.cat.contains("입환") || next.cat.contains("출고준비")) &&
                                Duration.between(curr.arvDateTime, next.dptDateTime).toMinutes() in 0..settings.deadheadNextThresholdMinutes.toLong()
                    }
                    if (hasNextDuty) continue
                }

                val leadMins = settings.deadheadLeadMinutes
                val triggerTime = curr.dptDateTime.minusMinutes(leadMins.toLong())
                val ruleTitle = "편승 ${leadMins}분 전 알람"
                alarms.add(
                    WorkAlarmItem(
                        id = (dateHash * 31 + i * 10 + 3) and 0x7FFFFFFF,
                        triggerDateTime = triggerTime,
                        ruleTitle = ruleTitle,
                        cat = "편승",
                        title = "편승 ${leadMins}분 전",
                        message = "[$trnNo] $dptStn 출발 ($dptTimeStr)",
                        trainNo = trnNo,
                        dptStnNm = dptStn,
                        dptTimeStr = dptTimeStr
                    )
                )
            }

            // --- Rule 4: Shunting (입환) ---
            if (isShunting && settings.shuntingEnabled) {
                // Exception 4.1: Prev Drive/Prep/Deadhead within N mins
                if (settings.shuntingSkipIfPrevDuty) {
                    val hasPrevDuty = metaList.take(i).any { prev ->
                        (prev.cat.contains("운전") || prev.cat.contains("승무") || prev.cat.contains("출고준비") || prev.cat.contains("편승")) &&
                                Duration.between(prev.arvDateTime, curr.dptDateTime).toMinutes() in 0..settings.shuntingPrevThresholdMinutes.toLong()
                    }
                    if (hasPrevDuty) continue
                }

                val leadMins = settings.shuntingLeadMinutes
                val triggerTime = curr.dptDateTime.minusMinutes(leadMins.toLong())
                val ruleTitle = "입환 ${leadMins}분 전 알람"
                alarms.add(
                    WorkAlarmItem(
                        id = (dateHash * 31 + i * 10 + 4) and 0x7FFFFFFF,
                        triggerDateTime = triggerTime,
                        ruleTitle = ruleTitle,
                        cat = "입환",
                        title = "입환 ${leadMins}분 전",
                        message = "[$trnNo] $dptStn 출발 ($dptTimeStr)",
                        trainNo = trnNo,
                        dptStnNm = dptStn,
                        dptTimeStr = dptTimeStr
                    )
                )
            }
        }

        return alarms.sortedBy { it.triggerDateTime }
    }
}
