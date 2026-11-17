package com.example.crewmate.labs.paystatement

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.crewmate.data.remote.xcrew.XcrewWebViewXhrDataSource
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.format.DateTimeFormatter

data class PayStatementUiState(
    val selectedMonth: YearMonth = YearMonth.now(),
    val isLoading: Boolean = false,
    val isOfflineMode: Boolean = false,
    val autoRefresh: Boolean = false,
    val errorMessage: String? = null,
    val report: PayStatementReport? = null,
    val lastUpdatedText: String? = null
)

class PayStatementViewModel(
    private val dataSource: XcrewWebViewXhrDataSource,
    private val storage: PayStatementStorage,
    private val isOfflineModeProvider: () -> Boolean
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        PayStatementUiState(
            isOfflineMode = isOfflineModeProvider(),
            autoRefresh = storage.loadAutoRefresh()
        )
    )
    val uiState: StateFlow<PayStatementUiState> = _uiState.asStateFlow()

    init {
        loadMonth(YearMonth.now(), forceRefresh = _uiState.value.autoRefresh)
    }

    fun previousMonth() {
        loadMonth(_uiState.value.selectedMonth.minusMonths(1), forceRefresh = _uiState.value.autoRefresh)
    }

    fun nextMonth() {
        loadMonth(_uiState.value.selectedMonth.plusMonths(1), forceRefresh = _uiState.value.autoRefresh)
    }

    fun setAutoRefresh(enabled: Boolean) {
        storage.saveAutoRefresh(enabled)
        _uiState.update { it.copy(autoRefresh = enabled) }
        if (enabled && !isOfflineModeProvider()) {
            loadMonth(_uiState.value.selectedMonth, forceRefresh = true)
        }
    }

    fun syncOfflineMode(isOffline: Boolean) {
        if (_uiState.value.isOfflineMode != isOffline) {
            loadMonth(_uiState.value.selectedMonth, forceRefresh = false)
        }
    }

    fun refresh() {
        if (isOfflineModeProvider()) {
            _uiState.update {
                it.copy(
                    isOfflineMode = true,
                    errorMessage = "오프라인 모드에서는 Xcrew 새로고침을 사용할 수 없습니다."
                )
            }
            return
        }
        loadMonth(_uiState.value.selectedMonth, forceRefresh = true)
    }

    fun loadMonth(month: YearMonth, forceRefresh: Boolean = false) {
        val ymKey = String.format("%04d%02d", month.year, month.monthValue)
        val offline = isOfflineModeProvider()
        val cached = storage.loadMonth(ymKey)
        val cachedReport = cached?.let { PayStatementParser.buildReport(it.rows) }

        if (offline || !forceRefresh) {
            _uiState.update {
                it.copy(
                    selectedMonth = month,
                    isLoading = false,
                    isOfflineMode = offline,
                    report = cachedReport,
                    lastUpdatedText = cached?.updatedAt,
                    errorMessage = if (offline && cachedReport == null) {
                        "오프라인 모드에서는 저장된 개인근무명세서만 조회할 수 있습니다."
                    } else null
                )
            }
            return
        }

        _uiState.update {
            it.copy(
                selectedMonth = month,
                isLoading = true,
                isOfflineMode = false,
                report = cachedReport,
                lastUpdatedText = cached?.updatedAt,
                errorMessage = null
            )
        }
        viewModelScope.launch {
            runCatching {
                val today = LocalDate.now()
                val queryDate = if (YearMonth.from(today) == month) {
                    today.format(DateTimeFormatter.BASIC_ISO_DATE)
                } else {
                    month.atDay(1).format(DateTimeFormatter.BASIC_ISO_DATE)
                }
                val rows = dataSource.fetchPersonalWorkSpec(queryDate)
                val report = PayStatementParser.buildReport(rows)
                Pair(rows, report)
            }.onSuccess { (rows, report) ->
                val updatedAt = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yy-MM-dd HH:mm"))
                storage.saveMonth(ymKey, rows, updatedAt)
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        report = report,
                        errorMessage = null,
                        lastUpdatedText = updatedAt
                    )
                }
            }.onFailure { error ->
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = error.message ?: "개인근무명세서 조회에 실패했습니다."
                    )
                }
            }
        }
    }
}

class PayStatementViewModelFactory(
    private val dataSource: XcrewWebViewXhrDataSource,
    private val storage: PayStatementStorage,
    private val isOfflineModeProvider: () -> Boolean
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(PayStatementViewModel::class.java)) {
            return PayStatementViewModel(dataSource, storage, isOfflineModeProvider) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}
