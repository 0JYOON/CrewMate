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
    suspend fun getTodayOrNextWork(today: LocalDate = com.example.crewmate.util.AppDateProvider.today): TodayWorkResult? {
        val currentMonth = YearMonth.from(today)
        val currentSchedules = workScheduleRepository.getSchedules(currentMonth.toApiMonth()).schedules
        val candidates = currentSchedules.toDiaCandidates().filter { it.date >= today }
        val todayCandidates = candidates.filter { it.date == today }

        val yesterday = today.minusDays(1)
        val yesterdayMonth = YearMonth.from(yesterday)
        val yesterdaySchedules = workScheduleRepository.getSchedules(yesterdayMonth.toApiMonth()).schedules
        val yesterdayCandidates = yesterdaySchedules.toDiaCandidates().filter { it.date == yesterday }

        val yesterdayOvernightCandidates = yesterdayCandidates.filter { cand ->
            val st = cand.schedule.startTime
            val et = cand.schedule.endTime
            (st != null && et != null && st > et) || cand.schedule.isContinuation
        }

        val todayHasAlcSpec12 = currentSchedules.any {
            it.workDate?.let(::parseDate) == today && it.alcSpecDvCd?.trim() == "12"
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
        val details = unique.map { candidate ->
            try {
                val pjtDt = candidate.date.format(DATE_FORMAT)
                val diaNo = candidate.schedule.diaNo.orEmpty()
                val primaryDetail = diaDetailRepository.fetchDiaDetail(
                    pjtDt = pjtDt,
                    pdiaNo = diaNo,
                )
                val detail = if (primaryDetail.segments.isEmpty() && candidate.schedule.isContinuation) {
                    val prevDt = candidate.date.minusDays(1).format(DATE_FORMAT)
                    runCatching { diaDetailRepository.fetchDiaDetail(prevDt, diaNo) }
                        .getOrDefault(primaryDetail)
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
        val shiftType = currentSchedules.determineShiftType(selectedDateStr)

        val datesToQuery = when (shiftType) {
            ShiftType.DAYTIME -> setOf(selectedDate)
            ShiftType.OVERNIGHT_DAY1 -> setOf(selectedDate, selectedDate.plusDays(1))
            ShiftType.OVERNIGHT_DAY2 -> setOf(selectedDate.minusDays(1), selectedDate)
        }

        val isOffline = isOfflineModeProvider()
        val attendanceStatuses = mutableListOf<AttendanceStatusDto>()
        if (!isOffline) {
            runCatching {
                datesToQuery.forEach { dateToFetch ->
                    try {
                        val dateStr = dateToFetch.format(DateTimeFormatter.ofPattern("yyyyMMdd", Locale.ROOT))
                        val statuses = dataSource.fetchAttendanceStatus(empNm, dateStr)
                        attendanceStatuses.addAll(statuses)

                        // Save snapshot to local Room DB for offline mode
                        if (appContext != null && statuses.isNotEmpty()) {
                            try {
                                val db = com.example.crewmate.database.CrewMateDatabase.getInstance(appContext)
                                val jsonArr = org.json.JSONArray()
                                statuses.forEach { dto ->
                                    val obj = org.json.JSONObject()
                                    obj.put("trnNo", dto.trnNo)
                                    obj.put("trnSttNm", dto.trnSttNm)
                                    obj.put("gtwcEpctBlgNm", dto.gtwcEpctBlgNm)
                                    obj.put("gtwcEpctTm", dto.gtwcEpctTm)
                                    obj.put("gtwcGubunCd", dto.gtwcGubunCd)
                                    obj.put("pjtDt", dto.pjtDt)
                                    obj.put("gtwcTpDvCd", dto.gtwcTpDvCd)
                                    obj.put("flg", dto.flg)
                                    obj.put("tnsmTm", dto.tnsmTm)
                                    obj.put("gtwcTm", dto.gtwcTm)
                                    obj.put("actlTm", dto.actlTm)
                                    jsonArr.put(obj)
                                }
                                val entity = com.example.crewmate.database.AttendanceStatusEntity(
                                    pjtDt = dateStr,
                                    jsonString = jsonArr.toString(),
                                    updatedAt = java.time.LocalDateTime.now().toString()
                                )
                                db.attendanceStatusDao().insertAttendanceStatus(entity)
                            } catch (_: Exception) {
                            }
                        }
                    } catch (_: Exception) {
                    }
                }
            }
        } else {
            // Offline Mode: Load cached attendance status snapshot from Room DB
            if (appContext != null) {
                runCatching {
                    val db = com.example.crewmate.database.CrewMateDatabase.getInstance(appContext!!)
                    datesToQuery.forEach { dateToFetch ->
                        val dateStr = dateToFetch.format(DateTimeFormatter.ofPattern("yyyyMMdd", Locale.ROOT))
                        val cachedEntity = db.attendanceStatusDao().getAttendanceStatus(dateStr)
                        if (cachedEntity != null && cachedEntity.jsonString.isNotBlank()) {
                            val parser = com.example.crewmate.data.remote.xcrew.AttendanceStatusParser()
                            val cachedList = parser.parse(cachedEntity.jsonString)
                            attendanceStatuses.addAll(cachedList)
                        }
                    }
                }
            }
        }

        // For Daytime shifts, strictly filter attendance status by today's date
        val finalAttendanceStatuses = if (shiftType == ShiftType.DAYTIME) {
            attendanceStatuses.filter { it.pjtDt == null || it.pjtDt == selectedDateStr }
        } else {
            attendanceStatuses
        }

        val result = TodayWorkResult(
            isToday = selectedDate == today,
            workDate = selectedDate,
            dias = details,
            attendanceStatusList = finalAttendanceStatuses
        )

        // Schedule alarms and auto-offline transitions if appContext is available
        if (appContext != null && details.isNotEmpty()) {
            val allSegments = details.flatMap { it.detail.segments }
            try {
                com.example.crewmate.alarm.AutoOfflineManager.scheduleAutoOffline(appContext, allSegments, selectedDate)
            } catch (e: Exception) {
                android.util.Log.e("TodayWorkRepository", "Failed to schedule auto offline", e)
            }
            try {
                val settingsStore = com.example.crewmate.alarm.WorkAlarmSettingsStore(appContext)
                val settings = settingsStore.loadSettings()
                if (settings.alarmEnabled) {
                    val alarmItems = com.example.crewmate.alarm.WorkAlarmRuleEvaluator.evaluate(
                        segments = allSegments,
                        workDate = selectedDate,
                        settings = settings,
                        attendanceStatuses = finalAttendanceStatuses
                    )
                    val dateKey = selectedDate.format(java.time.format.DateTimeFormatter.BASIC_ISO_DATE)
                    val bypassedKeys = settingsStore.getBypassedAlarmKeys(dateKey)
                    val activeAlarms = alarmItems.filter { !bypassedKeys.contains(it.uniqueKey) }
                    com.example.crewmate.alarm.WorkAlarmScheduler.scheduleAlarms(appContext, activeAlarms)
                }
            } catch (e: Exception) {
                android.util.Log.e("TodayWorkRepository", "Failed to schedule work alarms", e)
            }
        }

        return result
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