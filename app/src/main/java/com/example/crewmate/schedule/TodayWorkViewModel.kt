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
) : ViewModel() {
    private val _uiState = MutableStateFlow<TodayWorkUiState>(TodayWorkUiState.Loading)
    val uiState: StateFlow<TodayWorkUiState> = _uiState.asStateFlow()
    private var loadJob: Job? = null

    init {
        load()
    }

    fun retry() = load()

    private fun load() {
        loadJob?.cancel()
        _uiState.value = TodayWorkUiState.Loading
        loadJob = viewModelScope.launch {
            try {
                val result = repository.getTodayOrNextWork()
                _uiState.value = result?.let(TodayWorkUiState::Success)
                    ?: TodayWorkUiState.NoUpcomingWork
            } catch (timeout: TimeoutCancellationException) {
                _uiState.value = TodayWorkUiState.Error("근무 정보를 불러오는 시간이 초과되었습니다.")
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                _uiState.value = TodayWorkUiState.Error(error.toSafeUiMessage())
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
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (!modelClass.isAssignableFrom(TodayWorkViewModel::class.java)) {
            throw IllegalArgumentException("Unsupported ViewModel type")
        }
        return TodayWorkViewModel(repository) as T
    }
}