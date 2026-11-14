package com.example.crewmate.schedule

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.crewmate.data.remote.xcrew.InvalidXcrewJsonException
import com.example.crewmate.data.remote.xcrew.MissingXcrewWebViewException
import com.example.crewmate.data.remote.xcrew.UnexpectedXcrewResponseException
import com.example.crewmate.data.remote.xcrew.XcrewHttpException
import com.example.crewmate.data.remote.xcrew.XcrewNetworkException
import com.example.crewmate.data.remote.xcrew.XcrewResponseException
import com.example.crewmate.data.remote.xcrew.XcrewSessionExpiredException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface TodayWorkUiState {
    data object Loading : TodayWorkUiState
    data object NoUpcomingWork : TodayWorkUiState
    data class Success(val result: TodayWorkResult) : TodayWorkUiState
    data class Error(val message: String) : TodayWorkUiState
}

class TodayWorkViewModel(
    private val repository: TodayWorkRepository,
    private val appContext: android.content.Context? = null,
) : ViewModel() {
    private val _uiState = MutableStateFlow<TodayWorkUiState>(TodayWorkUiState.Loading)
    val uiState: StateFlow<TodayWorkUiState> = _uiState.asStateFlow()

    private val _isRefreshingAttendance = MutableStateFlow(false)
    val isRefreshingAttendance: StateFlow<Boolean> = _isRefreshingAttendance.asStateFlow()

    private var loadJob: Job? = null
    private var attendanceRefreshJob: Job? = null

    init {
        load(showLoadingSpinner = true)
    }

    fun retry() = load(showLoadingSpinner = true)

    fun refreshInBackground() = load(showLoadingSpinner = false)

    fun refreshAttendanceOnly(onComplete: ((Boolean, String) -> Unit)? = null) {
        val currentState = _uiState.value as? TodayWorkUiState.Success ?: return
        if (_isRefreshingAttendance.value) return

        attendanceRefreshJob?.cancel()
        _isRefreshingAttendance.value = true
        attendanceRefreshJob = viewModelScope.launch {
            try {
                val updated = repository.refreshAttendanceStatusesOnly(currentState.result)
                _uiState.value = TodayWorkUiState.Success(updated)
                onComplete?.invoke(true, "출무현황이 최신 정보로 갱신되었습니다.")
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                onComplete?.invoke(false, error.toSafeUiMessage())
            } finally {
                _isRefreshingAttendance.value = false
            }
        }
    }

    private fun load(showLoadingSpinner: Boolean = true) {
        loadJob?.cancel()
        val hasExistingSuccess = _uiState.value is TodayWorkUiState.Success
        if (showLoadingSpinner && !hasExistingSuccess) {
            _uiState.value = TodayWorkUiState.Loading
        }
        loadJob = viewModelScope.launch {
            // 1단계: 현재 화면에 성공 데이터가 없다면 로컬 DB 캐시를 먼저 0초 즉시 조회하여 표출 (무한 로딩 원천 차단)
            if (_uiState.value !is TodayWorkUiState.Success) {
                val cachedResult = runCatching { repository.getTodayOrNextWork(localOnly = true) }.getOrNull()
                if (cachedResult != null && cachedResult.dias.any { it.detail.segments.isNotEmpty() }) {
                    _uiState.value = TodayWorkUiState.Success(cachedResult)
                    notifyWidgetUpdate()
                }
            }

            // 2단계: 원격 최신화 (세션 유효 시 근무표 + 출무현황 실시간 조회)
            try {
                val result = repository.getTodayOrNextWork(localOnly = false)
                if (result != null) {
                    _uiState.value = TodayWorkUiState.Success(result)
                    notifyWidgetUpdate()
                } else if (_uiState.value !is TodayWorkUiState.Success) {
                    _uiState.value = TodayWorkUiState.NoUpcomingWork
                }
            } catch (timeout: TimeoutCancellationException) {
                if (_uiState.value !is TodayWorkUiState.Success) {
                    _uiState.value = TodayWorkUiState.Error("근무 정보를 불러오는 시간이 초과되었습니다.")
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                if (_uiState.value !is TodayWorkUiState.Success) {
                    _uiState.value = TodayWorkUiState.Error(error.toSafeUiMessage())
                }
            }
        }
    }

    private fun notifyWidgetUpdate() {
        appContext?.let { ctx ->
            runCatching {
                val intent = android.content.Intent("kr.crewmate.app.action.UPDATE_TODAY_WIDGET").apply {
                    setPackage(ctx.packageName)
                }
                ctx.sendBroadcast(intent)
            }
        }
    }

    private fun Exception.toSafeUiMessage(): String = when (this) {
        is XcrewSessionExpiredException -> "Xcrew 로그인 세션이 만료되었습니다. 다시 로그인한 뒤 시도해 주세요."
        is MissingXcrewWebViewException -> "로그인 세션을 준비하지 못했습니다. 다시 시도해 주세요."
        is XcrewHttpException -> "승무다이아 요청에 실패했습니다. (HTTP $statusCode)"
        is InvalidXcrewJsonException, is UnexpectedXcrewResponseException -> "Xcrew 승무다이아 응답을 읽을 수 없습니다."
        is XcrewNetworkException -> "네트워크 연결을 확인해 주세요."
        is XcrewResponseException -> "Xcrew 근무 정보를 불러오지 못했습니다."
        else -> "근무 정보를 불러오지 못했습니다. 다시 시도해 주세요."
    }
}

class TodayWorkViewModelFactory(
    private val repository: TodayWorkRepository,
    private val appContext: android.content.Context? = null,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (!modelClass.isAssignableFrom(TodayWorkViewModel::class.java)) {
            throw IllegalArgumentException("Unsupported ViewModel type")
        }
        return TodayWorkViewModel(repository, appContext) as T
    }
}