package com.example.crewmate.schedule

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.crewmate.data.remote.xcrew.NonOpData
import com.example.crewmate.data.remote.xcrew.XcrewWebViewXhrDataSource
import com.example.crewmate.database.AnnualLeaveEntity
import com.example.crewmate.database.CarriedOverLeaveEntity
import com.example.crewmate.database.LeaveRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter

class NonOpStatusViewModel(
    private val dataSource: XcrewWebViewXhrDataSource,
    private val leaveRepository: LeaveRepository
) : ViewModel() {

    private val _year = MutableStateFlow(LocalDate.now().year)
    val year: StateFlow<Int> = _year.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val _nonOpData = MutableStateFlow<List<NonOpData>>(emptyList())
    val nonOpData: StateFlow<List<NonOpData>> = _nonOpData.asStateFlow()

    private val _annualLeave = MutableStateFlow<AnnualLeaveEntity?>(null)
    val annualLeave: StateFlow<AnnualLeaveEntity?> = _annualLeave.asStateFlow()

    private val _carriedOverLeaves = MutableStateFlow<List<CarriedOverLeaveEntity>>(emptyList())
    val carriedOverLeaves: StateFlow<List<CarriedOverLeaveEntity>> = _carriedOverLeaves.asStateFlow()

    private var loadJob: Job? = null

    init {
        viewModelScope.launch {
            val currentYear = LocalDate.now().year
            val currentDateStr = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)
            leaveRepository.cleanupExpired(currentYear, currentDateStr)
            
            launch {
                leaveRepository.getAnnualLeave(currentYear).collect {
                    _annualLeave.value = it
                }
            }
            launch {
                leaveRepository.getCarriedOverLeaves().collect {
                    _carriedOverLeaves.value = it
                }
            }
            loadData(currentYear)
        }
    }

    fun setYear(newYear: Int) {
        _year.value = newYear
        loadData(newYear)
    }

    private fun loadData(targetYear: Int) {
        loadJob?.cancel()
        _isLoading.value = true
        _errorMessage.value = null
        loadJob = viewModelScope.launch {
            try {
                val currentYear = LocalDate.now().year
                val currentDateStr = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)
                leaveRepository.cleanupExpired(currentYear, currentDateStr)

                val data = dataSource.fetchNonOpStatus(targetYear.toString())
                _nonOpData.value = data
            } catch (e: Exception) {
                _errorMessage.value = "조회 실패: ${e.message ?: e.javaClass.simpleName}"
                _nonOpData.value = emptyList()
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun saveAnnualLeave(totalDays: Int) {
        viewModelScope.launch {
            leaveRepository.insertAnnualLeave(_year.value, totalDays)
        }
    }

    fun addCarriedOverLeave(days: Float, endDate: String) {
        viewModelScope.launch {
            leaveRepository.insertCarriedOverLeave(days, endDate)
        }
    }

    fun updateCarriedOverLeave(leave: CarriedOverLeaveEntity, days: Float, endDate: String) {
        viewModelScope.launch {
            leaveRepository.updateCarriedOverLeave(leave, days, endDate)
        }
    }

    fun deleteCarriedOverLeave(leave: CarriedOverLeaveEntity) {
        viewModelScope.launch {
            leaveRepository.deleteCarriedOverLeave(leave)
        }
    }
}

class NonOpStatusViewModelFactory(
    private val dataSource: XcrewWebViewXhrDataSource,
    private val leaveRepository: LeaveRepository
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return NonOpStatusViewModel(dataSource, leaveRepository) as T
    }
}
