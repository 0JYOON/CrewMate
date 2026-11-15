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
    val errorMessage: String? = null,
    val report: PayStatementReport? = null,
    val lastUpdatedText: String? = null
)

class PayStatementViewModel(
    private val dataSource: XcrewWebViewXhrDataSource
) : ViewModel() {

    private val _uiState = MutableStateFlow(PayStatementUiState())
    val uiState: StateFlow<PayStatementUiState> = _uiState.asStateFlow()

    init {
        loadMonth(YearMonth.now())
    }

    fun previousMonth() {
        loadMonth(_uiState.value.selectedMonth.minusMonths(1))
    }

    fun nextMonth() {
        loadMonth(_uiState.value.selectedMonth.plusMonths(1))
    }

    fun refresh() {
        loadMonth(_uiState.value.selectedMonth)
    }

    fun loadMonth(month: YearMonth) {
        _uiState.update { it.copy(selectedMonth = month, isLoading = true, errorMessage = null) }
        viewModelScope.launch {
            runCatching {
                val today = LocalDate.now()
                val queryDate = if (YearMonth.from(today) == month) {
                    today.format(DateTimeFormatter.BASIC_ISO_DATE)
                } else {
                    month.atDay(1).format(DateTimeFormatter.BASIC_ISO_DATE)
                }
                val rows = dataSource.fetchPersonalWorkSpec(queryDate)
                PayStatementParser.buildReport(rows)
            }.onSuccess { report ->
                val updatedAt = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yy-MM-dd HH:mm"))
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
    private val dataSource: XcrewWebViewXhrDataSource
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(PayStatementViewModel::class.java)) {
            return PayStatementViewModel(dataSource) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}
