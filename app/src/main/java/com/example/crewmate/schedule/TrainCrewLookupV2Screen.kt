package com.example.crewmate.schedule

import android.app.DatePickerDialog
import android.util.Log
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.crewmate.data.remote.xcrew.CrewWorkStatusDataSource
import com.example.crewmate.data.remote.xcrew.DiaDetailDataSource
import com.example.crewmate.data.remote.xcrew.DiaDetailParser
import com.example.crewmate.database.DiaCensusDao
import com.example.crewmate.database.DiaCensusEntity
import com.example.crewmate.schedule.CensusProgressState
import com.example.crewmate.schedule.DiaCensusManager
import com.example.crewmate.schedule.DiaDetailLoadState
import com.example.crewmate.schedule.ScheduleDetailSheet
import com.example.crewmate.schedule.TrainNumberNormalizer
import com.example.crewmate.schedule.WorkSchedule
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

internal object OfflineLookupRange {
    fun contains(today: LocalDate, target: LocalDate): Boolean =
        !target.isBefore(today.minusDays(1)) && !target.isAfter(today.plusDays(1))

    fun clamp(today: LocalDate, target: LocalDate): LocalDate =
        target.coerceIn(today.minusDays(1), today.plusDays(1))
}

data class TrainCrewV2SearchResult(
    val pjtDt: String,         // YYYYMMDD
    val pdiaNo: String,        // e.g. "16014"
    val category: String,      // e.g. "운전", "출고준비", "편승", "입환"
    val trainNo: String,       // e.g. "K4501"
    val dptStnNm: String,     // e.g. "안산"
    val dptTimeStr: String,   // e.g. "06:43"
    val arvStnNm: String,     // e.g. "불암산"
    val arvTimeStr: String,   // e.g. "08:25"
    val crewNames: List<String>, // e.g. ["윤영준", "홍길동"]
    val gwkTm: String?,       // Commute start time e.g. "06:03"
    val loiwTm: String?,       // Commute end time e.g. "17:07"
    val isPreviousDay: Boolean = false // 전일 출근
)

private val DISPLAY_DATE = DateTimeFormatter.ofPattern("yyyy.MM.dd", Locale.ROOT)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrainCrewLookupV2Screen(
    censusManager: DiaCensusManager,
    diaCensusDao: DiaCensusDao,
    crewWorkStatusDataSource: CrewWorkStatusDataSource? = null,
    diaDetailDataSource: DiaDetailDataSource? = null,
    isOfflineMode: Boolean = false,
    onBack: () -> Unit = {}
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    // 1단계 전수조사 상태
    val censusList by diaCensusDao.getAllCensusFlow().collectAsState(initial = emptyList())
    val censusState by censusManager.progressState.collectAsState()

    val isRunningCensus = censusState is CensusProgressState.Running
    val censusProgress = when (val s = censusState) {
        is CensusProgressState.Running -> if (s.total > 0) s.current.toFloat() / s.total.toFloat() else 0f
        else -> 0f
    }
    val censusProgressText = when (val s = censusState) {
        is CensusProgressState.Running -> s.message
        is CensusProgressState.Finished -> s.message
        is CensusProgressState.Error -> s.message
        else -> ""
    }

    val censusCount = censusList.size
    val totalTrainCount = remember(censusList) {
        censusList.flatMap { it.trainNumbers.split(",") }.filter { it.isNotBlank() }.distinct().size
    }
    val latestUpdateDate = remember(censusList) {
        censusList.maxOfOrNull { it.updatedAt } ?: "수집 이력 없음"
    }

    // 2단계 승무원 조회 상태
    var searchDate by remember { mutableStateOf(LocalDate.now()) }
    var trainNumberInput by remember { mutableStateOf("") }
    var isSearchingV2 by remember { mutableStateOf(false) }
    var searchV2Results by remember { mutableStateOf<List<TrainCrewV2SearchResult>?>(null) }
    var searchErrorMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(isOfflineMode) {
        if (isOfflineMode) {
            val today = LocalDate.now()
            if (!OfflineLookupRange.contains(today, searchDate)) {
                searchDate = OfflineLookupRange.clamp(today, searchDate)
            }
        }
    }

    // 1단계 로컬 DB 진단 상태 변수들
    var diagDiaInput by remember { mutableStateOf("") }
    var diagDiaResults by remember { mutableStateOf<List<DiaCensusEntity>?>(null) }
    var diagTrainInput by remember { mutableStateOf("") }
    var diagTrainResults by remember { mutableStateOf<List<DiaCensusEntity>?>(null) }

    // 다이아 상세 바텀시트
    var selectedDiaNo by remember { mutableStateOf<String?>(null) }
    val bottomSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val detailCache = remember { mutableStateMapOf<String, DiaDetailLoadState>() }

    // Helper function for V2 Search
    fun executeV2Search() {
        val normalizedTrain = TrainNumberNormalizer.normalizeTrainNumber(trainNumberInput)
        if (normalizedTrain.isBlank()) {
            Toast.makeText(context, "열차번호를 입력해 주세요.", Toast.LENGTH_SHORT).show()
            return
        }
        if (censusCount == 0) {
            searchErrorMessage = if (isOfflineMode) {
                "⚠️ 저장된 다이아 기초자료 조사 데이터가 없습니다. 온라인 모드에서 [다이아 기초자료 조사 실행]을 먼저 진행해 주세요."
            } else {
                "⚠️ 저장된 다이아 기초자료 조사 데이터가 없습니다. 하단의 [다이아 기초자료 조사 실행]을 먼저 진행해 주세요."
            }
            searchV2Results = null
            return
        }

        if (isOfflineMode) {
            val today = LocalDate.now()
            if (!OfflineLookupRange.contains(today, searchDate)) {
                searchDate = OfflineLookupRange.clamp(today, searchDate)
            }
        }

        isSearchingV2 = true
        searchErrorMessage = null

        coroutineScope.launch(Dispatchers.IO) {
            try {
                val pjtDt = searchDate.format(DateTimeFormatter.ofPattern("yyyyMMdd", Locale.ROOT))
                val prevDt = searchDate.minusDays(1).format(DateTimeFormatter.ofPattern("yyyyMMdd", Locale.ROOT))

                // Fetch crew assignments for pjtDt and prevDt
                val (assignmentsToday, assignmentsPrev) = coroutineScope {
                    val todayDef = async {
                        runCatching { crewWorkStatusDataSource?.fetchCrewWorkStatus(pjtDt).orEmpty() }.getOrDefault(emptyList())
                    }
                    val prevDef = async {
                        runCatching { crewWorkStatusDataSource?.fetchCrewWorkStatus(prevDt).orEmpty() }.getOrDefault(emptyList())
                    }
                    Pair(todayDef.await(), prevDef.await())
                }

                if (isOfflineMode && assignmentsToday.isEmpty() && assignmentsPrev.isEmpty()) {
                    withContext(Dispatchers.Main) {
                        isSearchingV2 = false
                        searchV2Results = null
                        searchErrorMessage = "⚠️ 저장된 소속 승무원 근무 현황 데이터가 없습니다. (오프라인 조회 가능 범위: 어제~내일)"
                    }
                    return@launch
                }

                val assignmentsByDiaToday = assignmentsToday.groupBy { it.pdiaNo.trim() }
                val assignmentsByDiaPrev = assignmentsPrev.groupBy { it.pdiaNo.trim() }

                // Query census records from DB
                val matchingEntities = diaCensusDao.searchCensusByTrainNumber(normalizedTrain)
                val parser = DiaDetailParser()
                val results = mutableListOf<TrainCrewV2SearchResult>()

                matchingEntities.forEach { entity ->
                    try {
                        val detail = parser.parse(entity.detailJson)
                        val segmentDatesToday = getSegmentDates(pjtDt, detail.segments)
                        val segmentDatesPrev = getSegmentDates(prevDt, detail.segments)

                        detail.segments.forEachIndexed { index, seg ->
                            val segTrn = TrainNumberNormalizer.normalizeTrainNumber(seg.trnNo.orEmpty())
                            if (segTrn == normalizedTrain) {
                                val dateToday = segmentDatesToday.getOrElse(index) { pjtDt }
                                val datePrev = segmentDatesPrev.getOrElse(index) { prevDt }

                                val cat = seg.pjtHrDvNm?.trim()?.ifEmpty { "운전" } ?: "운전"
                                val dptStn = seg.dptStnNm?.trim()?.ifEmpty { "-" } ?: "-"
                                val dptTm = seg.dptTm?.toClockFormat() ?: "-"
                                val arvStn = seg.arvStnNm?.trim()?.ifEmpty { "-" } ?: "-"
                                val arvTm = seg.arvTm?.toClockFormat() ?: "-"

                                if (dateToday == pjtDt) {
                                    val matchedAssignments = assignmentsByDiaToday[entity.pdiaNo].orEmpty()
                                    val crewNames = matchedAssignments.flatMap { it.crewMembers }.distinct()
                                    val gwkTm = matchedAssignments.firstOrNull()?.gwkTm?.toClockFormat()
                                    val loiwTm = matchedAssignments.firstOrNull()?.loiwTm?.toClockFormat()

                                    results.add(
                                        TrainCrewV2SearchResult(
                                            pjtDt = pjtDt,
                                            pdiaNo = entity.pdiaNo,
                                            category = cat,
                                            trainNo = seg.trnNo?.trim().orEmpty(),
                                            dptStnNm = dptStn,
                                            dptTimeStr = dptTm,
                                            arvStnNm = arvStn,
                                            arvTimeStr = arvTm,
                                            crewNames = crewNames,
                                            gwkTm = gwkTm,
                                            loiwTm = loiwTm,
                                            isPreviousDay = false
                                        )
                                    )
                                } else if (datePrev == pjtDt) {
                                    // Segment started yesterday on prevDt and crossed midnight into today (pjtDt)!
                                    val matchedAssignments = assignmentsByDiaPrev[entity.pdiaNo].orEmpty()
                                    val crewNames = matchedAssignments.flatMap { it.crewMembers }.distinct()
                                    val gwkTm = matchedAssignments.firstOrNull()?.gwkTm?.toClockFormat()
                                    val loiwTm = matchedAssignments.firstOrNull()?.loiwTm?.toClockFormat()

                                    results.add(
                                        TrainCrewV2SearchResult(
                                            pjtDt = prevDt,
                                            pdiaNo = entity.pdiaNo,
                                            category = cat,
                                            trainNo = seg.trnNo?.trim().orEmpty(),
                                            dptStnNm = dptStn,
                                            dptTimeStr = dptTm,
                                            arvStnNm = arvStn,
                                            arvTimeStr = arvTm,
                                            crewNames = crewNames,
                                            gwkTm = gwkTm,
                                            loiwTm = loiwTm,
                                            isPreviousDay = true
                                        )
                                    )
                                }
                            }
                        }
                    } catch (e: Exception) {
                        Log.w("TrainCrewLookupV2", "Failed to parse census entity", e)
                    }
                }

                // Sort results by departure time and filter out empty crew results
                val sortedResults = results
                    .distinctBy { it.pdiaNo to (it.trainNo to (it.dptTimeStr to it.isPreviousDay)) }
                    .filter { it.crewNames.isNotEmpty() }
                    .sortedBy { it.dptTimeStr }

                withContext(Dispatchers.Main) {
                    isSearchingV2 = false
                    searchV2Results = sortedResults
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    isSearchingV2 = false
                    searchErrorMessage = "승무원 탐색 중 오류가 발생했습니다: ${e.message}"
                }
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.Top
    ) {
        // Top Header Title
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "열차 승무원 조회",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.primary
            )
        }

        Spacer(Modifier.height(14.dp))

        // ==========================================
        // TOP MODULE: Phase 2 Train Crew Search V2
        // ==========================================
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        ) {
            Column(Modifier.fillMaxWidth().padding(14.dp)) {
                if (isRunningCensus) {
                    Surface(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                        color = MaterialTheme.colorScheme.tertiaryContainer,
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            val progressText = when (val s = censusState) {
                                is CensusProgressState.Running -> "진행률: ${s.current} / ${s.total} (${(censusProgress * 100).toInt()}%)"
                                else -> "기초 자료 조사 수집 진행 중..."
                            }
                            Text("⏳ 승무원 조회를 위한 기초 자료 조사가 진행 중입니다.", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = MaterialTheme.colorScheme.onTertiaryContainer)
                            Text(progressText, fontSize = 12.sp, color = MaterialTheme.colorScheme.onTertiaryContainer)
                            LinearProgressIndicator(
                                progress = { censusProgress },
                                modifier = Modifier.fillMaxWidth(),
                                strokeCap = androidx.compose.ui.graphics.StrokeCap.Round,
                                trackColor = MaterialTheme.colorScheme.surfaceVariant,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(modifier = Modifier.weight(1f)) {
                        OutlinedTextField(
                            value = searchDate.format(DISPLAY_DATE),
                            onValueChange = {},
                            readOnly = true,
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("운행일자") },
                            shape = RoundedCornerShape(12.dp),
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = MaterialTheme.colorScheme.onSurface,
                                unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                                focusedBorderColor = MaterialTheme.colorScheme.primary,
                                unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                                focusedLabelColor = MaterialTheme.colorScheme.primary,
                                unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant
                            )
                        )
                        Box(
                            modifier = Modifier
                                .matchParentSize()
                                .clickable(enabled = !isSearchingV2) {
                                    val dialog = DatePickerDialog(
                                        context,
                                        { _, year, month, day ->
                                            val picked = LocalDate.of(year, month + 1, day)
                                            searchDate = if (isOfflineMode) {
                                                OfflineLookupRange.clamp(LocalDate.now(), picked)
                                            } else {
                                                picked
                                            }
                                        },
                                        searchDate.year,
                                        searchDate.monthValue - 1,
                                        searchDate.dayOfMonth,
                                    )
                                    if (isOfflineMode) {
                                        val zone = ZoneId.systemDefault()
                                        val today = LocalDate.now()
                                        dialog.datePicker.minDate =
                                            today.minusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
                                        dialog.datePicker.maxDate =
                                            today.plusDays(1).atTime(23, 59, 59).atZone(zone).toInstant().toEpochMilli()
                                    }
                                    dialog.show()
                                },
                        )
                    }
                    OutlinedTextField(
                        value = trainNumberInput,
                        onValueChange = { trainNumberInput = it },
                        modifier = Modifier.weight(1f),
                        label = { Text("열차번호") },
                        placeholder = { Text("예: 4501") },
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = MaterialTheme.colorScheme.onSurface,
                            unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                            focusedLabelColor = MaterialTheme.colorScheme.primary,
                            unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant
                        ),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Ascii,
                            imeAction = ImeAction.Search,
                        ),
                        keyboardActions = KeyboardActions(
                            onSearch = { executeV2Search() },
                            onDone = { executeV2Search() },
                        ),
                        singleLine = true,
                        enabled = !isSearchingV2 && !isRunningCensus,
                    )
                }

                if (isOfflineMode) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = "📌 오프라인 모드: 운행일자는 어제~내일 범위만 선택할 수 있습니다.",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = { executeV2Search() },
                    enabled = !isSearchingV2 && !isRunningCensus,
                    modifier = Modifier.fillMaxWidth().height(46.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    ),
                ) {
                    Text(
                        text = if (isSearchingV2) "승무원 탐색 중..." else "🔍 승무원 조회",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                    )
                }
            }
        }

        // Search Results Cards Display
        if (isSearchingV2) {
            Spacer(Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(12.dp))
                Text("저장된 기초자료 DB에서 승무 정보를 탐색 중입니다...", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        searchErrorMessage?.let { msg ->
            Spacer(Modifier.height(12.dp))
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.errorContainer,
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(
                    text = msg,
                    modifier = Modifier.padding(14.dp),
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp
                )
            }
        }

        searchV2Results?.let { results ->
            Spacer(Modifier.height(14.dp))
            if (results.isEmpty()) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.surface,
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Text(
                        text = "입력하신 열차번호와 일치하는 승무 정보가 없습니다.",
                        modifier = Modifier.padding(18.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 14.sp
                    )
                }
            } else {
                val dutyResults = remember(results) { results.filter { !it.category.contains("편승") } }
                val deadheadResults = remember(results) { results.filter { it.category.contains("편승") } }
                var showDeadheadSection by remember { mutableStateOf(false) }

                if (dutyResults.isNotEmpty()) {
                    Text(
                        text = "주요 승무 구간 (${dutyResults.size}건)",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )

                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        dutyResults.forEach { res ->
                            TrainCrewV2ResultCard(res = res, onDiaClick = { selectedDiaNo = it })
                        }
                    }
                } else if (deadheadResults.isNotEmpty()) {
                    Surface(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                        color = MaterialTheme.colorScheme.surface,
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        Text(
                            text = "주요 승무 구간(운전 등)은 없으며, 편승 구간만 검색되었습니다.",
                            modifier = Modifier.padding(16.dp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                if (deadheadResults.isNotEmpty()) {
                    Spacer(Modifier.height(10.dp))
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showDeadheadSection = !showDeadheadSection },
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "🚌 편승 구간 정보 (${deadheadResults.size}건)",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = if (showDeadheadSection) "▲ 접기" else "▼ 펼치기",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    AnimatedVisibility(visible = showDeadheadSection) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            deadheadResults.forEach { res ->
                                TrainCrewV2ResultCard(res = res, onDiaClick = { selectedDiaNo = it })
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            Text(
                text = if (isOfflineMode) {
                    "ℹ️ 오프라인 모드에서는 저장된 기초자료·캐시 기준으로 표시되며 실시간 Xcrew 변동사항이 반영되지 않습니다."
                } else {
                    "ℹ️ 열차·다이아 구간 정보는 수집된 기초자료(${latestUpdateDate}) 기반이며, 실시간 Xcrew 변경사항과 다를 수 있습니다."
                },
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 15.sp,
                modifier = Modifier.padding(horizontal = 4.dp)
            )
        }

        Spacer(Modifier.height(24.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, thickness = 1.dp)
        Spacer(Modifier.height(20.dp))

        // ==========================================
        // BOTTOM MODULE: Phase 1 Census Module
        // ==========================================
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "📊 승무원 조회 기초 자료(다이아) 수집",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                Text(
                    text = "저장된 다이아 정보 : ${latestUpdateDate} 기준\n총 ${censusCount}개 다이아, ${totalTrainCount}개 연계 열차 데이터 보관 중",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                    lineHeight = 20.sp
                )

                if (isOfflineMode) {
                    Text(
                        text = "ℹ️ 기초자료 조사는 온라인 모드에서 먼저 실행해 주세요. 이미 저장된 기초자료로는 오프라인에서도 승무원 조회가 가능합니다.",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    Text(
                        text = "⚠️ 조사 실행 도중 화면을 벗어나지 않는 것을 권장드립니다.",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.error
                    )
                }

                if (isRunningCensus || censusProgressText.isNotBlank()) {
                    Spacer(Modifier.height(4.dp))
                    if (isRunningCensus) {
                        LinearProgressIndicator(
                            progress = { censusProgress },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp),
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = MaterialTheme.colorScheme.surface,
                        )
                    }
                    Text(
                        text = censusProgressText,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (censusState is CensusProgressState.Error) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                    )
                }

                Spacer(Modifier.height(4.dp))

                Button(
                    onClick = {
                        if (isRunningCensus || isOfflineMode) return@Button
                        val today = LocalDate.now()
                        censusManager.startBackgroundCensus(
                            startDate = today.withDayOfMonth(1),
                            daysCount = today.lengthOfMonth()
                        )
                        Toast.makeText(context, "기존 수집 데이터를 초기화하고 당월 기초자료 조사를 시작했습니다.", Toast.LENGTH_SHORT).show()
                    },
                    enabled = !isRunningCensus && !isOfflineMode,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    )
                ) {
                    Text(
                        text = when {
                            isOfflineMode -> "오프라인 모드에서는 실행할 수 없습니다"
                            isRunningCensus -> "기초자료 조사 수집 진행 중..."
                            else -> "다이아 기초자료 조사 실행"
                        },
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        Spacer(Modifier.height(18.dp))

        // Diagnostic Card A: 다이아번호 진단 카드
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "🔍 다이아번호 수집 정보 진단",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = diagDiaInput,
                        onValueChange = { diagDiaInput = it },
                        modifier = Modifier.weight(1f),
                        label = { Text("다이아번호") },
                        placeholder = { Text("예: 16014") },
                        shape = RoundedCornerShape(10.dp),
                        singleLine = true
                    )
                    Button(
                        onClick = {
                            val target = diagDiaInput.trim()
                            if (target.isBlank()) return@Button
                            coroutineScope.launch(Dispatchers.IO) {
                                val matches = diaCensusDao.getAllCensus().filter { it.pdiaNo.equals(target, ignoreCase = true) }
                                withContext(Dispatchers.Main) {
                                    diagDiaResults = matches
                                }
                            }
                        },
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.height(52.dp)
                    ) {
                        Text("조회", fontWeight = FontWeight.Bold)
                    }
                }

                diagDiaResults?.let { results ->
                    if (results.isEmpty()) {
                        Text("수집된 데이터 중 [${diagDiaInput}] 다이아 정보가 없습니다.", fontSize = 12.sp, color = MaterialTheme.colorScheme.error)
                    } else {
                        val parser = remember { DiaDetailParser() }
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            results.forEach { entity ->
                                Surface(
                                    color = MaterialTheme.colorScheme.surfaceVariant,
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Text("다이아: ${entity.pdiaNo} (수집시각: ${entity.updatedAt})", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                        Text("연계 열차번호: ${entity.trainNumbers}", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                                        val parsed = runCatching { parser.parse(entity.detailJson) }.getOrNull()
                                        parsed?.segments?.forEach { seg ->
                                            Text(
                                                text = "• [${seg.trnNo ?: "-"} (${seg.pjtHrDvNm ?: "운전"})] ${seg.dptStnNm} ${seg.dptTm} → ${seg.arvStnNm} ${seg.arvTm}",
                                                fontSize = 11.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        // Diagnostic Card B: 열차번호 진단 카드
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "🔍 열차번호 연관 다이아 진단",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = diagTrainInput,
                        onValueChange = { diagTrainInput = it },
                        modifier = Modifier.weight(1f),
                        label = { Text("열차번호") },
                        placeholder = { Text("예: K4501") },
                        shape = RoundedCornerShape(10.dp),
                        singleLine = true
                    )
                    Button(
                        onClick = {
                            val target = TrainNumberNormalizer.normalizeTrainNumber(diagTrainInput)
                            if (target.isBlank()) return@Button
                            coroutineScope.launch(Dispatchers.IO) {
                                val matches = diaCensusDao.searchCensusByTrainNumber(target)
                                withContext(Dispatchers.Main) {
                                    diagTrainResults = matches
                                }
                            }
                        },
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.height(52.dp)
                    ) {
                        Text("조회", fontWeight = FontWeight.Bold)
                    }
                }

                diagTrainResults?.let { results ->
                    if (results.isEmpty()) {
                        Text("수집된 데이터 중 [${diagTrainInput}] 열차와 매칭되는 다이아가 없습니다.", fontSize = 12.sp, color = MaterialTheme.colorScheme.error)
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            results.forEach { entity ->
                                Surface(
                                    color = MaterialTheme.colorScheme.surfaceVariant,
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Text("${entity.pdiaNo} DIA", fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.primary)
                                        Text("연계 열차번호: ${entity.trainNumbers}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))
        Spacer(Modifier.height(16.dp))
    }

    if (selectedDiaNo != null) {
        ModalBottomSheet(
            onDismissRequest = { selectedDiaNo = null },
            sheetState = bottomSheetState,
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            val mockSchedule = WorkSchedule(
                workDate = searchDate.format(DateTimeFormatter.ofPattern("yyyyMMdd")),
                diaNo = selectedDiaNo,
                trainNo = null,
                trainNo2 = null,
                startTime = null,
                endTime = null,
                nonActiveType = null,
                isContinuation = false,
                isHoliday = false,
                isChanged = false
            )

            ScheduleDetailSheet(
                date = searchDate,
                schedules = listOf(mockSchedule),
                deviceEvents = emptyList(),
                diaDetailDataSource = diaDetailDataSource,
                detailCache = detailCache,
                showTabs = false
            )
        }
    }
}

private fun String.toClockFormat(): String {
    val v = this.trim().replace(":", "")
    if (v.length == 4 && v.all(Char::isDigit)) {
        return "${v.substring(0, 2)}:${v.substring(2, 4)}"
    }
    return this.trim()
}

@Composable
private fun TrainCrewV2ResultCard(
    res: TrainCrewV2SearchResult,
    onDiaClick: (String) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            // 1. 다이아 번호 & 출퇴근 시각
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "${res.pdiaNo} DIA",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.clickable { onDiaClick(res.pdiaNo) }
                    )
                    if (res.isPreviousDay) {
                        Spacer(Modifier.width(6.dp))
                        Surface(
                            color = MaterialTheme.colorScheme.errorContainer,
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Text(
                                text = "전일 출근",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                            )
                        }
                    }
                }

                val commuteText = when {
                    !res.gwkTm.isNullOrBlank() && !res.loiwTm.isNullOrBlank() -> "출근 ${res.gwkTm} / 퇴근 ${res.loiwTm}"
                    !res.gwkTm.isNullOrBlank() -> "출근 ${res.gwkTm}"
                    else -> "출퇴근시각 정보없음"
                }
                Text(
                    text = commuteText,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            // 2. 열차번호, 항목, 구간
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    color = if (res.category.contains("편승")) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.primaryContainer,
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text(
                        text = "${res.trainNo} (${res.category})",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (res.category.contains("편승")) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }

                Text(
                    text = "${res.dptStnNm} ${res.dptTimeStr} → ${res.arvStnNm} ${res.arvTimeStr}",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            // 3. 승무원 이름
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("👤 승무원", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    text = res.crewNames.joinToString(" · ").ifEmpty { "정보 없음" },
                    fontSize = 14.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

private fun getSegmentDates(baseDateStr: String, segments: List<com.example.crewmate.data.remote.xcrew.DiaDetailSegment>): List<String> {
    val formatter = DateTimeFormatter.ofPattern("yyyyMMdd", Locale.ROOT)
    var currentDate = runCatching { LocalDate.parse(baseDateStr, formatter) }.getOrElse { LocalDate.now() }
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
