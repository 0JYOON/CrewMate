package com.example.crewmate.labs.handovercrew

import android.content.Context
import com.example.crewmate.data.remote.xcrew.CrewAssignment
import com.example.crewmate.data.remote.xcrew.DiaDetailParser
import com.example.crewmate.data.remote.xcrew.DiaDetailResponse
import com.example.crewmate.data.remote.xcrew.DiaDetailSegment
import com.example.crewmate.data.remote.xcrew.XcrewSessionRegistry
import com.example.crewmate.data.remote.xcrew.XcrewWebViewXhrDataSource
import com.example.crewmate.data.remote.xcrew.XcrewWorkScheduleParser
import com.example.crewmate.database.CrewMateDatabase
import com.example.crewmate.schedule.TrainCrewSearchMatcher
import com.example.crewmate.schedule.TrainCrewSearchRepository
import com.example.crewmate.schedule.TrainCrewSearchResult
import com.example.crewmate.schedule.TrainNumberNormalizer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate

/**
 * 교대승무원 조회 저장소.
 * 온라인 상태에서는 Xcrew 실시간 승무/다이아 조회를 수행하며,
 * 오프라인 모드이거나 세션이 없을 때는 Room DB(crew_assignments + dia_details) 캐시로 조회합니다.
 */
class HandoverCrewRepository(private val context: Context) {

    private val database by lazy { CrewMateDatabase.getInstance(context.applicationContext) }
    private val diaDetailParser by lazy { DiaDetailParser() }

    suspend fun lookupHandover(
        workDate: LocalDate,
        currentDiaNo: String,
        allSegments: List<DiaDetailSegment>,
        targetSegment: DiaDetailSegment
    ): HandoverCrewReport = withContext(Dispatchers.IO) {
        val rawTrainNo = targetSegment.trnNo?.trim().orEmpty()
        val normalizedTrain = TrainNumberNormalizer.normalizeTrainNumber(rawTrainNo)
        require(normalizedTrain.isNotEmpty()) { "조회 가능한 열차번호가 아닙니다." }

        val segmentDate = HandoverCrewResolver.resolveSegmentDate(workDate, allSegments, targetSegment)
        val targetDateStr = HandoverCrewResolver.formatApiDate(segmentDate)

        val prefs = context.getSharedPreferences("crewmate_prefs", Context.MODE_PRIVATE)
        val isOfflineMode = prefs.getBoolean("is_offline_mode", false)
        val session = XcrewSessionRegistry.current() ?: XcrewSessionRegistry.rawCurrent()

        if (!isOfflineMode && session != null) {
            val dataSource = XcrewWebViewXhrDataSource(session, XcrewWorkScheduleParser())
            val censusResults = runCatching {
                searchFromCensusWithAssignments(
                    segmentDate = segmentDate,
                    targetDateStr = targetDateStr,
                    normalizedTrain = normalizedTrain,
                    onlineDataSource = dataSource
                )
            }.getOrDefault(emptyList())
            if (censusResults.isNotEmpty()) {
                return@withContext HandoverCrewResolver.resolveHandover(
                    trainNumber = rawTrainNo,
                    targetDate = targetDateStr,
                    currentDiaNo = currentDiaNo,
                    currentSegment = targetSegment,
                    results = censusResults,
                    fromOfflineCache = false
                )
            }

            val onlineAttempt = runCatching {
                val repo = TrainCrewSearchRepository(
                    crewWorkStatusDataSource = dataSource,
                    diaDetailDataSource = dataSource,
                    isOfflineModeProvider = { false }
                )
                repo.search(targetDateStr, normalizedTrain)
            }
            onlineAttempt.onSuccess { report ->
                return@withContext HandoverCrewResolver.resolveHandover(
                    trainNumber = rawTrainNo,
                    targetDate = targetDateStr,
                    currentDiaNo = currentDiaNo,
                    currentSegment = targetSegment,
                    results = report.results,
                    fromOfflineCache = false
                )
            }
            // 온라인 실패 시 로컬 캐시가 있으면 폴백 시도, 없으면 예외 전달
            val cachedResults = searchFromLocalCache(segmentDate, targetDateStr, normalizedTrain)
            if (cachedResults.isNotEmpty()) {
                return@withContext HandoverCrewResolver.resolveHandover(
                    trainNumber = rawTrainNo,
                    targetDate = targetDateStr,
                    currentDiaNo = currentDiaNo,
                    currentSegment = targetSegment,
                    results = cachedResults,
                    fromOfflineCache = true
                )
            }
            throw onlineAttempt.exceptionOrNull() ?: IllegalStateException("교대승무원 조회에 실패했습니다.")
        }

        val cachedResults = searchFromLocalCache(segmentDate, targetDateStr, normalizedTrain)
        HandoverCrewResolver.resolveHandover(
            trainNumber = rawTrainNo,
            targetDate = targetDateStr,
            currentDiaNo = currentDiaNo,
            currentSegment = targetSegment,
            results = cachedResults,
            fromOfflineCache = true
        )
    }

    private suspend fun searchFromCensusWithAssignments(
        segmentDate: LocalDate,
        targetDateStr: String,
        normalizedTrain: String,
        onlineDataSource: XcrewWebViewXhrDataSource
    ): List<TrainCrewSearchResult> {
        val censusEntities = runCatching {
            database.diaCensusDao().searchCensusByTrainNumber(normalizedTrain)
        }.getOrDefault(emptyList())
        if (censusEntities.isEmpty()) return emptyList()

        val prevDateStr = HandoverCrewResolver.formatApiDate(segmentDate.minusDays(1))
        val diaDetails = censusEntities.mapNotNull { entity ->
            runCatching {
                val parsed = diaDetailParser.parse(entity.detailJson)
                if (parsed.segments.isNotEmpty()) entity.pdiaNo.trim() to parsed else null
            }.getOrNull()
        }
        if (diaDetails.isEmpty()) return emptyList()

        val assignToday = runCatching { onlineDataSource.fetchCrewWorkStatus(targetDateStr) }
            .getOrElse { loadCachedAssignments(targetDateStr) }
        val assignPrev = runCatching { onlineDataSource.fetchCrewWorkStatus(prevDateStr) }
            .getOrElse { loadCachedAssignments(prevDateStr) }

        val resToday = TrainCrewSearchMatcher.match(
            sourceDt = targetDateStr,
            targetDt = targetDateStr,
            searchTrainNumber = normalizedTrain,
            diaDetailsInApiOrder = diaDetails,
            assignmentsByDia = assignToday.groupBy { it.pdiaNo.trim() }
        )
        val resPrev = TrainCrewSearchMatcher.match(
            sourceDt = prevDateStr,
            targetDt = targetDateStr,
            searchTrainNumber = normalizedTrain,
            diaDetailsInApiOrder = diaDetails,
            assignmentsByDia = assignPrev.groupBy { it.pdiaNo.trim() }
        )
        return (resPrev + resToday).distinctBy {
            "${it.pjtDt}|${it.pdiaNo}|${it.segment.dptTm}|${it.segment.arvTm}"
        }
    }

    private suspend fun loadCachedAssignments(dt: String): List<CrewAssignment> {
        return database.crewAssignmentDao().getAssignmentsForDate(dt).map { entity ->
            CrewAssignment(
                pdiaNo = entity.pdiaNo,
                repTrn1No = entity.repTrn1No,
                repTrn2No = entity.repTrn2No,
                gwkTm = entity.gwkTm,
                loiwTm = entity.loiwTm,
                crewMembers = entity.crewMembers
            )
        }
    }

    private suspend fun searchFromLocalCache(
        segmentDate: LocalDate,
        targetDateStr: String,
        normalizedTrain: String
    ): List<TrainCrewSearchResult> {
        val prevDateStr = HandoverCrewResolver.formatApiDate(segmentDate.minusDays(1))
        val todayResults = matchCachedDate(sourceDt = targetDateStr, targetDt = targetDateStr, normalizedTrain = normalizedTrain)
        val prevResults = matchCachedDate(sourceDt = prevDateStr, targetDt = targetDateStr, normalizedTrain = normalizedTrain)
        return (prevResults + todayResults).distinctBy {
            "${it.pjtDt}|${it.pdiaNo}|${it.segment.dptTm}|${it.segment.arvTm}"
        }
    }

    private suspend fun matchCachedDate(
        sourceDt: String,
        targetDt: String,
        normalizedTrain: String
    ): List<TrainCrewSearchResult> {
        val assignments = database.crewAssignmentDao().getAssignmentsForDate(sourceDt).map { entity ->
            CrewAssignment(
                pdiaNo = entity.pdiaNo,
                repTrn1No = entity.repTrn1No,
                repTrn2No = entity.repTrn2No,
                gwkTm = entity.gwkTm,
                loiwTm = entity.loiwTm,
                crewMembers = entity.crewMembers
            )
        }
        val diaEntities = database.diaDetailDao().getDiaDetailsForDate(sourceDt)
        val parsedFromDetails = diaEntities.mapNotNull { entity ->
            runCatching {
                val parsed = diaDetailParser.parse(entity.detailJson)
                if (parsed.segments.isNotEmpty()) entity.pdiaNo to parsed else null
            }.getOrNull()
        }

        val diaDetailsMap = linkedMapOf<String, DiaDetailResponse>()
        parsedFromDetails.forEach { (pdiaNo, detail) ->
            diaDetailsMap[pdiaNo.trim()] = detail
        }

        // 기초자료(dia_census)에 저장된 다이아도 보조 결합
        if (assignments.isNotEmpty()) {
            val allCensus = runCatching { database.diaCensusDao().getAllCensus() }.getOrDefault(emptyList())
            allCensus.forEach { census ->
                val cleanDia = census.pdiaNo.trim()
                if (cleanDia !in diaDetailsMap) {
                    runCatching {
                        val parsed = diaDetailParser.parse(census.detailJson)
                        if (parsed.segments.isNotEmpty()) {
                            diaDetailsMap[cleanDia] = parsed
                        }
                    }
                }
            }
        }

        val diaDetails = diaDetailsMap.entries.map { it.key to it.value }
        if (diaDetails.isEmpty()) return emptyList()

        val assignmentsByDia = assignments.groupBy(CrewAssignment::pdiaNo)
        return TrainCrewSearchMatcher.match(
            sourceDt = sourceDt,
            targetDt = targetDt,
            searchTrainNumber = normalizedTrain,
            diaDetailsInApiOrder = diaDetails,
            assignmentsByDia = assignmentsByDia
        )
    }
}
