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
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

data class TrainCrewLookupUiState(
    val date: LocalDate = LocalDate.now(),
    val trainNumber: String = "",
    val isLoading: Boolean = false,
    val report: TrainCrewSearchReport? = null,
    val errorMessage: String? = null,
)

class TrainCrewLookupViewModel(
    private val repository: TrainCrewSearchRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(TrainCrewLookupUiState())
    val uiState: StateFlow<TrainCrewLookupUiState> = _uiState.asStateFlow()
    private var searchJob: Job? = null

    fun setDate(date: LocalDate) {
        repository.clearCache() // 날짜 변경 시 캐시 초기화
        _uiState.value = _uiState.value.copy(date = date, report = null, errorMessage = null)
    }

    fun setTrainNumber(value: String) {
        repository.clearCache() // 열차번호 변경 시 캐시 초기화
        _uiState.value = _uiState.value.copy(trainNumber = value.filterNot(Char::isWhitespace), report = null, errorMessage = null)
    }

    fun search() {
        val current = _uiState.value
        val normalized = TrainNumberNormalizer.normalizeTrainNumber(current.trainNumber)
        if (normalized.isEmpty()) {
            _uiState.value = current.copy(errorMessage = "열차번호를 입력해 주세요.", report = null)
            return
        }
        searchJob?.cancel()
        _uiState.value = current.copy(isLoading = true, errorMessage = null, report = null)
        searchJob = viewModelScope.launch {
            try {
                val dateText = current.date.format(API_DATE)
                val report = repository.search(dateText, normalized)
                _uiState.value = _uiState.value.copy(isLoading = false, report = report)
            } catch (timeout: TimeoutCancellationException) {
                _uiState.value = _uiState.value.copy(isLoading = false, errorMessage = "조회 시간이 초과되었습니다. 다시 시도해 주세요.")
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                _uiState.value = _uiState.value.copy(isLoading = false, errorMessage = error.toSafeMessage())
            }
        }
    }

    private fun Exception.toSafeMessage(): String = when (this) {
        is AllDiaDetailsFailedException -> "다이아 상세 조회에 모두 실패했습니다. 잠시 후 다시 시도해 주세요."
        is XcrewSessionExpiredException -> "Xcrew 로그인 세션이 만료되었습니다. 다시 로그인한 뒤 시도해 주세요."
        is MissingXcrewWebViewException -> "로그인 세션을 준비하지 못했습니다. 다시 시도해 주세요."
        is XcrewHttpException -> "Xcrew 근무 정보 조회에 실패했습니다. (HTTP $statusCode)"
        is InvalidXcrewJsonException, is UnexpectedXcrewResponseException -> "Xcrew 응답을 읽을 수 없습니다."
        is XcrewNetworkException -> "네트워크 연결을 확인해 주세요."
        is XcrewResponseException -> "Xcrew 근무 정보를 불러오지 못했습니다."
        else -> "승무원 정보를 조회하지 못했습니다. 다시 시도해 주세요."
    }

    private companion object {
        val API_DATE: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMdd", Locale.ROOT)
    }
}

class TrainCrewLookupViewModelFactory(
    private val repository: TrainCrewSearchRepository,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (!modelClass.isAssignableFrom(TrainCrewLookupViewModel::class.java)) {
            throw IllegalArgumentException("Unsupported ViewModel type")
        }
        return TrainCrewLookupViewModel(repository) as T
    }
}
