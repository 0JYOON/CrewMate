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
    private val diaDetailRepository: DiaDetailRepository,
    private val dataSource: XcrewWebViewXhrDataSource,
    private val crewWorkStatusRepository: CrewWorkStatusRepository,
    private val xcrewSession: XcrewWebViewSession,
    private val appContext: android.content.Context? = null,
    private val isOfflineModeProvider: () -> Boolean = { false }
) {
    suspend fun getTodayOrNextWork(
        today: LocalDate = com.example.crewmate.util.AppDateProvider.today,
        localOnly: Boolean = false,
    ): TodayWorkResult? {
        val isOffline = isOfflineModeProvider() || localOnly
        val shouldRefreshSchedule = !isOffline && (xcrewSession.isSessionCurrentlyValid() || xcrewSession.isMockReviewSession)
        val currentMonth = YearMonth.from(today)
        val currentSchedules = if (localOnly) {
            workScheduleRepository.getCachedSchedulesOnly(currentMonth.toApiMonth()).schedules
        } else {
            workScheduleRepository.getSchedules(
                month = currentMonth.toApiMonth(),
                forceRefresh = shouldRefreshSchedule
            ).schedules
        }
        val candidates = currentSchedules.toDiaCandidates().filter { it.date >= today }
        val todayCandidates = candidates.filter { it.date == today }

        val yesterday = today.minusDays(1)
        val yesterdayMonth = YearMonth.from(yesterday)
        val yesterdaySchedules = if (yesterdayMonth == currentMonth) {
            currentSchedules
        } else if (localOnly) {
            workScheduleRepository.getCachedSchedulesOnly(yesterdayMonth.toApiMonth()).schedules
        } else {
            workScheduleRepository.getSchedules(yesterdayMonth.toApiMonth()).schedules
        }
        val yesterdayCandidates = yesterdaySchedules.toDiaCandidates().filter { it.date == yesterday }

        val todayHasAlcSpec12 = currentSchedules.any {
            it.workDate?.let(::parseDate) == today && it.alcSpecDvCd?.trim() == "12"
        }

        val yesterdayOvernightCandidates = yesterdayCandidates.filter { cand ->
            val st = cand.schedule.startTime
            val et = cand.schedule.endTime
            val isNight11 = cand.schedule.alcSpecDvCd?.trim() == "11"
            (st != null && et != null && st > et) || cand.schedule.isContinuation || (isNight11 && todayHasAlcSpec12)
        }

        val selected = when {
            (todayHasAlcSpec12 || yesterdayOvernightCandidates.isNotEmpty()) && todayCandidates.isEmpty() -> {
                yesterdayOvernightCandidates.ifEmpty { todayCandidates.ifEmpty { candidates } }
            }
            todayCandidates.isNotEmpty() -> {
                if (yesterdayOvernightCandidates.isNotEmpty()) {
                    yesterdayOvernightCandidates + todayCandidates
                } else {
                    todayCandidates
                }
            }
            candidates.isNotEmpty() -> {
                val nearestDate = candidates.minOf { it.date }
                candidates.filter { it.date == nearestDate }
            }
            else -> {
                val nextMonth = currentMonth.plusMonths(1)
                val nextMonthSchedules = if (localOnly) {
                    workScheduleRepository.getCachedSchedulesOnly(nextMonth.toApiMonth()).schedules
                } else {
                    workScheduleRepository.getSchedules(nextMonth.toApiMonth()).schedules
                }
                val nextCandidates = nextMonthSchedules
                    .toDiaCandidates()
                    .filter { it.date > today }
                if (nextCandidates.isEmpty()) return null
                val nearestDate = nextCandidates.minOf { it.date }
                nextCandidates.filter { it.date == nearestDate }
            }
        }
        if (selected.isEmpty()) return null

        val unique = selected.distinctBy { it.date to (it.schedule.diaNo to it.schedule.isContinuation) }
        val details = unique.map { candidate ->
            try {
                val pjtDt = candidate.date.format(DATE_FORMAT)
                val diaNo = candidate.schedule.diaNo.orEmpty()
                val primaryDetail = if (localOnly) {
                    diaDetailRepository.getCachedDiaDetailOnly(pjtDt = pjtDt, pdiaNo = diaNo)
                } else {
                    diaDetailRepository.fetchDiaDetail(
                        pjtDt = pjtDt,
                        pdiaNo = diaNo,
                    )
                }
                val detail = if (primaryDetail.segments.isEmpty() && candidate.schedule.isContinuation) {
                    val prevDt = candidate.date.minusDays(1).format(DATE_FORMAT)
                    runCatching {
                        if (localOnly) {
                            diaDetailRepository.getCachedDiaDetailOnly(prevDt, diaNo)
                        } else {
                            diaDetailRepository.fetchDiaDetail(prevDt, diaNo)
                        }
                    }.getOrDefault(primaryDetail)
                } else {
                    primaryDetail
                }
                TodayWorkDia(candidate.schedule, detail)
            } catch (e: Exception) {
                val fallbackDetail = DiaDetailResponse(
                    effectiveTerm = null,
                    runDtDv = null,
                    segments = emptyList(),
                    summaries = emptyList(),
                    crewMeta = null
                )
                TodayWorkDia(candidate.schedule, fallbackDetail)
            }
        }
        val selectedDate = unique.first().date
        val empNm = xcrewSession.userInfo.value?.first ?: ""

        val selectedDateStr = selectedDate.format(DATE_FORMAT)

        val tomorrow = selectedDate.plusDays(1)
        val tomorrowMonth = YearMonth.from(tomorrow)
        val tomorrowSchedules = if (tomorrowMonth == currentMonth) {
            currentSchedules
        } else {
            runCatching {
                if (localOnly) {
                    workScheduleRepository.getCachedSchedulesOnly(tomorrowMonth.toApiMonth()).schedules
                } else {
                    workScheduleRepository.getSchedules(tomorrowMonth.toApiMonth()).schedules
                }
            }.getOrDefault(emptyList())
        }

        val allKnownSchedules = (yesterdaySchedules + currentSchedules + tomorrowSchedules)
            .distinctBy { it.workDate to (it.diaNo to it.alcSpecDvCd) }

        val shiftType = allKnownSchedules.determineShiftType(selectedDateStr)

        val datesToQuery = when (shiftType) {
            ShiftType.DAYTIME -> sortedSetOf(selectedDate)
            ShiftType.OVERNIGHT_DAY1 -> sortedSetOf(selectedDate, selectedDate.plusDays(1))
            ShiftType.OVERNIGHT_DAY2 -> sortedSetOf(selectedDate.minusDays(1), selectedDate)
        }

        val attendanceStatuses = mutableListOf<AttendanceStatusDto>()

        // 1. 먼저 로컬 Room DB에 저장된 출무현황 스냅샷을 기본 로드 (오프라인/폴백 대비)
        val ctx = appContext
        val cachedByDate = mutableMapOf<String, List<AttendanceStatusDto>>()
        if (ctx != null) {
            datesToQuery.forEach { dateToFetch ->
                val dateStr = dateToFetch.format(DateTimeFormatter.ofPattern("yyyyMMdd", Locale.ROOT))
                val snapshot = com.example.crewmate.alarm.AttendanceSnapshotStore.load(ctx, dateStr)
                if (snapshot != null && snapshot.statuses.isNotEmpty()) {
                    cachedByDate[dateStr] = snapshot.statuses
                }
            }
        }

        // 2. 온라인 모드일 때: 메뉴 진입 시마다 서버에서 실시간 최신 출무 현황 조회 (세션 유지 시 즉시 갱신)
        if (!isOffline) {
            datesToQuery.forEach { dateToFetch ->
                val dateStr = dateToFetch.format(DateTimeFormatter.ofPattern("yyyyMMdd", Locale.ROOT))
                val cachedForDate = cachedByDate[dateStr].orEmpty()

                try {
                    val currentEmpNm = empNm.ifBlank { xcrewSession.userInfo.value?.first.orEmpty() }
                    val rawStatuses = dataSource.fetchAttendanceStatus(currentEmpNm, dateStr)
                    val statuses = rawStatuses.map { dto ->
                        val cleanPjt = dto.pjtDt?.trim()?.replace("-", "")?.filter { it.isDigit() }
                        val normalizedPjtDt = if (!cleanPjt.isNullOrEmpty() && cleanPjt.length == 8) cleanPjt else dateStr
                        dto.copy(pjtDt = normalizedPjtDt)
                    }

                    if (statuses.isNotEmpty()) {
                        attendanceStatuses.addAll(statuses)
                        if (ctx != null) {
                            com.example.crewmate.alarm.AttendanceSnapshotStore.save(ctx, dateStr, statuses)
                        }
                    } else {
                        attendanceStatuses.addAll(cachedForDate)
                    }
                } catch (_: Exception) {
                    attendanceStatuses.addAll(cachedForDate)
                }
            }
        } else {
            datesToQuery.forEach { dateToFetch ->
                val dateStr = dateToFetch.format(DateTimeFormatter.ofPattern("yyyyMMdd", Locale.ROOT))
                attendanceStatuses.addAll(cachedByDate[dateStr].orEmpty())
            }
        }

        // For Daytime shifts, strictly filter attendance status by today's date
        val filteredAttendanceStatuses = if (shiftType == ShiftType.DAYTIME) {
            attendanceStatuses.filter { it.pjtDt == null || it.pjtDt == selectedDateStr }
        } else {
            attendanceStatuses
        }

        val finalAttendanceStatuses = filteredAttendanceStatuses
            .distinctBy { (it.pjtDt ?: "") to ((it.trnNo ?: "") to (it.gtwcEpctTm ?: "")) }
            .sortedWith(compareBy({ it.pjtDt ?: "" }, { it.gtwcEpctTm ?: "" }))

        val result = TodayWorkResult(
            isToday = selectedDate == today,
            workDate = selectedDate,
            dias = details,
            attendanceStatusList = finalAttendanceStatuses
        )

        // Delegate scheduling of precision alarms, auto-offline, and telegram tracking to coordinator
        if (appContext != null && details.isNotEmpty()) {
            val allSegments = details.flatMap { it.detail.segments }
            com.example.crewmate.alarm.WorkDutySyncCoordinator.sync(
                context = appContext,
                workDate = selectedDate,
                segments = allSegments,
                attendanceStatuses = finalAttendanceStatuses
            )
        }

        return result
    }

    /**
     * 전체 화면(다이아/월간근무표)을 다시 불러오지 않고 오직 출무현황만 Xcrew로부터 새로 조회하여
     * 스냅샷 및 정밀 알람(출무 완료 시 자동 해제)에 즉시 반영합니다.
     */
    suspend fun refreshAttendanceStatusesOnly(currentResult: TodayWorkResult): TodayWorkResult {
        if (isOfflineModeProvider()) return currentResult

        val selectedDate = currentResult.workDate
        val selectedDateStr = selectedDate.format(DATE_FORMAT)
        val yesterday = selectedDate.minusDays(1)
        val tomorrow = selectedDate.plusDays(1)

        val months = linkedSetOf(
            YearMonth.from(yesterday).toApiMonth(),
            YearMonth.from(selectedDate).toApiMonth(),
            YearMonth.from(tomorrow).toApiMonth()
        )
        val allKnownSchedules = months.flatMap { m ->
            runCatching { workScheduleRepository.getCachedSchedulesOnly(m).schedules }.getOrDefault(emptyList())
        }.distinctBy { it.workDate to (it.diaNo to it.alcSpecDvCd) }

        val shiftType = allKnownSchedules.determineShiftType(selectedDateStr)
        val datesToQuery = when (shiftType) {
            ShiftType.DAYTIME -> sortedSetOf(selectedDate)
            ShiftType.OVERNIGHT_DAY1 -> sortedSetOf(selectedDate, selectedDate.plusDays(1))
            ShiftType.OVERNIGHT_DAY2 -> sortedSetOf(selectedDate.minusDays(1), selectedDate)
        }

        val ctx = appContext
        val cachedByDate = mutableMapOf<String, List<AttendanceStatusDto>>()
        if (ctx != null) {
            datesToQuery.forEach { dateToFetch ->
                val dateStr = dateToFetch.format(DATE_FORMAT)
                val snapshot = com.example.crewmate.alarm.AttendanceSnapshotStore.load(ctx, dateStr)
                if (snapshot != null && snapshot.statuses.isNotEmpty()) {
                    cachedByDate[dateStr] = snapshot.statuses
                }
            }
        }

        val empNm = xcrewSession.userInfo.value?.first.orEmpty()
        val attendanceStatuses = mutableListOf<AttendanceStatusDto>()
        var anyFetchError: Exception? = null

        datesToQuery.forEach { dateToFetch ->
            val dateStr = dateToFetch.format(DATE_FORMAT)
            val cachedForDate = cachedByDate[dateStr].orEmpty()
            try {
                val currentEmpNm = empNm.ifBlank { xcrewSession.userInfo.value?.first.orEmpty() }
                val rawStatuses = dataSource.fetchAttendanceStatus(currentEmpNm, dateStr)
                val statuses = rawStatuses.map { dto ->
                    val cleanPjt = dto.pjtDt?.trim()?.replace("-", "")?.filter { it.isDigit() }
                    val normalizedPjtDt = if (!cleanPjt.isNullOrEmpty() && cleanPjt.length == 8) cleanPjt else dateStr
                    dto.copy(pjtDt = normalizedPjtDt)
                }
                if (statuses.isNotEmpty()) {
                    attendanceStatuses.addAll(statuses)
                    if (ctx != null) {
                        com.example.crewmate.alarm.AttendanceSnapshotStore.save(ctx, dateStr, statuses)
                    }
                } else {
                    attendanceStatuses.addAll(cachedForDate)
                }
            } catch (e: Exception) {
                anyFetchError = e
                attendanceStatuses.addAll(cachedForDate)
            }
        }

        if (attendanceStatuses.isEmpty() && anyFetchError != null) {
            throw anyFetchError!!
        }

        val filteredAttendanceStatuses = if (shiftType == ShiftType.DAYTIME) {
            attendanceStatuses.filter { it.pjtDt == null || it.pjtDt == selectedDateStr }
        } else {
            attendanceStatuses
        }

        val finalAttendanceStatuses = filteredAttendanceStatuses
            .distinctBy { (it.pjtDt ?: "") to ((it.trnNo ?: "") to (it.gtwcEpctTm ?: "")) }
            .sortedWith(compareBy({ it.pjtDt ?: "" }, { it.gtwcEpctTm ?: "" }))

        val updatedResult = currentResult.copy(attendanceStatusList = finalAttendanceStatuses)

        if (appContext != null && currentResult.dias.isNotEmpty()) {
            val allSegments = currentResult.dias.flatMap { it.detail.segments }
            com.example.crewmate.alarm.WorkDutySyncCoordinator.sync(
                context = appContext,
                workDate = selectedDate,
                segments = allSegments,
                attendanceStatuses = finalAttendanceStatuses
            )
        }

        return updatedResult
    }

    private fun List<WorkSchedule>.toDiaCandidates(): List<DutyCandidate> = mapNotNull { schedule ->
        val date = schedule.workDate?.let(::parseDate) ?: return@mapNotNull null
        if (!schedule.isValidActiveDia()) {
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