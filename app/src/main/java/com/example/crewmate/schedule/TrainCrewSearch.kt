package com.example.crewmate.schedule

import android.util.Log
import com.example.crewmate.data.remote.xcrew.CrewAssignment
import com.example.crewmate.data.remote.xcrew.CrewWorkStatusDataSource
import com.example.crewmate.data.remote.xcrew.DiaDetailDataSource
import com.example.crewmate.data.remote.xcrew.DiaDetailResponse
import com.example.crewmate.data.remote.xcrew.DiaDetailSegment
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

data class TrainCrewSearchResult(
    val pjtDt: String,
    val pdiaNo: String,
    val segment: DiaDetailSegment,
    /** Keep duplicate assignments for a Dia; never silently replace one crew row with another. */
    val crewAssignments: List<CrewAssignment>,
) {
    val crewMembers: List<String>
        get() = crewAssignments.flatMap(CrewAssignment::crewMembers).distinct()
}

data class TrainCrewSearchReport(
    val pjtDt: String,
    val normalizedTrainNumber: String,
    val results: List<TrainCrewSearchResult>,
    val diaCount: Int,
    val failedDiaCount: Int,
)

object TrainNumberNormalizer {
    fun normalizeTrainNumber(value: String): String =
        value.trim().uppercase(Locale.ROOT).filter(Char::isDigit)
}

object TrainCrewSearchMatcher {
    fun match(
        sourceDt: String, // the date the Dia belongs to
        targetDt: String, // the date the user wants to search
        searchTrainNumber: String,
        diaDetailsInApiOrder: List<Pair<String, DiaDetailResponse>>,
        assignmentsByDia: Map<String, List<CrewAssignment>>,
    ): List<TrainCrewSearchResult> {
        val normalizedSearch = TrainNumberNormalizer.normalizeTrainNumber(searchTrainNumber)
        if (normalizedSearch.isEmpty()) return emptyList()

        return buildList {
            diaDetailsInApiOrder.forEach { (pdiaNo, diaDetail) ->
                val segmentDates = getSegmentDates(sourceDt, diaDetail.segments)
                diaDetail.segments.forEachIndexed segmentLoop@{ index, segment ->
                    val segmentDate = segmentDates[index]
                    val trainNo = segment.trnNo?.takeIf(String::isNotBlank) ?: return@segmentLoop
                    val dvName = segment.pjtHrDvNm?.trim() ?: ""
                    
                    if (dvName.contains("편승") || trainNo == "도" || trainNo == "도보") return@segmentLoop

                    if (segmentDate == targetDt && TrainNumberNormalizer.normalizeTrainNumber(trainNo) == normalizedSearch) {
                        add(
                            TrainCrewSearchResult(
                                pjtDt = sourceDt,
                                pdiaNo = pdiaNo,
                                segment = segment,
                                crewAssignments = assignmentsByDia[pdiaNo].orEmpty(),
                            ),
                        )
                    }
                }
            }
        }
    }

    private fun getSegmentDates(baseDateStr: String, segments: List<DiaDetailSegment>): List<String> {
        val formatter = java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd")
        var currentDate = java.time.LocalDate.parse(baseDateStr, formatter)
        var lastHour = -1

        return segments.map { segment ->
            val dptHour = segment.dptTm?.trim()?.takeIf { it.length == 4 }?.take(2)?.toIntOrNull()
            val arvHour = segment.arvTm?.trim()?.takeIf { it.length == 4 }?.take(2)?.toIntOrNull()
            
            if (dptHour != null) {
                if (lastHour in 18..23 && dptHour in 0..12) {
                    currentDate = currentDate.plusDays(1)
                }
                lastHour = dptHour
            }
            
            val segmentDateStr = currentDate.format(formatter)
            
            if (arvHour != null && lastHour != -1) {
                if (lastHour in 18..23 && arvHour in 0..12) {
                    currentDate = currentDate.plusDays(1)
                }
                lastHour = arvHour
            }
            
            segmentDateStr
        }
    }
}

class TrainCrewSearchRepository(
    private val crewWorkStatusDataSource: CrewWorkStatusDataSource,
    private val diaDetailDataSource: DiaDetailDataSource,
    private val isOfflineModeProvider: () -> Boolean = { false }
) {
    private val diaCache = ConcurrentHashMap<String, DiaDetailResponse>()

    suspend fun search(pjtDt: String, trainNumber: String): TrainCrewSearchReport {
        val normalizedSearch = TrainNumberNormalizer.normalizeTrainNumber(trainNumber)
        require(pjtDt.matches(Regex("\\d{8}"))) { "Project date must be yyyyMMdd" }
        require(normalizedSearch.isNotEmpty()) { "Train number must contain digits" }
        Log.i(TAG, "search date=$pjtDt train=$normalizedSearch")

        val prevDt = java.time.LocalDate.parse(pjtDt, java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd"))
            .minusDays(1)
            .format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd"))

        val (assignmentsToday, assignmentsPrev) = coroutineScope {
            if (isOfflineModeProvider()) {
                Pair(emptyList(), emptyList())
            } else {
                val todayDeferred = async { runCatching { crewWorkStatusDataSource.fetchCrewWorkStatus(pjtDt) }.getOrDefault(emptyList()) }
                val prevDeferred = async { runCatching { crewWorkStatusDataSource.fetchCrewWorkStatus(prevDt) }.getOrDefault(emptyList()) }
                Pair(todayDeferred.await(), prevDeferred.await())
            }
        }

        val assignmentsByDiaToday = assignmentsToday.groupBy(CrewAssignment::pdiaNo)
        val assignmentsByDiaPrev = assignmentsPrev.groupBy(CrewAssignment::pdiaNo)

        fun matchesTargetTrain(assignment: CrewAssignment, target: String): Boolean {
            val trn1 = assignment.repTrn1No?.let(TrainNumberNormalizer::normalizeTrainNumber).orEmpty()
            val trn2 = assignment.repTrn2No?.let(TrainNumberNormalizer::normalizeTrainNumber).orEmpty()
            return trn1 == target || trn2 == target
        }

        fun containsTargetTrain(assignment: CrewAssignment, target: String): Boolean {
            val trn1 = assignment.repTrn1No?.let(TrainNumberNormalizer::normalizeTrainNumber).orEmpty()
            val trn2 = assignment.repTrn2No?.let(TrainNumberNormalizer::normalizeTrainNumber).orEmpty()
            return trn1.contains(target) || trn2.contains(target)
        }

        val primaryCandidatesToday = assignmentsToday.filter { matchesTargetTrain(it, normalizedSearch) }.map { it.pdiaNo }.filter { it.matches(Regex("[A-Za-z0-9]+")) }.distinct()
        val primaryCandidatesPrev = assignmentsPrev.filter { matchesTargetTrain(it, normalizedSearch) }.map { it.pdiaNo }.filter { it.matches(Regex("[A-Za-z0-9]+")) }.distinct()

        val (diaNumbersToday, diaNumbersPrev) = if (primaryCandidatesToday.isNotEmpty() || primaryCandidatesPrev.isNotEmpty()) {
            Pair(primaryCandidatesToday, primaryCandidatesPrev)
        } else {
            val secondaryToday = assignmentsToday.filter { containsTargetTrain(it, normalizedSearch) }.map { it.pdiaNo }.filter { it.matches(Regex("[A-Za-z0-9]+")) }.distinct()
            val secondaryPrev = assignmentsPrev.filter { containsTargetTrain(it, normalizedSearch) }.map { it.pdiaNo }.filter { it.matches(Regex("[A-Za-z0-9]+")) }.distinct()
            if (secondaryToday.isNotEmpty() || secondaryPrev.isNotEmpty()) {
                Pair(secondaryToday, secondaryPrev)
            } else {
                val allToday = assignmentsToday.map(CrewAssignment::pdiaNo).filter { it.matches(Regex("[A-Za-z0-9]+")) }.distinct()
                val allPrev = assignmentsPrev.map(CrewAssignment::pdiaNo).filter { it.matches(Regex("[A-Za-z0-9]+")) }.distinct()
                Pair(allToday, allPrev)
            }
        }

        // 동시 호출 수(Semaphore) 대폭 제한: 3
        val semaphore = Semaphore(3)
        val fetchResults = coroutineScope {
            if (isOfflineModeProvider()) {
                emptyList()
            } else {
                val todayDeferreds = diaNumbersToday.map { pdiaNo ->
                    async { 
                        kotlinx.coroutines.delay(20) // 미세 딜레이로 서버 핑 분산
                        fetchDiaDetailWithCache(semaphore, pjtDt, pdiaNo) 
                    }
                }
                val prevDeferreds = diaNumbersPrev.map { pdiaNo ->
                    async { 
                        kotlinx.coroutines.delay(20)
                        fetchDiaDetailWithCache(semaphore, prevDt, pdiaNo) 
                    }
                }
                todayDeferreds.awaitAll() + prevDeferreds.awaitAll()
            }
        }
        val detailsToday = fetchResults.filter { it.pjtDt == pjtDt }.mapNotNull { result -> result.detail?.let { result.pdiaNo to it } }
        val detailsPrev = fetchResults.filter { it.pjtDt == prevDt }.mapNotNull { result -> result.detail?.let { result.pdiaNo to it } }
        
        val failedDiaCount = fetchResults.count(DiaFetchResult::failed)

        val totalDiaNumbers = diaNumbersToday.size + diaNumbersPrev.size
        if (totalDiaNumbers > 0 && detailsToday.isEmpty() && detailsPrev.isEmpty()) {
            throw AllDiaDetailsFailedException()
        }

        val resultsToday = TrainCrewSearchMatcher.match(
            sourceDt = pjtDt,
            targetDt = pjtDt,
            searchTrainNumber = normalizedSearch,
            diaDetailsInApiOrder = detailsToday,
            assignmentsByDia = assignmentsByDiaToday,
        )
        val resultsPrev = TrainCrewSearchMatcher.match(
            sourceDt = prevDt,
            targetDt = pjtDt,
            searchTrainNumber = normalizedSearch,
            diaDetailsInApiOrder = detailsPrev,
            assignmentsByDia = assignmentsByDiaPrev,
        )

        val allResults = resultsToday + resultsPrev

        Log.i(TAG, "search resultCount=${allResults.size} failedDiaCount=$failedDiaCount")
        return TrainCrewSearchReport(
            pjtDt = pjtDt,
            normalizedTrainNumber = normalizedSearch,
            results = allResults,
            diaCount = totalDiaNumbers,
            failedDiaCount = failedDiaCount,
        )
    }

    fun clearCache() {
        diaCache.clear()
    }

    private suspend fun fetchDiaDetailWithCache(semaphore: Semaphore, pjtDt: String, pdiaNo: String): DiaFetchResult {
        return semaphore.withPermit {
            val cacheKey = "$pjtDt|$pdiaNo"
            try {
                val detail = diaCache[cacheKey] ?: diaDetailDataSource
                    .fetchDiaDetail(pjtDt, pdiaNo)
                    .also { diaCache.putIfAbsent(cacheKey, it) }
                Log.i(TAG, "dia=$pdiaNo detail=success")
                DiaFetchResult(pjtDt, pdiaNo, detail, failed = false)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                Log.w(TAG, "dia=$pdiaNo detail=failure")
                DiaFetchResult(pjtDt, pdiaNo, detail = null, failed = true)
            }
        }
    }

    private companion object {
        const val TAG = "TrainCrewSearch"
        const val MAX_CONCURRENT_DETAIL_REQUESTS = 20
    }

    private data class DiaFetchResult(
        val pjtDt: String,
        val pdiaNo: String,
        val detail: DiaDetailResponse?,
        val failed: Boolean,
    )
}

class AllDiaDetailsFailedException : Exception()
