package com.example.crewmate.schedule

import com.example.crewmate.data.remote.xcrew.AttendanceStatusDto
import com.example.crewmate.data.remote.xcrew.DiaDetailDataSource
import com.example.crewmate.data.remote.xcrew.DiaDetailResponse
import com.example.crewmate.data.remote.xcrew.XcrewWebViewSession
import com.example.crewmate.data.remote.xcrew.XcrewWebViewXhrDataSource
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.Locale

data class TodayWorkResult(
    val isToday: Boolean,
    val workDate: LocalDate,
    val dias: List<TodayWorkDia>,
    val attendanceStatusList: List<AttendanceStatusDto> = emptyList()
)

data class TodayWorkDia(
    val schedule: WorkSchedule,
    val detail: DiaDetailResponse,
)

class TodayWorkRepository(
    private val workScheduleRepository: WorkScheduleRepository,
    private val diaDetailDataSource: DiaDetailDataSource,
    private val dataSource: XcrewWebViewXhrDataSource,
    private val crewWorkStatusRepository: CrewWorkStatusRepository,
    private val xcrewSession: XcrewWebViewSession
) {
    suspend fun getTodayOrNextWork(today: LocalDate = LocalDate.now()): TodayWorkResult? {
        val currentMonth = YearMonth.from(today)
        val currentSchedules = workScheduleRepository.getSchedules(currentMonth.toApiMonth()).schedules
        val candidates = currentSchedules.toDiaCandidates().filter { it.date >= today }
        val todayCandidates = candidates.filter { it.date == today }

        val selected = when {
            todayCandidates.isNotEmpty() -> todayCandidates
            candidates.isNotEmpty() -> {
                val nearestDate = candidates.minOf { it.date }
                candidates.filter { it.date == nearestDate }
            }
            else -> {
                val nextMonth = currentMonth.plusMonths(1)
                val nextCandidates = workScheduleRepository.getSchedules(nextMonth.toApiMonth())
                    .schedules
                    .toDiaCandidates()
                    .filter { it.date > today }
                if (nextCandidates.isEmpty()) return null
                val nearestDate = nextCandidates.minOf { it.date }
                nextCandidates.filter { it.date == nearestDate }
            }
        }

        val unique = selected.distinctBy { it.date to (it.schedule.diaNo to it.schedule.isContinuation) }
        val details = unique.mapNotNull { candidate ->
            try {
                val pjtDt = candidate.date.format(DATE_FORMAT)
                val diaNo = candidate.schedule.diaNo.orEmpty()
                val primaryDetail = diaDetailDataSource.fetchDiaDetail(
                    pjtDt = pjtDt,
                    pdiaNo = diaNo,
                )
                val detail = if (primaryDetail.segments.isEmpty() && candidate.schedule.isContinuation) {
                    val prevDt = candidate.date.minusDays(1).format(DATE_FORMAT)
                    runCatching { diaDetailDataSource.fetchDiaDetail(prevDt, diaNo) }
                        .getOrDefault(primaryDetail)
                } else {
                    primaryDetail
                }
                TodayWorkDia(candidate.schedule, detail)
            } catch (e: Exception) {
                // 오프라인이나 통신 실패 시 상세 정보 제외 처리 (오류 방지)
                null
            }
        }
        val selectedDate = unique.first().date
        val empNm = xcrewSession.userInfo.value?.first ?: ""

        val attendanceStatuses = mutableListOf<AttendanceStatusDto>()
        runCatching {
            // 1. 야간근무 시작일(Day 1) 판단
            val isOvernightStart = details.any { dia ->
                val st = dia.schedule.startTime
                val et = dia.schedule.endTime
                st != null && et != null && st > et
            }

            // 2. 야간근무 이튿날(Day 2) 판단
            val isOvernightContinuation = details.any { it.schedule.isContinuation } || run {
                val yesterday = selectedDate.minusDays(1)
                val yesterdaySchedules = currentSchedules.filter {
                    it.workDate?.let(::parseDate) == yesterday
                }
                yesterdaySchedules.any { dia ->
                    val st = dia.startTime
                    val et = dia.endTime
                    st != null && et != null && st > et
                }
            }

            val datesToQuery = mutableSetOf<LocalDate>()
            if (isOvernightContinuation) {
                datesToQuery.add(selectedDate.minusDays(1)) // Day 1 (어제)
                datesToQuery.add(selectedDate)              // Day 2 (오늘)
            } else if (isOvernightStart) {
                datesToQuery.add(selectedDate)              // Day 1 (오늘)
                datesToQuery.add(selectedDate.plusDays(1))  // Day 2 (내일)
            } else {
                datesToQuery.add(selectedDate)
            }

            datesToQuery.forEach { dateToFetch ->
                try {
                    val dateStr = dateToFetch.format(DateTimeFormatter.ofPattern("yyyyMMdd", Locale.ROOT))
                    val statuses = dataSource.fetchAttendanceStatus(empNm, dateStr)
                    attendanceStatuses.addAll(statuses)
                } catch (e: Exception) {
                    // 오프라인이거나 통신 실패 시 빈 리스트로 처리 (충돌 방지)
                }
            }
        }

        return TodayWorkResult(
            isToday = selectedDate == today,
            workDate = selectedDate,
            dias = details,
            attendanceStatusList = attendanceStatuses
        )
    }

    private fun List<WorkSchedule>.toDiaCandidates(): List<DutyCandidate> = mapNotNull { schedule ->
        val date = schedule.workDate?.let(::parseDate) ?: return@mapNotNull null
        val diaNo = schedule.diaNo?.trim().orEmpty()
        if (diaNo.isEmpty() || diaNo == "연차" || diaNo == "S" || !diaNo.matches(DIA_NUMBER)) {
            return@mapNotNull null
        }
        DutyCandidate(date, schedule)
    }

    private fun parseDate(value: String): LocalDate? = try {
        LocalDate.parse(value, DATE_FORMAT)
    } catch (_: DateTimeParseException) {
        null
    }

    private fun YearMonth.toApiMonth(): String = format(MONTH_FORMAT)

    private data class DutyCandidate(val date: LocalDate, val schedule: WorkSchedule)

    private companion object {
        val DATE_FORMAT: DateTimeFormatter = DateTimeFormatter.BASIC_ISO_DATE
        val MONTH_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMM", Locale.ROOT)
        val DIA_NUMBER = Regex("[A-Za-z0-9]+")
    }
}