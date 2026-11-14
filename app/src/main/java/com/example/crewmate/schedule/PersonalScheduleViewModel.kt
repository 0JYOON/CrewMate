package com.example.crewmate.schedule

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.crewmate.data.remote.xcrew.InvalidXcrewJsonException
import com.example.crewmate.data.remote.xcrew.MissingXcrewCookieException
import com.example.crewmate.data.remote.xcrew.UnexpectedXcrewResponseException
import com.example.crewmate.data.remote.xcrew.XcrewHttpException
import com.example.crewmate.data.remote.xcrew.XcrewNetworkException
import com.example.crewmate.data.remote.xcrew.XcrewSessionExpiredException
import com.example.crewmate.data.remote.xcrew.XcrewWebViewSession
import com.example.crewmate.data.remote.xcrew.XcrewWebViewXhrDataSource
import com.example.crewmate.database.MemoEntity
import com.example.crewmate.database.MemoRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

class PersonalScheduleViewModel(
    private val dataSource: XcrewWebViewXhrDataSource,
    private val xcrewSession: XcrewWebViewSession,
    private val memoRepository: MemoRepository? = null,
) : ViewModel() {
    private var selectedMonth: YearMonth = YearMonth.now()
    private var requestGeneration = 0L

    private val _uiState = MutableStateFlow<ScheduleUiState>(ScheduleUiState.Loading(selectedMonth.toApiMonth()))
    val uiState: StateFlow<ScheduleUiState> = _uiState.asStateFlow()

    private val _memos = MutableStateFlow<Map<String, List<MemoEntity>>>(emptyMap())
    val memos: StateFlow<Map<String, List<MemoEntity>>> = _memos.asStateFlow()

    private var loadJob: Job? = null
    private var memoJob: Job? = null

    init {
        loadMonth(selectedMonth)
    }

    private fun observeMemos(monthValue: YearMonth) {
        memoJob?.cancel()
        val repo = memoRepository ?: return
        val yearMonthStr = monthValue.format(DateTimeFormatter.ofPattern("yyyy-MM", Locale.ROOT))
        memoJob = viewModelScope.launch {
            repo.getMemosForMonth(yearMonthStr).collect { memoList ->
                _memos.value = memoList.groupBy { it.date }
            }
        }
    }

    fun addMemo(date: String, content: String) {
        val repo = memoRepository ?: return
        if (content.isBlank()) return
        viewModelScope.launch {
            repo.insertMemo(date, content)
        }
    }

    fun updateMemo(memo: MemoEntity, newContent: String) {
        val repo = memoRepository ?: return
        if (newContent.isBlank()) return
        viewModelScope.launch {
            repo.updateMemo(memo, newContent)
        }
    }

    fun deleteMemo(memo: MemoEntity) {
        val repo = memoRepository ?: return
        viewModelScope.launch {
            repo.deleteMemo(memo)
        }
    }

    fun previousMonth() = loadMonth(selectedMonth.minusMonths(1), debounce = true)

    fun nextMonth() = loadMonth(selectedMonth.plusMonths(1), debounce = true)

    fun retrySelectedMonth() = loadMonth(selectedMonth)

    private fun loadMonth(monthValue: YearMonth, debounce: Boolean = false) {
        loadJob?.cancel()
        selectedMonth = monthValue
        observeMemos(monthValue)
        val requestId = ++requestGeneration
        val month = monthValue.toApiMonth()
        _uiState.value = ScheduleUiState.Loading(month)

        val pjtDt = if (monthValue == YearMonth.now()) {
            LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"))
        } else {
            monthValue.atDay(1).format(DateTimeFormatter.ofPattern("yyyyMMdd"))
        }
        val empNm = xcrewSession.userInfo.value?.first ?: ""

        loadJob = viewModelScope.launch {
            try {
                if (debounce) delay(MONTH_CHANGE_DEBOUNCE_MILLIS)
                val dtos = dataSource.fetchPersonalWorkSchedule(pjtDt, empNm)

                val mappedSchedules = dtos.map { dto ->
                    val rawDia = dto.pdiaNo?.trim().orEmpty()
                    val contMatch = Regex("^~\\(?(.+?)\\)?$").find(rawDia)
                    val parsedDia = contMatch?.groupValues?.get(1)?.trim() ?: rawDia.takeIf { it.isNotEmpty() }
                    var finalDia = parsedDia?.takeIf { it.isNotEmpty() && it != "null" }
                    val nonActive = dto.nonAtulClsfCdNm?.trim()
                    if (nonActive?.isNotEmpty() == true && (finalDia == null || finalDia == "S")) {
                        finalDia = nonActive
                    }

                    WorkSchedule(
                        workDate = dto.pjtDt?.trim(),
                        diaNo = finalDia,
                        trainNo = dto.repTrn1No?.trim(),
                        trainNo2 = dto.repTrn2No?.trim(),
                        startTime = dto.gwkTm?.trim(),
                        endTime = dto.loiwTm?.trim(),
                        nonActiveType = dto.nonAtulClsfCdNm?.trim(),
                        isContinuation = contMatch != null,
                        isHoliday = dto.hldyYn?.trim()?.uppercase() == "Y"
                    )
                }

                if (mappedSchedules.isEmpty()) {
                    if (requestId == requestGeneration) {
                        _uiState.value = ScheduleUiState.Error(month, "데이터가 없습니다.")
                    }
                    return@launch
                }

                if (requestId == requestGeneration) {
                    _uiState.value = ScheduleUiState.Success(month, mappedSchedules)
                }
            } catch (_: TimeoutCancellationException) {
                if (requestId == requestGeneration) {
                    _uiState.value = ScheduleUiState.Error(month, "근무표 요청 시간이 초과되었습니다.")
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                if (requestId == requestGeneration) {
                    _uiState.value = ScheduleUiState.Error(month, error.toSafeUiMessage())
                }
            }
        }
    }

    private fun Exception.toSafeUiMessage(): String = when (this) {
        is MissingXcrewCookieException -> "Xcrew 로그인 세션을 찾을 수 없습니다. 다시 로그인해 주세요."
        is XcrewSessionExpiredException -> "로그인이 만료되었을 수 있습니다. 다시 로그인해 주세요."
        is XcrewHttpException -> "근무표 요청에 실패했습니다. (HTTP ${this.statusCode})"
        is InvalidXcrewJsonException -> "Xcrew 응답을 읽을 수 없습니다."
        is UnexpectedXcrewResponseException -> this.message ?: "예상하지 못한 Xcrew 응답입니다."
        is XcrewNetworkException -> "네트워크 연결을 확인해 주세요."
        is RuntimeException -> this.message ?: "런타임 에러: ${this.javaClass.simpleName}"
        else -> "근무표를 불러오지 못했습니다. (${this.message})"
    }

    private companion object {
        val MONTH_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMM", Locale.ROOT)
        const val MONTH_CHANGE_DEBOUNCE_MILLIS = 350L
    }

    private fun YearMonth.toApiMonth(): String = format(MONTH_FORMAT)

    fun setMonth(yearMonth: YearMonth) {
        loadMonth(yearMonth)
    }
}

class PersonalScheduleViewModelFactory(
    private val dataSource: XcrewWebViewXhrDataSource,
    private val xcrewSession: XcrewWebViewSession,
    private val memoRepository: MemoRepository
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return PersonalScheduleViewModel(dataSource, xcrewSession, memoRepository) as T
    }
}
