package com.example.crewmate.labs.colleaguecompare

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth

@Composable
fun ColleagueCompareScreen(
    viewModel: ColleagueCompareViewModel
) {
    val context = LocalContext.current
    val state by viewModel.uiState.collectAsState()
    val ymKey = viewModel.yearMonthKey()
    val horizontalScroll = rememberScrollState()

    var showAddColleagueDialog by remember { mutableStateOf(false) }
    var showAddVirtualDialog by remember { mutableStateOf(false) }
    var showFilterDialog by remember { mutableStateOf(false) }
    var hiddenPersonIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var editingVirtualPerson by remember { mutableStateOf<ComparePerson?>(null) }
    var editingCell by remember { mutableStateOf<Triple<ComparePerson, String, CompareDayCell?>?>(null) }

    val visiblePeople = remember(state.people, hiddenPersonIds) {
        state.people.filter { it.id !in hiddenPersonIds }
    }

    val daysInMonth = state.selectedMonth.lengthOfMonth()
    val dateList = remember(state.selectedMonth) {
        (1..daysInMonth).map { day ->
            state.selectedMonth.atDay(day)
        }
    }
    val today = remember { LocalDate.now() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 10.dp, vertical = 8.dp)
    ) {
        // 월 선택 & 새로고침 바
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 6.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { viewModel.previousMonth() }, modifier = Modifier.size(30.dp)) {
                        Text("◀", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                    Text(
                        text = "${state.selectedMonth.year}년 ${state.selectedMonth.monthValue}월",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.ExtraBold,
                        maxLines = 1,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(horizontal = 2.dp)
                    )
                    IconButton(onClick = { viewModel.nextMonth() }, modifier = Modifier.size(30.dp)) {
                        Text("▶", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    if (state.lastRefreshedAt != null) {
                        Text(
                            text = state.lastRefreshedAt!!,
                            fontSize = 8.5.sp,
                            maxLines = 1,
                            softWrap = false,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.Medium
                        )
                    }
                    val autoRef = state.displayOptions.autoRefresh
                    Surface(
                        onClick = { viewModel.setAutoRefresh(!autoRef) },
                        shape = RoundedCornerShape(6.dp),
                        color = if (autoRef) Color(0xFFDCFCE7) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
                        border = BorderStroke(
                            1.dp,
                            if (autoRef) Color(0xFF16A34A).copy(alpha = 0.5f) else MaterialTheme.colorScheme.outlineVariant
                        )
                    ) {
                        Text(
                            text = if (autoRef) "자동" else "수동",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (autoRef) Color(0xFF166534) else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.5.dp)
                        )
                    }
                    Surface(
                        onClick = { if (!state.isLoading) viewModel.refreshAllOnline() },
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.primaryContainer,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.55f)),
                        modifier = Modifier.size(30.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            if (state.isLoading) {
                                CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                            } else {
                                Text(text = "🔄", fontSize = 14.sp, color = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(6.dp))

        // 액션 버튼 바: 승무원 추가 / 수동 추가 / 필터
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            FilledTonalButton(
                onClick = {
                    viewModel.clearSearchResults()
                    showAddColleagueDialog = true
                },
                modifier = Modifier.weight(1f).height(34.dp),
                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text("+ 승무원 추가", fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1)
            }
            OutlinedButton(
                onClick = { showAddVirtualDialog = true },
                modifier = Modifier.weight(1f).height(34.dp),
                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text("+ 수동 추가", fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1)
            }
            if (state.people.isNotEmpty()) {
                OutlinedButton(
                    onClick = { showFilterDialog = true },
                    modifier = Modifier.height(34.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp)
                ) {
                    val activeCnt = visiblePeople.size
                    val totalCnt = state.people.size
                    Text(
                        text = if (hiddenPersonIds.isNotEmpty()) "🔍 $activeCnt/$totalCnt" else "🔍 필터",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1
                    )
                }
            }
        }

        Spacer(Modifier.height(6.dp))

        // 표시 옵션 뱃지 바: 출퇴근 표시 + 가로/세로 전환 + 자동 새로고침
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val commuteOn = state.displayOptions.showCommuteTimes
            Surface(
                onClick = { viewModel.setShowCommuteTimes(!commuteOn) },
                shape = RoundedCornerShape(8.dp),
                color = if (commuteOn) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                border = BorderStroke(
                    1.dp,
                    if (commuteOn) MaterialTheme.colorScheme.primary.copy(alpha = 0.5f) else MaterialTheme.colorScheme.outlineVariant
                )
            ) {
                Text(
                    text = if (commuteOn) "⚙️ 출퇴근 ON" else "⚙️ 출퇴근 OFF",
                    fontSize = 10.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (commuteOn) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }

            val transposed = state.displayOptions.transposeMatrix
            Surface(
                onClick = { viewModel.setTransposeMatrix(!transposed) },
                shape = RoundedCornerShape(8.dp),
                color = if (transposed) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                border = BorderStroke(
                    1.dp,
                    if (transposed) MaterialTheme.colorScheme.secondary.copy(alpha = 0.5f) else MaterialTheme.colorScheme.outlineVariant
                )
            ) {
                Text(
                    text = if (transposed) "⇄ 세로(날짜) 기준" else "⇄ 가로/세로 전환",
                    fontSize = 10.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (transposed) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        }

        if (state.statusMessage != null) {
            Spacer(Modifier.height(6.dp))
            Text(
                text = state.statusMessage!!,
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold
            )
        }
        if (state.errorMessage != null) {
            Spacer(Modifier.height(6.dp))
            Text(
                text = state.errorMessage!!,
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.error,
                fontWeight = FontWeight.SemiBold
            )
        }

        Spacer(Modifier.height(8.dp))

        val cellWidth = if (state.displayOptions.showCommuteTimes) 74.dp else 56.dp
        val rowHeight = if (state.displayOptions.showCommuteTimes) 42.dp else 34.dp
        val nameColWidth = 92.dp
        val dateColWidth = 70.dp

        // 비교 그리드 카드 (기본: 행=인원/열=날짜, 전환: 행=날짜/열=인원)
        Surface(
            modifier = Modifier.fillMaxWidth().weight(1f),
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
            if (!state.displayOptions.transposeMatrix) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    // 헤더 행: 이름 | 1(월) 2(화) ...
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Box(
                            modifier = Modifier
                                .width(nameColWidth)
                                .height(34.dp)
                                .border(0.5.dp, MaterialTheme.colorScheme.outlineVariant)
                                .padding(horizontal = 6.dp),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            Text("성명", fontSize = 11.sp, fontWeight = FontWeight.ExtraBold)
                        }
                        Row(modifier = Modifier.horizontalScroll(horizontalScroll)) {
                            dateList.forEach { d ->
                                val isToday = d == today
                                val (dowText, dowColor) = dayHeaderStyle(d)
                                Box(
                                    modifier = Modifier
                                        .width(cellWidth)
                                        .height(34.dp)
                                        .background(
                                            if (isToday) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f)
                                            else Color.Transparent
                                        )
                                        .border(0.5.dp, MaterialTheme.colorScheme.outlineVariant),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "${d.dayOfMonth}($dowText)",
                                        fontSize = 10.5.sp,
                                        fontWeight = if (isToday) FontWeight.ExtraBold else FontWeight.Bold,
                                        color = dowColor
                                    )
                                }
                            }
                        }
                    }

                    // 본문 스크롤 (내 근무표 + 동료/가상인물 목록)
                    LazyColumn(modifier = Modifier.fillMaxWidth()) {
                        item(key = "row_my_self") {
                            CompareMatrixRow(
                                name = "나 (본인)",
                                badgeText = null,
                                isMySelf = true,
                                isVirtual = false,
                                dateList = dateList,
                                today = today,
                                cellProvider = { d -> state.myCells[d.toString()] },
                                showCommuteTimes = state.displayOptions.showCommuteTimes,
                                nameColWidth = nameColWidth,
                                cellWidth = cellWidth,
                                rowHeight = rowHeight,
                                horizontalScroll = horizontalScroll,
                                onNameClick = null,
                                onCellClick = null,
                                onRemoveClick = null
                            )
                        }

                        items(items = visiblePeople, key = { it.id }) { person ->
                            val isVirtual = person.type == CompareEntryType.VIRTUAL_PERSON
                            CompareMatrixRow(
                                name = person.name,
                                badgeText = if (isVirtual) "가상" else null,
                                isMySelf = false,
                                isVirtual = isVirtual,
                                dateList = dateList,
                                today = today,
                                cellProvider = { d -> person.cellForDate(ymKey, d.toString()) },
                                showCommuteTimes = state.displayOptions.showCommuteTimes,
                                nameColWidth = nameColWidth,
                                cellWidth = cellWidth,
                                rowHeight = rowHeight,
                                horizontalScroll = horizontalScroll,
                                onNameClick = if (isVirtual) {
                                    { editingVirtualPerson = person }
                                } else null,
                                onCellClick = { d, cell -> editingCell = Triple(person, d.toString(), cell) },
                                onRemoveClick = { viewModel.removePerson(person.id) }
                            )
                        }
                    }
                }
            } else {
                // 행/열 전환 모드: 좌측 날짜 고정 + 우측 인원(나 + 동료들) 가로 스크롤
                val transposedColWidth = if (state.displayOptions.showCommuteTimes) 84.dp else 72.dp
                Column(modifier = Modifier.fillMaxWidth()) {
                    // 헤더 행: 날짜 | 나(본인) | 동료1 | ...
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Box(
                            modifier = Modifier
                                .width(dateColWidth)
                                .height(42.dp)
                                .border(0.5.dp, MaterialTheme.colorScheme.outlineVariant)
                                .padding(horizontal = 6.dp),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            Text("날짜", fontSize = 11.sp, fontWeight = FontWeight.ExtraBold)
                        }
                        Row(modifier = Modifier.horizontalScroll(horizontalScroll)) {
                            Box(
                                modifier = Modifier
                                    .width(transposedColWidth)
                                    .height(42.dp)
                                    .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f))
                                    .border(0.5.dp, MaterialTheme.colorScheme.outlineVariant)
                                    .padding(horizontal = 4.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "나 (본인)",
                                    fontSize = 10.5.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = MaterialTheme.colorScheme.primary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            visiblePeople.forEach { person ->
                                val isVirtual = person.type == CompareEntryType.VIRTUAL_PERSON
                                Row(
                                    modifier = Modifier
                                        .width(transposedColWidth)
                                        .height(42.dp)
                                        .border(0.5.dp, MaterialTheme.colorScheme.outlineVariant)
                                        .padding(horizontal = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .weight(1f)
                                            .then(
                                                if (isVirtual) Modifier.clickable { editingVirtualPerson = person }
                                                else Modifier
                                            ),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Text(
                                            text = person.name,
                                            fontSize = 10.5.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (isVirtual) Color(0xFF6D28D9) else MaterialTheme.colorScheme.onSurface,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    Text(
                                        text = "✕",
                                        fontSize = 10.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier
                                            .clickable { viewModel.removePerson(person.id) }
                                            .padding(horizontal = 2.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }
                    }

                    // 날짜별 행
                    LazyColumn(modifier = Modifier.fillMaxWidth()) {
                        items(items = dateList, key = { it.toString() }) { d ->
                            val isToday = d == today
                            val (dowText, dowColor) = dayHeaderStyle(d)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(
                                        if (isToday) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.18f)
                                        else MaterialTheme.colorScheme.surface
                                    )
                            ) {
                                Box(
                                    modifier = Modifier
                                        .width(dateColWidth)
                                        .height(rowHeight)
                                        .background(
                                            if (isToday) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
                                            else Color.Transparent
                                        )
                                        .border(0.5.dp, MaterialTheme.colorScheme.outlineVariant)
                                        .padding(horizontal = 6.dp),
                                    contentAlignment = Alignment.CenterStart
                                ) {
                                    Text(
                                        text = "${d.dayOfMonth}($dowText)",
                                        fontSize = 11.sp,
                                        fontWeight = if (isToday) FontWeight.ExtraBold else FontWeight.Bold,
                                        color = dowColor
                                    )
                                }
                                Row(modifier = Modifier.horizontalScroll(horizontalScroll)) {
                                    CompareCellBox(
                                        cell = state.myCells[d.toString()],
                                        isToday = isToday,
                                        isMySelfCell = true,
                                        showCommuteTimes = state.displayOptions.showCommuteTimes,
                                        cellWidth = transposedColWidth,
                                        rowHeight = rowHeight,
                                        onClick = null
                                    )
                                    visiblePeople.forEach { person ->
                                        val cell = person.cellForDate(ymKey, d.toString())
                                        CompareCellBox(
                                            cell = cell,
                                            isToday = isToday,
                                            isMySelfCell = false,
                                            showCommuteTimes = state.displayOptions.showCommuteTimes,
                                            cellWidth = transposedColWidth,
                                            rowHeight = rowHeight,
                                            onClick = { editingCell = Triple(person, d.toString(), cell) }
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

    // 승무원 표시 필터 팝업
    if (showFilterDialog) {
        ColleagueFilterDialog(
            people = state.people,
            hiddenPersonIds = hiddenPersonIds,
            onTogglePerson = { id ->
                hiddenPersonIds = if (id in hiddenPersonIds) hiddenPersonIds - id else hiddenPersonIds + id
            },
            onSelectAll = { hiddenPersonIds = emptySet() },
            onDeselectAll = { hiddenPersonIds = state.people.map { it.id }.toSet() },
            onDismiss = { showFilterDialog = false }
        )
    }

    // 승무원 추가 팝업
    if (showAddColleagueDialog) {
        AddColleagueDialog(
            isSearching = state.isSearchingColleagues,
            searchResults = state.searchResults,
            onSearch = { viewModel.searchColleagues(it) },
            onAddName = { name ->
                viewModel.addRealColleague(name)
                showAddColleagueDialog = false
            },
            onDismiss = { showAddColleagueDialog = false }
        )
    }

    // 수동 추가 팝업 (순환 패턴 / 스크린샷 OCR / AI 일괄 붙여넣기)
    if (showAddVirtualDialog) {
        ManualPersonAddDialog(
            defaultMonth = state.selectedMonth,
            onConfirmPattern = { name, seq, anchor ->
                viewModel.addVirtualPerson(name, seq, anchor)
                showAddVirtualDialog = false
            },
            onConfirmCells = { name, ym, cells ->
                viewModel.addManualCellsPerson(name, ym, cells)
                showAddVirtualDialog = false
            },
            onConfirmBatch = { imports ->
                viewModel.addManualBatch(imports)
                showAddVirtualDialog = false
            },
            onDismiss = { showAddVirtualDialog = false }
        )
    }

    // 수동 추가 이름 클릭 -> 순환 다이아 및 시작날짜 수정 팝업
    editingVirtualPerson?.let { person ->
        VirtualPersonDialog(
            title = "수동 추가 설정 수정",
            confirmLabel = "저장",
            initialName = person.name,
            initialAnchorDate = person.patternAnchorDate ?: "${state.selectedMonth}-01",
            initialPatternText = person.patternSequence.joinToString(", "),
            allowEmptyPattern = true,
            onConfirm = { name, seq, anchor ->
                viewModel.updateVirtualPerson(person.id, name, seq, anchor)
                editingVirtualPerson = null
            },
            onDismiss = { editingVirtualPerson = null }
        )
    }

    // 근무 셀 덮어쓰기 팝업 (실존 동료 + 수동 추가 공통)
    editingCell?.let { (person, dateIso, currentCell) ->
        EditCellDialog(
            personName = person.name,
            dateIso = dateIso,
            isVirtual = person.type == CompareEntryType.VIRTUAL_PERSON,
            initialDia = currentCell?.diaNo.orEmpty(),
            initialStart = currentCell?.startTime.orEmpty(),
            initialEnd = currentCell?.endTime.orEmpty(),
            isCurrentlyOverridden = currentCell?.isOverridden == true,
            onSave = { dia, st, en ->
                viewModel.updateCellOverride(person.id, dateIso, dia, st, en)
                editingCell = null
            },
            onReset = {
                viewModel.updateCellOverride(person.id, dateIso, "", "", "")
                editingCell = null
            },
            onDismiss = { editingCell = null }
        )
    }
}

@Composable
private fun dayHeaderStyle(d: LocalDate): Pair<String, Color> {
    val dowText = when (d.dayOfWeek) {
        DayOfWeek.MONDAY -> "월"
        DayOfWeek.TUESDAY -> "화"
        DayOfWeek.WEDNESDAY -> "수"
        DayOfWeek.THURSDAY -> "목"
        DayOfWeek.FRIDAY -> "금"
        DayOfWeek.SATURDAY -> "토"
        DayOfWeek.SUNDAY -> "일"
    }
    val dowColor = when (d.dayOfWeek) {
        DayOfWeek.SATURDAY -> Color(0xFF1E64D8)
        DayOfWeek.SUNDAY -> Color(0xFFD32F2F)
        else -> MaterialTheme.colorScheme.onSurface
    }
    return dowText to dowColor
}

@Composable
private fun CompareMatrixRow(
    name: String,
    badgeText: String?,
    isMySelf: Boolean,
    isVirtual: Boolean,
    dateList: List<LocalDate>,
    today: LocalDate,
    cellProvider: (LocalDate) -> CompareDayCell?,
    showCommuteTimes: Boolean,
    nameColWidth: androidx.compose.ui.unit.Dp,
    cellWidth: androidx.compose.ui.unit.Dp,
    rowHeight: androidx.compose.ui.unit.Dp,
    horizontalScroll: androidx.compose.foundation.ScrollState,
    onNameClick: (() -> Unit)?,
    onCellClick: ((LocalDate, CompareDayCell?) -> Unit)?,
    onRemoveClick: (() -> Unit)?
) {
    val rowBg = if (isMySelf) {
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f)
    } else {
        MaterialTheme.colorScheme.surface
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(rowBg)
    ) {
        // 좌측 고정 이름 셀
        Row(
            modifier = Modifier
                .width(nameColWidth)
                .height(rowHeight)
                .border(0.5.dp, MaterialTheme.colorScheme.outlineVariant)
                .padding(horizontal = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .then(
                        if (onNameClick != null) Modifier.clickable { onNameClick() }
                        else Modifier
                    )
            ) {
                Text(
                    text = name,
                    fontSize = 11.sp,
                    fontWeight = if (isMySelf) FontWeight.ExtraBold else FontWeight.Bold,
                    color = when {
                        isMySelf -> MaterialTheme.colorScheme.primary
                        isVirtual -> Color(0xFF6D28D9)
                        else -> MaterialTheme.colorScheme.onSurface
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (onRemoveClick != null) {
                Text(
                    text = "✕",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .clickable { onRemoveClick() }
                        .padding(horizontal = 3.dp, vertical = 2.dp)
                )
            }
        }

        // 우측 일자별 셀 목록 (공통 horizontalScroll 연동)
        Row(modifier = Modifier.horizontalScroll(horizontalScroll)) {
            dateList.forEach { d ->
                val cell = cellProvider(d)
                CompareCellBox(
                    cell = cell,
                    isToday = d == today,
                    isMySelfCell = isMySelf,
                    showCommuteTimes = showCommuteTimes,
                    cellWidth = cellWidth,
                    rowHeight = rowHeight,
                    onClick = if (onCellClick != null) { { onCellClick(d, cell) } } else null
                )
            }
        }
    }
}

@Composable
private fun CompareCellBox(
    cell: CompareDayCell?,
    isToday: Boolean,
    isMySelfCell: Boolean,
    showCommuteTimes: Boolean,
    cellWidth: androidx.compose.ui.unit.Dp,
    rowHeight: androidx.compose.ui.unit.Dp,
    onClick: (() -> Unit)?
) {
    val dia = cell?.diaNo.orEmpty()
    val isRest = dia == "S" || dia == "휴무" || dia == "비번" || dia == "연차"
    val isOverridden = cell?.isOverridden == true
    val cellBg = when {
        isOverridden -> Color(0xFFFEF3C7).copy(alpha = 0.55f)
        isToday -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = if (isMySelfCell) 0.45f else 0.3f)
        else -> Color.Transparent
    }

    Box(
        modifier = Modifier
            .width(cellWidth)
            .height(rowHeight)
            .background(cellBg)
            .border(0.5.dp, MaterialTheme.colorScheme.outlineVariant)
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier)
            .padding(horizontal = 2.dp)
    ) {
        if (isOverridden) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 3.dp, end = 3.dp)
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(Color(0xFFEF4444))
            )
        }
        Column(
            modifier = Modifier.align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy((-1).dp)
        ) {
            Text(
                text = dia.ifBlank { "-" },
                fontSize = 11.sp,
                lineHeight = 13.sp,
                fontWeight = if (isRest || dia.isBlank()) FontWeight.Medium else FontWeight.Bold,
                color = when {
                    dia.isBlank() -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
                    isRest -> MaterialTheme.colorScheme.onSurfaceVariant
                    else -> MaterialTheme.colorScheme.onSurface
                },
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (showCommuteTimes && cell != null) {
                val timeText = cell.formattedCommuteTime()
                if (timeText.isNotEmpty()) {
                    Text(
                        text = timeText,
                        fontSize = 8.5.sp,
                        lineHeight = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

@Composable
private fun ColleagueFilterDialog(
    people: List<ComparePerson>,
    hiddenPersonIds: Set<String>,
    onTogglePerson: (String) -> Unit,
    onSelectAll: () -> Unit,
    onDeselectAll: () -> Unit,
    onDismiss: () -> Unit
) {
    var query by remember { mutableStateOf("") }
    val filtered = remember(people, query) {
        val q = query.trim().lowercase()
        if (q.isEmpty()) people else people.filter { it.name.lowercase().contains(q) }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("🔍 승무원 표시 필터", fontWeight = FontWeight.ExtraBold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text("승무원 이름 검색...", fontSize = 12.sp) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    FilledTonalButton(
                        onClick = onSelectAll,
                        modifier = Modifier.weight(1f).height(32.dp),
                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text("전체 선택", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                    OutlinedButton(
                        onClick = onDeselectAll,
                        modifier = Modifier.weight(1f).height(32.dp),
                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text("전체 해제", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 220.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    filtered.forEach { person ->
                        val checked = person.id !in hiddenPersonIds
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onTogglePerson(person.id) }
                                .padding(vertical = 4.dp, horizontal = 2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = checked,
                                onCheckedChange = { onTogglePerson(person.id) },
                                modifier = Modifier.size(32.dp)
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                text = person.name,
                                fontSize = 12.5.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("닫기") }
        }
    )
}

@Composable
private fun AddColleagueDialog(
    isSearching: Boolean,
    searchResults: List<String>,
    onSearch: (String) -> Unit,
    onAddName: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var keyword by remember { mutableStateOf("") }
    var hasSearched by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("승무원 추가", fontWeight = FontWeight.ExtraBold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "동료 성명 또는 일부 단어를 입력해 검색 후 선택하세요.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = keyword,
                        onValueChange = { keyword = it },
                        placeholder = { Text("예: 홍길동 또는 홍", fontSize = 12.sp) },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    Button(
                        onClick = {
                            hasSearched = true
                            onSearch(keyword)
                        },
                        enabled = keyword.isNotBlank() && !isSearching
                    ) {
                        Text("검색", fontSize = 12.sp)
                    }
                }

                if (isSearching) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        Text("소속 승무원 검색 중...", fontSize = 12.sp)
                    }
                }

                if (searchResults.isNotEmpty()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 180.dp)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        searchResults.forEach { name ->
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onAddName(name) },
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(name, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                    Text("+ 추가", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                }
                            }
                        }
                    }
                } else if (hasSearched && !isSearching) {
                    Text(
                        text = "검색된 승무원이 없습니다. 성명을 다시 확인해 주세요.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color(0xFFFFFBEB),
                    border = BorderStroke(1.dp, Color(0xFFFDE68A))
                ) {
                    Text(
                        text = "💡 동명이인이나 Xcrew 자료구조 상 오류 발생 가능성이 있으므로, 지인의 근무표를 받아 [수동 추가]로 직접 추가하는 것을 권장합니다.",
                        fontSize = 11.sp,
                        lineHeight = 15.sp,
                        color = Color(0xFF92400E),
                        modifier = Modifier.padding(8.dp)
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("닫기") }
        }
    )
}

@Composable
private fun ManualPersonAddDialog(
    defaultMonth: YearMonth,
    onConfirmPattern: (name: String, sequence: List<String>, anchorDate: String) -> Unit,
    onConfirmCells: (name: String, month: YearMonth, cells: Map<String, CompareDayCell>) -> Unit,
    onConfirmBatch: (List<ParsedColleagueImport>) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()

    var selectedTab by remember { mutableStateOf(0) } // 0=순환패턴, 1=스크린샷OCR, 2=AI일괄

    // Tab 0: 순환 패턴
    var name by remember { mutableStateOf("") }
    var anchorDate by remember { mutableStateOf("${defaultMonth}-01") }
    var patternText by remember { mutableStateOf("") }
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            PatternFileParser.parseFromUri(context, uri)
                .onSuccess { tokens ->
                    patternText = tokens.joinToString(", ")
                    Toast.makeText(context, "${tokens.size}개 다이아 순서를 불러왔습니다.", Toast.LENGTH_SHORT).show()
                }
                .onFailure { err ->
                    Toast.makeText(context, err.message ?: "파일 파싱 실패", Toast.LENGTH_SHORT).show()
                }
        }
    }

    // Tab 1: 스크린샷 OCR
    var isOcrRunning by remember { mutableStateOf(false) }
    var ocrName by remember { mutableStateOf("") }
    var ocrMonth by remember { mutableStateOf(defaultMonth) }
    var ocrEntries by remember { mutableStateOf<List<OcrDayEntry>>(emptyList()) }
    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            isOcrRunning = true
            scope.launch {
                runCatching {
                    ScreenshotScheduleOcr.recognizeFromUri(context, uri, defaultMonth)
                }.onSuccess { result ->
                    isOcrRunning = false
                    ocrName = result.name
                    ocrMonth = result.yearMonth
                    ocrEntries = result.entries
                    if (result.entries.isEmpty()) {
                        Toast.makeText(context, "근무표 날짜/다이아를 인식하지 못했습니다. AI 일괄 탭을 이용해 보세요.", Toast.LENGTH_LONG).show()
                    } else {
                        Toast.makeText(context, "${result.entries.size}일치 인식 완료! 내용을 확인 후 등록하세요.", Toast.LENGTH_SHORT).show()
                    }
                }.onFailure { err ->
                    isOcrRunning = false
                    Toast.makeText(context, "OCR 실패: ${err.message ?: "알 수 없는 오류"}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    // Tab 2: AI 일괄 붙여넣기
    var aiPasteText by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("수동 추가", fontWeight = FontWeight.ExtraBold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    listOf("🔁 순환 패턴", "📷 사진 OCR", "🤖 AI 일괄").forEachIndexed { idx, label ->
                        val isSel = selectedTab == idx
                        Surface(
                            onClick = { selectedTab = idx },
                            shape = RoundedCornerShape(8.dp),
                            color = if (isSel) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                            border = BorderStroke(
                                1.dp,
                                if (isSel) MaterialTheme.colorScheme.primary.copy(alpha = 0.65f) else MaterialTheme.colorScheme.outlineVariant
                            ),
                            modifier = Modifier.weight(1f).height(34.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize().padding(horizontal = 4.dp)) {
                                Text(
                                    text = label,
                                    fontSize = 10.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isSel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                    textAlign = TextAlign.Center,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }

                when (selectedTab) {
                    0 -> {
                        Column(
                            modifier = Modifier.verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedTextField(
                                value = name,
                                onValueChange = { name = it },
                                label = { Text("표시 이름", fontSize = 12.sp) },
                                placeholder = { Text("예: 홍길동 또는 미배정 A조", fontSize = 12.sp) },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                            OutlinedTextField(
                                value = anchorDate,
                                onValueChange = { anchorDate = it },
                                label = { Text("패턴 1번 시작일 (yyyy-MM-dd)", fontSize = 12.sp) },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                            OutlinedButton(
                                onClick = {
                                    filePickerLauncher.launch(
                                        arrayOf(
                                            "text/plain",
                                            "text/csv",
                                            "text/comma-separated-values",
                                            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                                            "*/*"
                                        )
                                    )
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("📂 엑셀(.xlsx/.csv) 또는 메모장(.txt) 불러오기", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                            OutlinedTextField(
                                value = patternText,
                                onValueChange = { patternText = it },
                                label = { Text("다이아번호 순서 (쉼표·줄바꿈 구분)", fontSize = 12.sp) },
                                placeholder = { Text("예: 16035, 16315, ~, S, 16038", fontSize = 12.sp) },
                                minLines = 3,
                                maxLines = 5,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }

                    1 -> {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 360.dp)
                                .verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "Xcrew 당월승무근무표 스크린샷을 선택하면 기기 내 OCR로 날짜·다이아·출퇴근시각을 추출합니다. (오프라인 지원)",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                lineHeight = 15.sp
                            )
                            FilledTonalButton(
                                onClick = {
                                    imagePickerLauncher.launch(
                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                    )
                                },
                                enabled = !isOcrRunning,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                if (isOcrRunning) {
                                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                    Spacer(Modifier.width(6.dp))
                                    Text("이미지 분석 중...", fontSize = 11.5.sp)
                                } else {
                                    Text("📷 근무표 스크린샷 선택", fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                                }
                            }

                            if (ocrEntries.isNotEmpty()) {
                                OutlinedTextField(
                                    value = ocrName,
                                    onValueChange = { ocrName = it },
                                    label = { Text("인식된 이름 (${ocrMonth.year}.${String.format("%02d", ocrMonth.monthValue)})", fontSize = 11.5.sp) },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth()
                                )
                                ocrEntries.forEachIndexed { idx, entry ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "${entry.day}일",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.width(30.dp)
                                        )
                                        OutlinedTextField(
                                            value = entry.diaNo,
                                            onValueChange = { v ->
                                                ocrEntries = ocrEntries.toMutableList().also { it[idx] = entry.copy(diaNo = v) }
                                            },
                                            label = { Text("다이아", fontSize = 9.sp) },
                                            singleLine = true,
                                            modifier = Modifier.weight(1.2f)
                                        )
                                        OutlinedTextField(
                                            value = entry.startTime,
                                            onValueChange = { v ->
                                                ocrEntries = ocrEntries.toMutableList().also { it[idx] = entry.copy(startTime = v) }
                                            },
                                            label = { Text("출근", fontSize = 9.sp) },
                                            singleLine = true,
                                            modifier = Modifier.weight(1f)
                                        )
                                        OutlinedTextField(
                                            value = entry.endTime,
                                            onValueChange = { v ->
                                                ocrEntries = ocrEntries.toMutableList().also { it[idx] = entry.copy(endTime = v) }
                                            },
                                            label = { Text("퇴근", fontSize = 9.sp) },
                                            singleLine = true,
                                            modifier = Modifier.weight(1f)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    2 -> {
                        Column(
                            modifier = Modifier.verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "1) [AI 프롬프트 복사]를 눌러 Gemini/ChatGPT에 스크린샷과 함께 전송한 뒤, 2) 출력 결과를 붙여넣으면 여러 명도 한 번에 등록됩니다.",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                lineHeight = 15.sp
                            )
                            OutlinedButton(
                                onClick = {
                                    clipboard.setText(AnnotatedString(ColleagueScheduleImportParser.AI_EXTRACTION_PROMPT))
                                    Toast.makeText(context, "AI 추출 프롬프트가 복사되었습니다.", Toast.LENGTH_SHORT).show()
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("📋 AI 추출 프롬프트 복사", fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                            }
                            OutlinedTextField(
                                value = aiPasteText,
                                onValueChange = { aiPasteText = it },
                                label = { Text("AI 출력 결과 붙여넣기 (다중 인원 지원)", fontSize = 11.5.sp) },
                                placeholder = {
                                    Text("[이름: 윤영준 | 월: 2026-11]\n01: 연차\n02: 16035 | 09:53 | 21:05", fontSize = 11.sp)
                                },
                                minLines = 5,
                                maxLines = 8,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    when (selectedTab) {
                        0 -> {
                            val seq = PatternFileParser.parseTextOrCsv(patternText)
                            if (seq.isEmpty()) {
                                Toast.makeText(context, "다이아번호 패턴을 입력하거나 파일을 불러와 주세요.", Toast.LENGTH_SHORT).show()
                            } else {
                                onConfirmPattern(name.ifBlank { "수동 추가" }, seq, anchorDate.trim())
                            }
                        }
                        1 -> {
                            if (ocrEntries.isEmpty()) {
                                Toast.makeText(context, "먼저 근무표 스크린샷을 선택해 주세요.", Toast.LENGTH_SHORT).show()
                            } else {
                                val ymDash = String.format("%04d-%02d", ocrMonth.year, ocrMonth.monthValue)
                                val cellMap = ocrEntries.associate { e ->
                                    val iso = "$ymDash-${String.format("%02d", e.day)}"
                                    iso to CompareDayCell(
                                        date = iso,
                                        diaNo = e.diaNo.trim().ifEmpty { "-" },
                                        startTime = e.startTime.trim(),
                                        endTime = e.endTime.trim()
                                    )
                                }
                                onConfirmCells(ocrName.ifBlank { "수동 추가" }, ocrMonth, cellMap)
                            }
                        }
                        2 -> {
                            val parsed = ColleagueScheduleImportParser.parseBatchScheduleText(aiPasteText, defaultMonth)
                            if (parsed.isEmpty()) {
                                Toast.makeText(context, "인식된 근무표 블록이 없습니다. 형식을 확인해 주세요.", Toast.LENGTH_SHORT).show()
                            } else {
                                onConfirmBatch(parsed)
                            }
                        }
                    }
                }
            ) {
                Text(if (selectedTab == 2) "일괄 등록" else "추가")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("취소") }
        }
    )
}

@Composable
private fun VirtualPersonDialog(
    title: String,
    confirmLabel: String,
    initialName: String,
    initialAnchorDate: String,
    initialPatternText: String,
    allowEmptyPattern: Boolean,
    onConfirm: (name: String, sequence: List<String>, anchorDate: String) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var name by remember(initialName) { mutableStateOf(initialName) }
    var anchorDate by remember(initialAnchorDate) { mutableStateOf(initialAnchorDate) }
    var patternText by remember(initialPatternText) { mutableStateOf(initialPatternText) }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            PatternFileParser.parseFromUri(context, uri)
                .onSuccess { tokens ->
                    patternText = tokens.joinToString(", ")
                    Toast.makeText(context, "${tokens.size}개 다이아 순서를 불러왔습니다.", Toast.LENGTH_SHORT).show()
                }
                .onFailure { err ->
                    Toast.makeText(context, err.message ?: "파일 파싱 실패", Toast.LENGTH_SHORT).show()
                }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, fontWeight = FontWeight.ExtraBold) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("표시 이름", fontSize = 12.sp) },
                    placeholder = { Text("예: 미배정 A조", fontSize = 12.sp) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = anchorDate,
                    onValueChange = { anchorDate = it },
                    label = { Text("패턴 1번 시작일 (yyyy-MM-dd)", fontSize = 12.sp) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedButton(
                    onClick = {
                        filePickerLauncher.launch(
                            arrayOf(
                                "text/plain",
                                "text/csv",
                                "text/comma-separated-values",
                                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                                "*/*"
                            )
                        )
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("📂 엑셀(.xlsx/.csv) 또는 메모장(.txt) 불러오기", fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                }
                OutlinedTextField(
                    value = patternText,
                    onValueChange = { patternText = it },
                    label = { Text("다이아번호 순서 (쉼표·줄바꿈 구분)", fontSize = 12.sp) },
                    placeholder = { Text("예: 16035, 16315, ~, S, 16038", fontSize = 12.sp) },
                    minLines = 3,
                    maxLines = 5,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val seq = PatternFileParser.parseTextOrCsv(patternText)
                    if (!allowEmptyPattern && seq.isEmpty()) {
                        Toast.makeText(context, "다이아번호 패턴을 입력하거나 파일을 불러와 주세요.", Toast.LENGTH_SHORT).show()
                    } else {
                        onConfirm(name.ifBlank { "수동 추가" }, seq, anchorDate.trim())
                    }
                }
            ) {
                Text(confirmLabel)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("취소") }
        }
    )
}

@Composable
private fun EditCellDialog(
    personName: String,
    dateIso: String,
    isVirtual: Boolean,
    initialDia: String,
    initialStart: String,
    initialEnd: String,
    isCurrentlyOverridden: Boolean,
    onSave: (diaNo: String, startTime: String, endTime: String) -> Unit,
    onReset: () -> Unit,
    onDismiss: () -> Unit
) {
    var dia by remember { mutableStateOf(initialDia) }
    var start by remember { mutableStateOf(initialStart) }
    var end by remember { mutableStateOf(initialEnd) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("$personName ($dateIso) 수정", fontWeight = FontWeight.ExtraBold, fontSize = 16.sp) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = dia,
                    onValueChange = { dia = it },
                    label = { Text("다이아번호 / 근무", fontSize = 12.sp) },
                    placeholder = { Text("예: 16035, S, 연차", fontSize = 12.sp) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = start,
                        onValueChange = { start = it },
                        label = { Text("출근(선택)", fontSize = 11.sp) },
                        placeholder = { Text("09:43", fontSize = 11.sp) },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = end,
                        onValueChange = { end = it },
                        label = { Text("퇴근(선택)", fontSize = 11.sp) },
                        placeholder = { Text("19:56", fontSize = 11.sp) },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }
                if (isCurrentlyOverridden) {
                    OutlinedButton(
                        onClick = onReset,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = if (isVirtual) "↩ 기본 순환 패턴으로 복원" else "↩ 원래 데이터로 복원",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = { onSave(dia, start, end) }) {
                Text("저장")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("취소") }
        }
    )
}
