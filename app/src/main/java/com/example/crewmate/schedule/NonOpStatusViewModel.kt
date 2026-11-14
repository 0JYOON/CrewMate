package com.example.crewmate.schedule

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.crewmate.data.remote.xcrew.NonOpData
import com.example.crewmate.data.remote.xcrew.XcrewWebViewXhrDataSource
import com.example.crewmate.database.AnnualLeaveEntity
import com.example.crewmate.database.CarriedOverLeaveEntity
import com.example.crewmate.database.CrewMateDatabase
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
    private val leaveRepository: LeaveRepository,
    private val workScheduleRepository: WorkScheduleRepository,
    private val appContext: Context
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

    private val _holidayReport = MutableStateFlow<HolidayWorkReport?>(null)
    val holidayReport: StateFlow<HolidayWorkReport?> = _holidayReport.asStateFlow()

    private val _isHolidayLoading = MutableStateFlow(false)
    val isHolidayLoading: StateFlow<Boolean> = _isHolidayLoading.asStateFlow()

    private var loadJob: Job? = null
    private var holidayJob: Job? = null

    init {
        val currentYear = LocalDate.now().year
        _holidayReport.value = HolidayWorkManager.getSavedReport(appContext, currentYear)

        viewModelScope.launch {
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
        _holidayReport.value = HolidayWorkManager.getSavedReport(appContext, newYear)
        loadData(newYear)
    }

    fun queryHolidayWork() {
        val targetYear = _year.value
        holidayJob?.cancel()
        holidayJob = viewModelScope.launch {
            _isHolidayLoading.value = true
            try {
                val db = CrewMateDatabase.getInstance(appContext)
                val monthSchedulesMap = mutableMapOf<String, List<WorkSchedule>>()

                // 1. 로컬 Room DB에 이미 캐시된 해당 연도 전체 근무표 우선 수집 (월별 복수 행 완전 보존)
                runCatching {
                    val cachedList = db.workScheduleDao().getSchedulesForYear("${targetYear}%")
                    val groupedByYm = cachedList.groupBy { it.yearMonth.filter { c -> c.isDigit() } }
                    for ((ym, entities) in groupedByYm) {
                        monthSchedulesMap[ym] = entities.map { entity ->
                            WorkSchedule(
                                workDate = entity.date,
                                diaNo = entity.diaNo,
                                trainNo = entity.trainNo,
                                trainNo2 = entity.trainNo2,
                                startTime = entity.startTime,
                                endTime = entity.endTime,
                                nonActiveType = entity.nonActiveType,
                                isContinuation = entity.isContinuation,
                                isHoliday = entity.isHoliday,
                                alcSpecDvCd = entity.alcSpecDvCd,
                                hldyYn = entity.hldyYn,
                                isChanged = entity.isChanged
                            )
                        }
                    }
                    Log.i("HolidayWork", "연도 $targetYear Room DB 캐시 수집: ${monthSchedulesMap.keys} (${cachedList.size}건)")
                }.onFailure {
                    Log.w("HolidayWork", "로컬 캐시 수집 실패", it)
                }

                // 2. 당해 연도 조회 시 당월(또는 당월+1)까지만 원격 조회하여 미발표 월 타임아웃 지연 차단
                val currentYear = LocalDate.now().year
                val currentMonth = LocalDate.now().monthValue
                val maxMonth = if (targetYear == currentYear) {
                    minOf(12, currentMonth + 1)
                } else if (targetYear < currentYear) {
                    12
                } else {
                    1
                }

                // 월별 순회: DB에 없거나 최근 월(갱신 필요)인 경우 원격 조회 보완
                for (m in 1..maxMonth) {
                    val mStr = "${targetYear}${m.toString().padStart(2, '0')}"
                    val hasCached = monthSchedulesMap[mStr]?.isNotEmpty() == true
                    if (!hasCached || (targetYear == currentYear && m >= currentMonth)) {
                        runCatching {
                            val monthData = workScheduleRepository.getSchedules(
                                month = mStr,
                                forceRefresh = targetYear == currentYear && m >= currentMonth
                            )
                            if (monthData.schedules.isNotEmpty()) {
                                monthSchedulesMap[mStr] = monthData.schedules
                                Log.i("HolidayWork", "월 $mStr 원격 수집 성공: ${monthData.schedules.size}건")
                            }
                        }.onFailure {
                            Log.w("HolidayWork", "월 $mStr 원격 수집 실패 또는 데이터 없음", it)
                        }
                    }
                }

                val allSchedules = monthSchedulesMap.values.flatten()
                Log.i("HolidayWork", "연도 $targetYear 총 수집 스케줄: ${allSchedules.size}건")
                val report = HolidayWorkManager.calculateHolidayWork(targetYear, allSchedules)
                HolidayWorkManager.saveReport(appContext, report)
                _holidayReport.value = report
                Log.i("HolidayWork", "연도 $targetYear 휴일근무 판정 완료: 총 ${report.totalCount}건 (주간 ${report.dayCount}, 야간 ${report.nightCount}, 분석 일수: ${report.examinedDaysCount})")
            } catch (e: Exception) {
                Log.e("HolidayWork", "휴일근무 현황 조회 실패", e)
            } finally {
                _isHolidayLoading.value = false
            }
        }
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
    private val leaveRepository: LeaveRepository,
    private val workScheduleRepository: WorkScheduleRepository,
    private val appContext: Context
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return NonOpStatusViewModel(dataSource, leaveRepository, workScheduleRepository, appContext) as T
    }
}
