package com.example.crewmate.schedule

import android.util.Log
import com.example.crewmate.data.remote.xcrew.CrewWorkStatusDataSource
import com.example.crewmate.data.remote.xcrew.DiaDetailDataSource
import com.example.crewmate.database.DiaCensusDao
import com.example.crewmate.database.DiaCensusEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

sealed class CensusProgressState {
    object Idle : CensusProgressState()
    data class Running(val current: Int, val total: Int, val message: String) : CensusProgressState()
    data class Finished(val totalCount: Int, val message: String) : CensusProgressState()
    data class Error(val message: String) : CensusProgressState()
}

class DiaCensusManager(
    private val crewWorkStatusDataSource: CrewWorkStatusDataSource,
    private val diaDetailDataSource: DiaDetailDataSource,
    private val diaCensusDao: DiaCensusDao,
    private val isOfflineProvider: () -> Boolean = { false }
) {
    private val appScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val _progressState = MutableStateFlow<CensusProgressState>(CensusProgressState.Idle)
    val progressState: StateFlow<CensusProgressState> = _progressState.asStateFlow()

    fun startBackgroundCensus(
        startDate: LocalDate = LocalDate.now().withDayOfMonth(1),
        daysCount: Int = LocalDate.now().lengthOfMonth()
    ) {
        if (isOfflineProvider()) {
            _progressState.value = CensusProgressState.Error("오프라인 모드에서는 기초자료 조사를 실행할 수 없습니다.")
            return
        }
        if (_progressState.value is CensusProgressState.Running) {
            runCatching { Log.i(TAG, "Census is already running in background.") }
            return
        }

        appScope.launch {
            try {
                if (isOfflineProvider()) {
                    _progressState.value = CensusProgressState.Error("오프라인 모드에서는 기초자료 조사를 실행할 수 없습니다.")
                    return@launch
                }

                // Requirement: Clear old census data on every new run
                try {
                    diaCensusDao.clearAllCensus()
                } catch (e: Exception) {
                    runCatching { Log.w(TAG, "Failed to clear old census records", e) }
                }

                val dateFormatter = DateTimeFormatter.ofPattern("yyyyMMdd", Locale.ROOT)
                val timestampFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss", Locale.ROOT)
                val updatedAt = LocalDateTime.now().format(timestampFormatter)

                runCatching { Log.i(TAG, "Starting $daysCount-day DIA census from $startDate in background appScope") }
                _progressState.value = CensusProgressState.Running(0, daysCount, "1단계: ${daysCount}일간의 소속 승무원 수집 준비 중...")

                // Step 1: Collect unique (pjtDt, pdiaNo) pairs from crew work status
                val targetPairs = mutableListOf<Pair<String, String>>()
                val dates = (0 until daysCount).map { startDate.plusDays(it.toLong()) }
                val seenDiaNumbers = mutableSetOf<String>()

                for ((index, date) in dates.withIndex()) {
                    if (isOfflineProvider()) {
                        _progressState.value = CensusProgressState.Error("오프라인 모드로 전환되어 기초자료 조사가 중단되었습니다.")
                        return@launch
                    }
                    val dateStr = date.format(dateFormatter)
                    _progressState.value = CensusProgressState.Running(index + 1, daysCount, "승무 현황 다이아 수집 중... (${dateStr})")
                    try {
                        val assignments = crewWorkStatusDataSource.fetchCrewWorkStatus(dateStr)
                        // Requirement: Filter out DIAs with no representative train number
                        val validAssignments = assignments.filter { 
                            !it.repTrn1No.isNullOrBlank() || !it.repTrn2No.isNullOrBlank() 
                        }
                        
                        // Requirement: Skip duplicate DIA numbers
                        val uniqueDias = validAssignments.map { it.pdiaNo.trim() }
                            .filter { it.isNotEmpty() && it != "S" && it.matches(Regex("[A-Za-z0-9]+")) }
                            .distinct()
                            
                        uniqueDias.forEach { pdiaNo ->
                            if (!seenDiaNumbers.contains(pdiaNo)) {
                                seenDiaNumbers.add(pdiaNo)
                                targetPairs.add(dateStr to pdiaNo)
                            }
                        }
                    } catch (e: Exception) {
                        runCatching { Log.w(TAG, "Failed to fetch crew work status for $dateStr", e) }
                    }
                    delay(100) // gentle rate-limit delay
                }

                // Deduplicate target pairs purely by pdiaNo (single Primary Key)
                val distinctPairs = targetPairs.distinctBy { it.second }
                val totalPairs = distinctPairs.size
                runCatching { Log.i(TAG, "Collected $totalPairs total distinct DIA candidates across $daysCount days") }

                if (totalPairs == 0) {
                    _progressState.value = CensusProgressState.Finished(0, "수집할 새로운 다이아 목록이 없습니다.")
                    return@launch
                }

                // Step 2: Fetch DiaDetail for each unique pdiaNo and save to Room DB
                var processedCount = 0

                for ((_, pdiaNo) in distinctPairs) {
                    if (isOfflineProvider()) {
                        _progressState.value = CensusProgressState.Error("오프라인 모드로 전환되어 기초자료 조사가 중단되었습니다.")
                        return@launch
                    }
                    processedCount++

                    // Check if already in Room DB
                    val existingDb = diaCensusDao.getCensusByPdiaNo(pdiaNo)
                    if (existingDb != null && existingDb.detailJson.isNotBlank()) {
                        _progressState.value = CensusProgressState.Running(
                            processedCount, 
                            totalPairs, 
                            "기존 DB 캐시 연결 중... [$pdiaNo DIA]"
                        )
                        continue
                    }

                    _progressState.value = CensusProgressState.Running(
                        processedCount, 
                        totalPairs, 
                        "상세 다이아 서버 수집 중... [$pdiaNo DIA]"
                    )

                    try {
                        val todayStr = LocalDate.now().format(dateFormatter)
                        val detail = diaDetailDataSource.fetchDiaDetail(todayStr, pdiaNo)
                        val trainNumbers = detail.segments
                            .mapNotNull { it.trnNo?.trim()?.takeIf(String::isNotEmpty) }
                            .filter { it != "도" && it != "도보" }
                            .distinct()
                            .joinToString(",")

                        val jsonStr = detail.rawRootJson?.toString().orEmpty()
                        if (jsonStr.isNotBlank()) {
                            diaCensusDao.insertCensus(
                                DiaCensusEntity(
                                    pdiaNo = pdiaNo,
                                    trainNumbers = trainNumbers,
                                    detailJson = jsonStr,
                                    updatedAt = updatedAt
                                )
                            )
                        }
                    } catch (e: Exception) {
                        runCatching { Log.w(TAG, "Failed to fetch dia detail for pdiaNo $pdiaNo", e) }
                    }
                    delay(200) // gentle delay between XHR calls
                }

                val msg = "기초자료 조사 완료! (총 ${totalPairs}개 다이아 보관 완료)"
                _progressState.value = CensusProgressState.Finished(totalPairs, msg)
                runCatching { Log.i(TAG, msg) }
            } catch (e: Exception) {
                runCatching { Log.e(TAG, "Error during background census", e) }
                _progressState.value = CensusProgressState.Error("기초자료 조사 중 오류가 발생했습니다: ${e.message}")
            }
        }
    }

    companion object {
        private const val TAG = "DiaCensusManager"
    }
}
