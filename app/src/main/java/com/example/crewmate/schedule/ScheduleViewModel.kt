package com.example.crewmate.schedule

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.crewmate.data.remote.xcrew.InvalidXcrewJsonException
import com.example.crewmate.data.remote.xcrew.MissingXcrewCookieException
import com.example.crewmate.data.remote.xcrew.UnexpectedXcrewResponseException
import com.example.crewmate.data.remote.xcrew.XcrewHttpException
import com.example.crewmate.data.remote.xcrew.XcrewNetworkException
import com.example.crewmate.data.remote.xcrew.XcrewSessionExpiredException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

import com.example.crewmate.database.MemoEntity
import com.example.crewmate.database.MemoRepository

sealed interface ScheduleUiState {
    data class Loading(val month: String) : ScheduleUiState
    data class Success(val month: String, val schedules: List<WorkSchedule>) : ScheduleUiState
    data class Error(val month: String, val message: String) : ScheduleUiState
}

class ScheduleViewModel(
    private val repository: WorkScheduleRepository,
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
        loadJob = viewModelScope.launch {
            try {
                if (debounce) delay(MONTH_CHANGE_DEBOUNCE_MILLIS)
                val result = repository.getSchedules(month)
                if (requestId == requestGeneration) {
                    _uiState.value = ScheduleUiState.Success(result.month, result.schedules)
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                if (requestId == requestGeneration) {
                    _uiState.value = ScheduleUiState.Error(month, e.toSafeUiMessage())
                }
            }
        }
    }

    private fun Exception.toSafeUiMessage(): String = when (this) {
        is MissingXcrewCookieException -> "Xcrew 로그인 세션을 찾을 수 없습니다. 다시 로그인해 주세요."
        is XcrewSessionExpiredException -> "로그인이 만료되었을 수 있습니다. 다시 로그인해 주세요."
        is XcrewHttpException -> "근무표 요청에 실패했습니다. (HTTP $statusCode)"
        is InvalidXcrewJsonException -> "Xcrew 응답을 읽을 수 없습니다."
        is UnexpectedXcrewResponseException -> "예상하지 못한 Xcrew 응답입니다."
        is XcrewNetworkException -> "네트워크 연결을 확인해 주세요."
        else -> "근무표를 불러오지 못했습니다."
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
