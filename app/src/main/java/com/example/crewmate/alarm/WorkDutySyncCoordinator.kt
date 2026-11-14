package com.example.crewmate.alarm

import android.content.Context
import android.util.Log
import com.example.crewmate.data.remote.xcrew.AttendanceStatusDto
import com.example.crewmate.data.remote.xcrew.DiaDetailParser
import com.example.crewmate.data.remote.xcrew.DiaDetailSegment
import com.example.crewmate.database.CrewMateDatabase
import com.example.crewmate.schedule.ShiftType
import com.example.crewmate.schedule.WorkSchedule
import com.example.crewmate.schedule.determineShiftType
import com.example.crewmate.schedule.isValidActiveDia
import com.example.crewmate.schedule.toDomain
import com.example.crewmate.telegram.SubwayNowManager
import com.example.crewmate.telegram.SubwayNowRuleEvaluator
import com.example.crewmate.telegram.SubwayNowSettings
import com.example.crewmate.telegram.SubwayNowTrackTarget
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

object WorkDutySyncCoordinator {
    private const val TAG = "WorkDutySyncCoordinator"
    private val MONTH_FORMAT = DateTimeFormatter.ofPattern("yyyyMM", Locale.ROOT)

    data class SyncResult(
        val alarms: List<WorkAlarmItem> = emptyList(),
        val activeAlarms: List<WorkAlarmItem> = emptyList(),
        val telegramTargets: List<SubwayNowTrackTarget> = emptyList()
    )

    /**
     * 로컬 Room DB(dia_details 당일 키 -> 최신 동일 다이아 -> dia_census 기초자료)로부터
     * 다이아 상세 세그먼트를 3단계 폴백으로 안전하게 로드합니다.
     */
    suspend fun loadCachedSegmentsForDia(
        db: CrewMateDatabase,
        dateStr: String,
        diaNo: String,
        fallbackDateStr: String? = null
    ): List<DiaDetailSegment> {
        val trimmedDia = diaNo.trim()
        val cleanDiaNo = trimmedDia.removePrefix("~").trim()
        if (cleanDiaNo.isEmpty()) return emptyList()

        val diaParser = DiaDetailParser()
        val detailDao = db.diaDetailDao()

        val candidates = listOfNotNull(
            detailDao.getDiaDetail("$dateStr|$trimmedDia")?.detailJson,
            detailDao.getDiaDetail("$dateStr|$cleanDiaNo")?.detailJson,
            fallbackDateStr?.let { detailDao.getDiaDetail("$it|$trimmedDia")?.detailJson },
            fallbackDateStr?.let { detailDao.getDiaDetail("$it|$cleanDiaNo")?.detailJson },
            detailDao.getLatestDiaDetailByPdiaNo(cleanDiaNo)?.detailJson,
            detailDao.getLatestDiaDetailByPdiaNo(trimmedDia)?.detailJson,
            db.diaCensusDao().getCensusByPdiaNo(cleanDiaNo)?.detailJson,
            db.diaCensusDao().getCensusByPdiaNo(trimmedDia)?.detailJson
        )

        for (json in candidates) {
            if (json.isNotBlank()) {
                val parsed = runCatching { diaParser.parse(json) }.getOrNull()
                if (parsed != null && parsed.segments.isNotEmpty()) {
                    return parsed.segments
                }
            }
        }
        return emptyList()
    }

    private suspend fun loadAllKnownSchedules(
        db: CrewMateDatabase,
        anchorDate: LocalDate
    ): List<WorkSchedule> {
        val months = linkedSetOf(
            YearMonth.from(anchorDate.minusDays(1)).format(MONTH_FORMAT),
            YearMonth.from(anchorDate).format(MONTH_FORMAT),
            YearMonth.from(anchorDate.plusDays(1)).format(MONTH_FORMAT)
        )
        val entities = months.flatMap { m -> db.workScheduleDao().getSchedulesForMonth(m) }
        return entities.map { it.toDomain() }
            .distinctBy { it.workDate to (it.diaNo to it.alcSpecDvCd) }
    }

    private suspend fun loadSegmentsForWorkDateFromDb(
        db: CrewMateDatabase,
        workDate: LocalDate,
        allSchedules: List<WorkSchedule>
    ): List<DiaDetailSegment> {
        val workDateStr = workDate.format(DateTimeFormatter.BASIC_ISO_DATE)
        val daySchedules = allSchedules.filter {
            it.workDate == workDateStr && it.isValidActiveDia()
        }
        val segments = mutableListOf<DiaDetailSegment>()
        daySchedules.forEach { sched ->
            val segs = loadCachedSegmentsForDia(
                db = db,
                dateStr = workDateStr,
                diaNo = sched.diaNo.orEmpty()
            )
            segments.addAll(segs)
        }
        return segments
    }

    private suspend fun evaluateNextDayActiveAlarms(
        context: Context,
        db: CrewMateDatabase,
        currentWorkDate: LocalDate,
        allSchedules: List<WorkSchedule>,
        alarmSettings: WorkAlarmSettings,
        latchedKeys: Set<String>,
        settingsStore: WorkAlarmSettingsStore
    ): List<WorkAlarmItem> {
        val nextDate = currentWorkDate.plusDays(1)
        val nextDateStr = nextDate.format(DateTimeFormatter.BASIC_ISO_DATE)
        val nextShiftType = allSchedules.determineShiftType(nextDateStr)

        // 익일이 전일 야간근무의 연장(OVERNIGHT_DAY2)인 경우, 이미 currentWorkDate 다이아에 포함되어 있으므로 중복 평가 제외
        if (nextShiftType == ShiftType.OVERNIGHT_DAY2) {
            return emptyList()
        }

        val nextDaySchedules = allSchedules.filter {
            it.workDate == nextDateStr && it.isValidActiveDia()
        }
        if (nextDaySchedules.isEmpty()) return emptyList()

        val nextSegments = mutableListOf<DiaDetailSegment>()
        nextDaySchedules.forEach { sched ->
            nextSegments.addAll(
                loadCachedSegmentsForDia(
                    db = db,
                    dateStr = nextDateStr,
                    diaNo = sched.diaNo.orEmpty()
                )
            )
        }

        val nextAttDates = when (nextShiftType) {
            ShiftType.DAYTIME -> listOf(nextDate)
            ShiftType.OVERNIGHT_DAY1 -> listOf(nextDate, nextDate.plusDays(1))
            ShiftType.OVERNIGHT_DAY2 -> listOf(nextDate.minusDays(1), nextDate)
        }
        val nextAttStatuses = mutableListOf<AttendanceStatusDto>()
        nextAttDates.forEach { dt ->
            val dtStr = dt.format(DateTimeFormatter.BASIC_ISO_DATE)
            AttendanceSnapshotStore.load(context, dtStr)?.let { nextAttStatuses.addAll(it.statuses) }
        }

        if (nextSegments.isEmpty() && nextAttStatuses.isEmpty()) return emptyList()

        val evaluated = WorkAlarmRuleEvaluator.evaluate(
            segments = nextSegments,
            workDate = nextDate,
            settings = alarmSettings,
            attendanceStatuses = nextAttStatuses,
            checkedInKeys = latchedKeys
        )
        val bypassed = settingsStore.getBypassedAlarmKeys(nextDateStr).toMutableSet()
        bypassed.addAll(settingsStore.getBypassedAlarmKeys(nextDate.plusDays(1).format(DateTimeFormatter.BASIC_ISO_DATE)))
        return evaluated.filter { !bypassed.contains(it.uniqueKey) }
    }

    /**
     * 당일 또는 특정 근무일(workDate)의 다이아 세그먼트 및 출무현황을 기반으로:
     * 1) 정밀 알람 평가 및 예약 (바이패스 필터링 + 익일 근무 사전 예약 적용)
     * 2) 자동 오프라인 전환 윈도우 등록
     * 3) 텔레그램 '지하철 NOW' 자동 추적 스케줄 동기화
     */
    suspend fun sync(
        context: Context,
        workDate: LocalDate,
        segments: List<DiaDetailSegment>,
        attendanceStatuses: List<AttendanceStatusDto> = emptyList()
    ): SyncResult = withContext(Dispatchers.IO) {
        val db = CrewMateDatabase.getInstance(context)
        val allSchedules = runCatching { loadAllKnownSchedules(db, workDate) }.getOrDefault(emptyList())

        // 전달된 segments가 비어있다면 로컬 DB(dia_details / dia_census)에서 폴백 복원하여 기존 출근/구간 알람 증발 방지
        val effectiveSegments = if (segments.isNotEmpty()) {
            segments
        } else {
            runCatching { loadSegmentsForWorkDateFromDb(db, workDate, allSchedules) }.getOrDefault(emptyList())
        }

        if (effectiveSegments.isEmpty() && attendanceStatuses.isEmpty()) {
            return@withContext SyncResult()
        }

        // 1. 자동 오프라인 전환 등록
        val autoOfflineEnabled = AutoOfflineManager.isEnabled(context)
        if (autoOfflineEnabled && effectiveSegments.isNotEmpty()) {
            try {
                AutoOfflineManager.scheduleAutoOffline(context, effectiveSegments, workDate)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to schedule auto offline", e)
            }
        }

        // 2. 정밀 알람 평가 및 등록 (당일 + 익일 신규 근무 사전 등록)
        val settingsStore = WorkAlarmSettingsStore(context)
        val alarmSettings = settingsStore.loadSettings()
        var allAlarms = emptyList<WorkAlarmItem>()
        var activeAlarms = emptyList<WorkAlarmItem>()

        try {
            if (alarmSettings.alarmEnabled) {
                val latchedKeys = AttendanceCheckInLatch.allKeys(context)
                allAlarms = WorkAlarmRuleEvaluator.evaluate(
                    segments = effectiveSegments,
                    workDate = workDate,
                    settings = alarmSettings,
                    attendanceStatuses = attendanceStatuses,
                    checkedInKeys = latchedKeys
                )

                val dateKey = workDate.format(DateTimeFormatter.BASIC_ISO_DATE)
                val bypassedKeys = settingsStore.getBypassedAlarmKeys(dateKey).toMutableSet()
                // 만약 야간근무 등으로 익일 키가 존재할 수 있으므로 익일 키도 합산
                val nextDateKey = workDate.plusDays(1).format(DateTimeFormatter.BASIC_ISO_DATE)
                bypassedKeys.addAll(settingsStore.getBypassedAlarmKeys(nextDateKey))

                activeAlarms = allAlarms.filter { !bypassedKeys.contains(it.uniqueKey) }

                // 전날 근무가 있었던 경우에도 자정 경과 후 다음 날 아침 출근 60분 전 알람이 울릴 수 있도록 익일 알람 합산 등록
                val nextDayActiveAlarms = runCatching {
                    evaluateNextDayActiveAlarms(
                        context = context,
                        db = db,
                        currentWorkDate = workDate,
                        allSchedules = allSchedules,
                        alarmSettings = alarmSettings,
                        latchedKeys = latchedKeys,
                        settingsStore = settingsStore
                    )
                }.getOrDefault(emptyList())

                val combinedToSchedule = (activeAlarms + nextDayActiveAlarms)
                    .distinctBy { it.uniqueKey }
                    .sortedBy { it.triggerDateTime }

                WorkAlarmScheduler.scheduleAlarms(context, combinedToSchedule)
            } else {
                WorkAlarmScheduler.cancelAllAlarms(context)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to schedule work alarms", e)
        }

        // 3. 텔레그램 '지하철 NOW' 동기화
        var telegramTargets = emptyList<SubwayNowTrackTarget>()
        try {
            if (SubwayNowManager.isConfigured(context) && effectiveSegments.isNotEmpty()) {
                val config = SubwayNowSettings.getConfig(context)
                val replacements = SubwayNowSettings.getStationReplacements(context)
                val dateKey = workDate.format(DateTimeFormatter.BASIC_ISO_DATE)
                val baseTargets = SubwayNowRuleEvaluator.evaluateTargets(
                    segments = effectiveSegments,
                    workDate = workDate,
                    sendLeadMinutes = config.sendLeadMinutes,
                    trackLeadMinutes = config.trackLeadMinutes,
                    trackInterval = config.trackInterval,
                    stationReplacements = replacements
                )
                val overrides = SubwayNowSettings.getTargetOverrides(context, dateKey)
                telegramTargets = SubwayNowRuleEvaluator.applyOverrides(baseTargets, overrides)
                SubwayNowManager.syncTodayDiaSchedule(context, effectiveSegments, workDate)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to sync telegram schedules", e)
        }

        SyncResult(
            alarms = allAlarms,
            activeAlarms = activeAlarms,
            telegramTargets = telegramTargets
        )
    }

    /**
     * 단말기 재부팅(BootReceiver), 자정 갱신, 또는 앱 구동 시 로컬 Room DB에서
     * 오늘(또는 야간 2일차인 경우 전일 다이아, 당일 휴무인 경우 다음 근무일)의 근무 및 출무 데이터를 복원하여
     * 알람, 자동 오프라인, 텔레그램 스케줄을 100% 온전히 복원합니다.
     */
    suspend fun syncFromLocalDatabase(
        context: Context,
        today: LocalDate = LocalDate.now()
    ): SyncResult? = withContext(Dispatchers.IO) {
        val db = CrewMateDatabase.getInstance(context)
        val todayStr = today.format(DateTimeFormatter.BASIC_ISO_DATE)

        val domainSchedules = loadAllKnownSchedules(db, today)
        if (domainSchedules.isEmpty()) {
            Log.d(TAG, "No schedules found in local DB around $todayStr")
            return@withContext null
        }

        val shiftType = domainSchedules.determineShiftType(todayStr)

        // 만약 야간 2일차인 경우 실제 다이아 시작일은 전일(yesterday)
        var targetWorkDate = if (shiftType == ShiftType.OVERNIGHT_DAY2) today.minusDays(1) else today
        var targetWorkDateStr = targetWorkDate.format(DateTimeFormatter.BASIC_ISO_DATE)

        // 실동 다이아 찾기
        var targetDaySchedules = domainSchedules.filter {
            it.workDate == targetWorkDateStr && it.isValidActiveDia()
        }

        // 당일이 휴무/비번이라 실동 다이아가 없다면 다가오는 가장 가까운 근무일(내일 등)을 타겟으로 지정
        if (targetDaySchedules.isEmpty()) {
            val upcomingSchedules = domainSchedules.filter {
                val wd = it.workDate
                wd != null && wd > todayStr && it.isValidActiveDia()
            }
            val nearestDateStr = upcomingSchedules.minOfOrNull { it.workDate!! }
            if (nearestDateStr != null) {
                val parsedNearest = runCatching { LocalDate.parse(nearestDateStr, DateTimeFormatter.BASIC_ISO_DATE) }.getOrNull()
                if (parsedNearest != null) {
                    targetWorkDate = parsedNearest
                    targetWorkDateStr = nearestDateStr
                    targetDaySchedules = upcomingSchedules.filter { it.workDate == nearestDateStr }
                }
            }
        }

        if (targetDaySchedules.isEmpty()) {
            Log.d(TAG, "No active dia found for date $targetWorkDateStr (shift: $shiftType)")
            return@withContext null
        }

        // 세그먼트 로드 (dia_details 당일 키 -> 최신 동일 다이아 -> dia_census 3단계 폴백)
        val allSegments = mutableListOf<DiaDetailSegment>()
        targetDaySchedules.forEach { sched ->
            val segs = loadCachedSegmentsForDia(
                db = db,
                dateStr = targetWorkDateStr,
                diaNo = sched.diaNo.orEmpty(),
                fallbackDateStr = todayStr
            )
            allSegments.addAll(segs)
        }

        // 출무 현황 로드 (야간근무의 경우 당일 및 전일/익일 모두 쿼리)
        val effectiveShiftType = domainSchedules.determineShiftType(targetWorkDateStr)
        val datesToQuery = when (effectiveShiftType) {
            ShiftType.DAYTIME -> listOf(targetWorkDate)
            ShiftType.OVERNIGHT_DAY1 -> listOf(targetWorkDate, targetWorkDate.plusDays(1))
            ShiftType.OVERNIGHT_DAY2 -> listOf(targetWorkDate.minusDays(1), targetWorkDate)
        }

        val attendanceStatuses = mutableListOf<AttendanceStatusDto>()
        datesToQuery.forEach { dt ->
            val dtStr = dt.format(DateTimeFormatter.BASIC_ISO_DATE)
            val snapshot = AttendanceSnapshotStore.load(context, dtStr)
            if (snapshot != null) {
                attendanceStatuses.addAll(snapshot.statuses)
            }
        }

        Log.i(TAG, "Syncing from local DB: workDate=$targetWorkDate, shift=$effectiveShiftType, segments=${allSegments.size}, attStatuses=${attendanceStatuses.size}")
        sync(
            context = context,
            workDate = targetWorkDate,
            segments = allSegments,
            attendanceStatuses = attendanceStatuses
        )
    }
}
