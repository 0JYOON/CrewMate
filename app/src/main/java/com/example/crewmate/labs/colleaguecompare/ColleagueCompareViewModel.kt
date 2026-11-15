package com.example.crewmate.labs.colleaguecompare

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.crewmate.data.remote.xcrew.PersonalWorkScheduleDto
import com.example.crewmate.data.remote.xcrew.XcrewWebViewXhrDataSource
import com.example.crewmate.database.CrewAssignmentDao
import com.example.crewmate.database.WorkScheduleDao
import com.example.crewmate.schedule.WorkScheduleParser
import com.example.crewmate.schedule.WorkScheduleRepositoryImpl
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.UUID

data class ColleagueCompareUiState(
    val selectedMonth: YearMonth = YearMonth.now(),
    val myCells: Map<String, CompareDayCell> = emptyMap(),
    val people: List<ComparePerson> = emptyList(),
    val displayOptions: CompareDisplayOptions = CompareDisplayOptions(),
    val isLoading: Boolean = false,
    val isSearchingColleagues: Boolean = false,
    val searchResults: List<String> = emptyList(),
    val lastRefreshedAt: String? = null,
    val statusMessage: String? = null,
    val errorMessage: String? = null
)

class ColleagueCompareViewModel(
    private val dataSource: XcrewWebViewXhrDataSource,
    private val workScheduleDao: WorkScheduleDao,
    private val crewAssignmentDao: CrewAssignmentDao,
    private val storage: ColleagueCompareStorage,
    private val isOfflineModeProvider: () -> Boolean
) : ViewModel() {

    private val scheduleRepo = WorkScheduleRepositoryImpl(
        dataSource = dataSource,
        parser = WorkScheduleParser(),
        workScheduleDao = workScheduleDao,
        isOfflineModeProvider = isOfflineModeProvider
    )

    private val _uiState = MutableStateFlow(
        ColleagueCompareUiState(
            people = storage.loadPeople(),
            displayOptions = storage.loadDisplayOptions(),
            lastRefreshedAt = storage.loadLastRefreshedAt()
        )
    )
    val uiState: StateFlow<ColleagueCompareUiState> = _uiState.asStateFlow()

    init {
        loadMyScheduleForMonth(_uiState.value.selectedMonth, forceRefresh = false)
    }

    fun yearMonthKey(ym: YearMonth = _uiState.value.selectedMonth): String =
        String.format("%04d%02d", ym.year, ym.monthValue)

    fun previousMonth() {
        val prev = _uiState.value.selectedMonth.minusMonths(1)
        _uiState.update { it.copy(selectedMonth = prev, errorMessage = null, statusMessage = null) }
        loadMyScheduleForMonth(prev, forceRefresh = false)
    }

    fun nextMonth() {
        val next = _uiState.value.selectedMonth.plusMonths(1)
        _uiState.update { it.copy(selectedMonth = next, errorMessage = null, statusMessage = null) }
        loadMyScheduleForMonth(next, forceRefresh = false)
    }

    fun setShowCommuteTimes(show: Boolean) {
        val opts = _uiState.value.displayOptions.copy(showCommuteTimes = show)
        storage.saveDisplayOptions(opts)
        _uiState.update { it.copy(displayOptions = opts) }
    }

    fun setTransposeMatrix(transpose: Boolean) {
        val opts = _uiState.value.displayOptions.copy(transposeMatrix = transpose)
        storage.saveDisplayOptions(opts)
        _uiState.update { it.copy(displayOptions = opts) }
    }

    fun clearSearchResults() {
        _uiState.update { it.copy(searchResults = emptyList(), isSearchingColleagues = false) }
    }

    fun loadMyScheduleForMonth(month: YearMonth, forceRefresh: Boolean) {
        val ymKey = yearMonthKey(month) // yyyyMM
        viewModelScope.launch {
            runCatching {
                scheduleRepo.getSchedules(month = ymKey, forceRefresh = forceRefresh).schedules
            }.onSuccess { list ->
                val map = buildMap {
                    list.forEach { sched ->
                        val rawDigits = sched.workDate?.filter { it.isDigit() }.orEmpty()
                        val dateKey = if (rawDigits.length == 8) {
                            "${rawDigits.substring(0, 4)}-${rawDigits.substring(4, 6)}-${rawDigits.substring(6, 8)}"
                        } else {
                            sched.workDate?.trim().orEmpty()
                        }
                        if (dateKey.isNotEmpty()) {
                            val dia = buildString {
                                if (sched.isContinuation) append("~")
                                append(sched.diaNo?.trim().orEmpty().ifEmpty { sched.nonActiveType?.trim().orEmpty() })
                            }.ifBlank { "-" }
                            put(
                                dateKey,
                                CompareDayCell(
                                    date = dateKey,
                                    diaNo = dia,
                                    startTime = sched.startTime.orEmpty(),
                                    endTime = sched.endTime.orEmpty()
                                )
                            )
                        }
                    }
                }
                _uiState.update { it.copy(myCells = map) }
            }
        }
    }

    fun searchColleagues(keyword: String) {
        val trimmed = keyword.trim()
        if (trimmed.isEmpty()) {
            _uiState.update { it.copy(searchResults = emptyList()) }
            return
        }
        val month = _uiState.value.selectedMonth
        _uiState.update { it.copy(isSearchingColleagues = true, errorMessage = null) }
        viewModelScope.launch {
            runCatching {
                val names = linkedSetOf<String>()
                // 1. 로컬 소속 현황 캐시에서 해당 월의 승무원 이름 검색
                for (day in 1..month.lengthOfMonth()) {
                    val ymd = String.format("%04d%02d%02d", month.year, month.monthValue, day)
                    val entities = crewAssignmentDao.getAssignmentsForDate(ymd)
                    entities.forEach { entity ->
                        entity.crewMembers.forEach { member ->
                            val clean = member.trim()
                            if (clean.contains(trimmed, ignoreCase = true)) {
                                names.add(clean)
                            }
                        }
                    }
                }
                // 2. 온라인이면 당월 복수 기준일(오늘/1일/10일/20일)의 소속현황을 병행 조회해 휴무자 누락 방지
                if (!isOfflineModeProvider()) {
                    val today = LocalDate.now()
                    val probeDates = linkedSetOf<String>().apply {
                        if (YearMonth.from(today) == month) {
                            add(today.format(DateTimeFormatter.BASIC_ISO_DATE))
                        }
                        add(month.atDay(1).format(DateTimeFormatter.BASIC_ISO_DATE))
                        add(month.atDay(10).format(DateTimeFormatter.BASIC_ISO_DATE))
                        add(month.atDay(20.coerceAtMost(month.lengthOfMonth())).format(DateTimeFormatter.BASIC_ISO_DATE))
                    }.take(3)
                    probeDates.forEach { probeDate ->
                        runCatching { dataSource.fetchCrewWorkStatus(probeDate) }.getOrNull()?.forEach { assignment ->
                            assignment.crewMembers.forEach { member ->
                                val clean = member.trim()
                                if (clean.contains(trimmed, ignoreCase = true)) {
                                    names.add(clean)
                                }
                            }
                        }
                    }
                }
                names.sorted()
            }.onSuccess { found ->
                _uiState.update { it.copy(isSearchingColleagues = false, searchResults = found) }
            }.onFailure { err ->
                _uiState.update {
                    it.copy(
                        isSearchingColleagues = false,
                        errorMessage = err.message ?: "승무원 검색 중 오류가 발생했습니다."
                    )
                }
            }
        }
    }

    fun addRealColleague(empNm: String) {
        val cleanName = empNm.trim()
        if (cleanName.isEmpty()) return
        val month = _uiState.value.selectedMonth
        val ymKey = yearMonthKey(month)
        val queryDate = "${ymKey}01"

        _uiState.update { it.copy(isLoading = true, errorMessage = null, statusMessage = null) }
        viewModelScope.launch {
            runCatching {
                val dtos = if (!isOfflineModeProvider()) {
                    runCatching { dataSource.fetchPersonalWorkSchedule(queryDate, cleanName) }.getOrDefault(emptyList())
                } else emptyList()
                val dtoBuckets = dtosToGroupedBuckets(dtos)
                val crewBuckets = collectCrewDaoBucketsForMonth(month, cleanName)
                Pair(dtoBuckets, crewBuckets)
            }.onSuccess { (dtoBuckets, crewBuckets) ->
                val maxPerDay = maxOf(
                    dtoBuckets.values.maxOfOrNull { it.size } ?: 0,
                    crewBuckets.values.maxOfOrNull { it.size } ?: 0
                )
                val current = _uiState.value.people.toMutableList()
                if (maxPerDay >= 2) {
                    // 동명이인 감지: 인원수(maxPerDay)만큼 가상 인물로 분할 추가
                    val mergedByDate = linkedMapOf<String, List<CompareDayCell>>()
                    val allDates = (dtoBuckets.keys + crewBuckets.keys).toSortedSet()
                    allDates.forEach { iso ->
                        val fromDto = dtoBuckets[iso].orEmpty()
                        val fromCrew = crewBuckets[iso].orEmpty()
                        mergedByDate[iso] = if (fromDto.size >= fromCrew.size) fromDto else fromCrew
                    }
                    val createdNames = mutableListOf<String>()
                    for (idx in 0 until maxPerDay) {
                        val virtualName = "$cleanName (${idx + 1})"
                        createdNames.add(virtualName)
                        val personCells = linkedMapOf<String, CompareDayCell>()
                        mergedByDate.forEach { (iso, list) ->
                            list.getOrNull(idx)?.let { cell ->
                                personCells[iso] = cell
                            }
                        }
                        val existingIdx = current.indexOfFirst {
                            it.type == CompareEntryType.VIRTUAL_PERSON && it.name == virtualName
                        }
                        if (existingIdx >= 0) {
                            val existing = current[existingIdx]
                            val mergedMonths = existing.cellsByMonth.toMutableMap().apply { put(ymKey, personCells) }
                            current[existingIdx] = existing.copy(cellsByMonth = mergedMonths)
                        } else {
                            current.add(
                                ComparePerson(
                                    id = "vir_${UUID.randomUUID()}",
                                    name = virtualName,
                                    type = CompareEntryType.VIRTUAL_PERSON,
                                    cellsByMonth = mapOf(ymKey to personCells)
                                )
                            )
                        }
                    }
                    storage.savePeople(current)
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            people = current,
                            statusMessage = "동명이인 ${maxPerDay}명이 확인되어 가상 인물(${createdNames.joinToString(", ")})로 추가했습니다."
                        )
                    }
                } else {
                    val dayMap = linkedMapOf<String, CompareDayCell>()
                    crewBuckets.forEach { (iso, list) -> list.firstOrNull()?.let { dayMap[iso] = it } }
                    dtoBuckets.forEach { (iso, list) -> list.firstOrNull()?.let { dayMap[iso] = it } }
                    val existingIdx = current.indexOfFirst { it.type == CompareEntryType.REAL_COLLEAGUE && it.name == cleanName }
                    if (existingIdx >= 0) {
                        val existing = current[existingIdx]
                        val mergedMonths = existing.cellsByMonth.toMutableMap().apply { put(ymKey, dayMap) }
                        current[existingIdx] = existing.copy(cellsByMonth = mergedMonths)
                    } else {
                        current.add(
                            ComparePerson(
                                id = "col_${UUID.randomUUID()}",
                                name = cleanName,
                                type = CompareEntryType.REAL_COLLEAGUE,
                                cellsByMonth = mapOf(ymKey to dayMap)
                            )
                        )
                    }
                    storage.savePeople(current)
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            people = current,
                            statusMessage = "'${cleanName}' 님의 ${month.monthValue}월 근무표를 불러와 저장했습니다."
                        )
                    }
                }
            }.onFailure { err ->
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = "'$cleanName' 조회 실패: ${err.message ?: "Xcrew 응답 오류"}"
                    )
                }
            }
        }
    }

    fun addVirtualPerson(name: String, sequence: List<String>, anchorDateIso: String) {
        val cleanName = name.trim().ifBlank { "가상 인물" }
        val cleanSeq = sequence.map { it.trim() }.filter { it.isNotEmpty() }
        if (cleanSeq.isEmpty()) return

        val updated = _uiState.value.people.toMutableList().apply {
            add(
                ComparePerson(
                    id = "vir_${UUID.randomUUID()}",
                    name = cleanName,
                    type = CompareEntryType.VIRTUAL_PERSON,
                    patternSequence = cleanSeq,
                    patternAnchorDate = anchorDateIso
                )
            )
        }
        storage.savePeople(updated)
        _uiState.update {
            it.copy(
                people = updated,
                statusMessage = "가상 인물 '${cleanName}' (${cleanSeq.size}일 패턴) 추가 완료"
            )
        }
    }

    fun updateVirtualPerson(
        personId: String,
        name: String,
        sequence: List<String>,
        anchorDateIso: String
    ) {
        val cleanName = name.trim().ifBlank { "가상 인물" }
        val cleanSeq = sequence.map { it.trim() }.filter { it.isNotEmpty() }
        val updated = _uiState.value.people.map { person ->
            if (person.id != personId || person.type != CompareEntryType.VIRTUAL_PERSON) {
                person
            } else {
                person.copy(
                    name = cleanName,
                    patternSequence = cleanSeq,
                    patternAnchorDate = anchorDateIso.trim().ifBlank { null }
                )
            }
        }
        storage.savePeople(updated)
        _uiState.update {
            it.copy(
                people = updated,
                statusMessage = "가상 인물 '${cleanName}' 설정이 수정되었습니다."
            )
        }
    }

    fun updateCellOverride(
        personId: String,
        dateIso: String,
        diaNo: String,
        startTime: String,
        endTime: String
    ) {
        val cleanDia = diaNo.trim()
        val updated = _uiState.value.people.map { person ->
            if (person.id != personId || person.type == CompareEntryType.MY_SELF) {
                person
            } else {
                val nextOverrides = person.manualOverrides.toMutableMap()
                if (cleanDia.isEmpty()) {
                    nextOverrides.remove(dateIso)
                } else {
                    nextOverrides[dateIso] = CompareDayCell(
                        date = dateIso,
                        diaNo = cleanDia,
                        startTime = startTime.trim(),
                        endTime = endTime.trim(),
                        isOverridden = true
                    )
                }
                person.copy(manualOverrides = nextOverrides)
            }
        }
        storage.savePeople(updated)
        _uiState.update { it.copy(people = updated) }
    }

    fun removePerson(personId: String) {
        val updated = _uiState.value.people.filterNot { it.id == personId }
        storage.savePeople(updated)
        _uiState.update { it.copy(people = updated) }
    }

    fun refreshAllOnline() {
        if (isOfflineModeProvider()) {
            _uiState.update { it.copy(errorMessage = "오프라인 모드에서는 Xcrew 새로고침을 실행할 수 없습니다.") }
            return
        }
        val month = _uiState.value.selectedMonth
        val ymKey = yearMonthKey(month)
        val queryDate = "${ymKey}01"

        _uiState.update { it.copy(isLoading = true, errorMessage = null, statusMessage = null) }
        loadMyScheduleForMonth(month, forceRefresh = true)

        viewModelScope.launch {
            var successCount = 0
            var failCount = 0
            val updated = _uiState.value.people.map { person ->
                if (person.type != CompareEntryType.REAL_COLLEAGUE) {
                    person
                } else {
                    val res = runCatching {
                        val dtos = dataSource.fetchPersonalWorkSchedule(queryDate, person.name)
                        dtosToCellMap(dtos)
                    }
                    res.onSuccess { dayMap ->
                        successCount++
                    }.onFailure {
                        failCount++
                    }
                    val map = res.getOrNull()
                    if (map != null) {
                        val merged = person.cellsByMonth.toMutableMap().apply { put(ymKey, map) }
                        person.copy(cellsByMonth = merged)
                    } else person
                }
            }
            val refreshedAt = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yy-MM-dd HH:mm"))
            storage.savePeople(updated)
            storage.saveLastRefreshedAt(refreshedAt)
            _uiState.update {
                it.copy(
                    isLoading = false,
                    people = updated,
                    lastRefreshedAt = refreshedAt,
                    statusMessage = "새로고침 완료 (동료 ${successCount}명 갱신${if (failCount > 0) ", ${failCount}명 실패" else ""})"
                )
            }
        }
    }

    fun clearMessages() {
        _uiState.update { it.copy(statusMessage = null, errorMessage = null) }
    }

    private suspend fun collectCrewDaoBucketsForMonth(
        month: YearMonth,
        cleanName: String
    ): Map<String, List<CompareDayCell>> {
        val map = linkedMapOf<String, MutableList<CompareDayCell>>()
        for (day in 1..month.lengthOfMonth()) {
            val ymd = String.format("%04d%02d%02d", month.year, month.monthValue, day)
            val iso = String.format("%04d-%02d-%02d", month.year, month.monthValue, day)
            val entities = crewAssignmentDao.getAssignmentsForDate(ymd)
            entities.forEach { entity ->
                if (entity.crewMembers.any { it.trim() == cleanName }) {
                    val dia = entity.pdiaNo.trim().ifEmpty { "-" }
                    map.getOrPut(iso) { mutableListOf() }.add(
                        CompareDayCell(
                            date = iso,
                            diaNo = dia,
                            startTime = entity.gwkTm.orEmpty(),
                            endTime = entity.loiwTm.orEmpty()
                        )
                    )
                }
            }
        }
        return map
    }

    private fun dtosToCellMap(dtos: List<PersonalWorkScheduleDto>): Map<String, CompareDayCell> {
        return dtosToGroupedBuckets(dtos).mapValuesNotNull { (_, list) -> list.firstOrNull() }
    }

    companion object {
        fun dtosToGroupedBuckets(dtos: List<PersonalWorkScheduleDto>): Map<String, List<CompareDayCell>> {
            val map = linkedMapOf<String, MutableList<CompareDayCell>>()
            dtos.forEach { dto ->
                val rawDate = dto.pjtDt?.filter { it.isDigit() }.orEmpty()
                if (rawDate.length == 8) {
                    val iso = "${rawDate.substring(0, 4)}-${rawDate.substring(4, 6)}-${rawDate.substring(6, 8)}"
                    val dia = dto.pdiaNo?.trim()?.takeIf { it.isNotEmpty() }
                        ?: dto.nonAtulClsfCdNm?.trim()?.takeIf { it.isNotEmpty() }
                        ?: "-"
                    map.getOrPut(iso) { mutableListOf() }.add(
                        CompareDayCell(
                            date = iso,
                            diaNo = dia,
                            startTime = dto.gwkTm?.trim().orEmpty(),
                            endTime = dto.loiwTm?.trim().orEmpty()
                        )
                    )
                }
            }
            return map
        }
    }
}

private inline fun <K, V, R : Any> Map<K, V>.mapValuesNotNull(transform: (Map.Entry<K, V>) -> R?): Map<K, R> {
    val result = linkedMapOf<K, R>()
    for (entry in this) {
        val v = transform(entry)
        if (v != null) result[entry.key] = v
    }
    return result
}

class ColleagueCompareViewModelFactory(
    private val dataSource: XcrewWebViewXhrDataSource,
    private val workScheduleDao: WorkScheduleDao,
    private val crewAssignmentDao: CrewAssignmentDao,
    private val storage: ColleagueCompareStorage,
    private val isOfflineModeProvider: () -> Boolean
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(ColleagueCompareViewModel::class.java)) {
            return ColleagueCompareViewModel(
                dataSource = dataSource,
                workScheduleDao = workScheduleDao,
                crewAssignmentDao = crewAssignmentDao,
                storage = storage,
                isOfflineModeProvider = isOfflineModeProvider
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}
