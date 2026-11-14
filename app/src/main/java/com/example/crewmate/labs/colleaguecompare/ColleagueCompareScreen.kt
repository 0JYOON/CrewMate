package com.example.crewmate.labs.colleaguecompare

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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

    var showOptionsDialog by remember { mutableStateOf(false) }
    var showAddColleagueDialog by remember { mutableStateOf(false) }
    var showAddVirtualDialog by remember { mutableStateOf(false) }
    var editingVirtualCell by remember { mutableStateOf<Triple<ComparePerson, String, CompareDayCell?>?>(null) }

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
                    IconButton(onClick = { viewModel.previousMonth() }, modifier = Modifier.size(32.dp)) {
                        Text("◀", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                    Text(
                        text = "${state.selectedMonth.year}년 ${state.selectedMonth.monthValue}월",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    IconButton(onClick = { viewModel.nextMonth() }, modifier = Modifier.size(32.dp)) {
                        Text("▶", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                }
                FilledTonalButton(
                    onClick = { viewModel.refreshAllOnline() },
                    enabled = !state.isLoading,
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                    modifier = Modifier.height(30.dp)
                ) {
                    if (state.isLoading) {
                        CircularProgressIndicator(modifier = Modifier.size(13.dp), strokeWidth = 2.dp)
                    } else {
                        Text("새로고침", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        Spacer(Modifier.height(6.dp))

        // 액션 버튼 바: 승무원 추가 / 가상 인물 / 표시 옵션
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            FilledTonalButton(
                onClick = { showAddColleagueDialog = true },
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
                Text("+ 가상 인물", fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1)
            }
            OutlinedButton(
                onClick = { showOptionsDialog = true },
                modifier = Modifier.height(34.dp),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp)
            ) {
                Text("⚙️ 옵션", fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1)
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

        val cellWidth = if (state.displayOptions.showCommuteTimes) 74.dp else 54.dp
        val rowHeight = if (state.displayOptions.showCommuteTimes) 50.dp else 38.dp
        val nameColWidth = 88.dp

        // 비교 그리드 카드 (좌측 이름 고정 + 우측 일자별 가로 스크롤)
        Surface(
            modifier = Modifier.fillMaxWidth().weight(1f, fill = false),
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
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
                    // 1. 내 근무표 (고정 최상단)
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
                            onCellClick = null,
                            onRemoveClick = null
                        )
                    }

                    // 2. 추가된 동료 및 가상 인물
                    items(items = state.people, key = { it.id }) { person ->
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
                            onCellClick = if (isVirtual) {
                                { d, cell -> editingVirtualCell = Triple(person, d.toString(), cell) }
                            } else null,
                            onRemoveClick = { viewModel.removePerson(person.id) }
                        )
                    }
                }
            }
        }

        if (state.people.isEmpty()) {
            Spacer(Modifier.height(10.dp))
            Text(
                text = "💡 상단의 [+ 승무원 추가]로 동료 이름을 검색하거나 [+ 가상 인물]로 미배정 패턴(엑셀·메모장 파일 포함)을 추가해 한눈에 비교해 보세요.",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 16.sp
            )
        } else if (state.people.any { it.type == CompareEntryType.VIRTUAL_PERSON }) {
            Spacer(Modifier.height(6.dp))
            Text(
                text = "💡 가상 인물 칸을 터치하면 해당 날짜의 다이아 내용을 직접 덮어씌울 수 있습니다.",
                fontSize = 10.5.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }

    // 옵션 팝업: 출퇴근시각 표출 여부
    if (showOptionsDialog) {
        AlertDialog(
            onDismissRequest = { showOptionsDialog = false },
            title = { Text("표시 옵션", fontWeight = FontWeight.ExtraBold) },
            text = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("출퇴근시각 함께 표출", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Text(
                            "끄면 다이아번호만 간결하게 표시합니다.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = state.displayOptions.showCommuteTimes,
                        onCheckedChange = { viewModel.setShowCommuteTimes(it) }
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showOptionsDialog = false }) {
                    Text("닫기", fontWeight = FontWeight.Bold)
                }
            }
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

    // 가상 인물(미배정 패턴) 추가 팝업
    if (showAddVirtualDialog) {
        AddVirtualPersonDialog(
            defaultAnchorDate = "${state.selectedMonth}-01",
            onAdd = { name, seq, anchor ->
                viewModel.addVirtualPerson(name, seq, anchor)
                showAddVirtualDialog = false
            },
            onDismiss = { showAddVirtualDialog = false }
        )
    }

    // 가상 인물 셀 덮어쓰기 팝업
    editingVirtualCell?.let { (person, dateIso, currentCell) ->
        EditVirtualCellDialog(
            personName = person.name,
            dateIso = dateIso,
            initialDia = currentCell?.diaNo.orEmpty(),
            initialStart = currentCell?.startTime.orEmpty(),
            initialEnd = currentCell?.endTime.orEmpty(),
            isCurrentlyOverridden = currentCell?.isOverridden == true,
            onSave = { dia, st, en ->
                viewModel.updateVirtualCellOverride(person.id, dateIso, dia, st, en)
                editingVirtualCell = null
            },
            onResetToPattern = {
                viewModel.updateVirtualCellOverride(person.id, dateIso, "", "", "")
                editingVirtualCell = null
            },
            onDismiss = { editingVirtualCell = null }
        )
    }
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
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = name,
                    fontSize = 11.sp,
                    fontWeight = if (isMySelf) FontWeight.ExtraBold else FontWeight.Bold,
                    color = if (isMySelf) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (badgeText != null) {
                    Text(
                        text = badgeText,
                        fontSize = 8.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF6D28D9),
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(Color(0xFFEDE9FE))
                            .padding(horizontal = 3.dp, vertical = 0.5.dp)
                    )
                }
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
                val isToday = d == today
                val dia = cell?.diaNo.orEmpty()
                val isRest = dia == "S" || dia == "휴무" || dia == "비번" || dia == "연차"
                val cellBg = when {
                    cell?.isOverridden == true -> Color(0xFFFEF3C7).copy(alpha = 0.65f)
                    isToday -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                    else -> Color.Transparent
                }

                Box(
                    modifier = Modifier
                        .width(cellWidth)
                        .height(rowHeight)
                        .background(cellBg)
                        .border(0.5.dp, MaterialTheme.colorScheme.outlineVariant)
                        .then(
                            if (isVirtual && onCellClick != null) {
                                Modifier.clickable { onCellClick(d, cell) }
                            } else Modifier
                        )
                        .padding(horizontal = 2.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = dia.ifBlank { "-" },
                            fontSize = 11.sp,
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
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }
            }
        }
    }
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

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("승무원 추가", fontWeight = FontWeight.ExtraBold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "동료 성명 또는 일부 단어를 입력해 검색하세요.",
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
                        onClick = { onSearch(keyword) },
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
                } else if (keyword.isNotBlank() && !isSearching) {
                    OutlinedButton(
                        onClick = { onAddName(keyword.trim()) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("'${keyword.trim()}' 이름으로 바로 조회·추가", fontSize = 12.sp)
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
private fun AddVirtualPersonDialog(
    defaultAnchorDate: String,
    onAdd: (name: String, sequence: List<String>, anchorDate: String) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var name by remember { mutableStateOf("") }
    var anchorDate by remember { mutableStateOf(defaultAnchorDate) }
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

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("가상 인물(미배정 패턴) 추가", fontWeight = FontWeight.ExtraBold) },
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
                    if (seq.isEmpty()) {
                        Toast.makeText(context, "다이아번호 패턴을 입력하거나 파일을 불러와 주세요.", Toast.LENGTH_SHORT).show()
                    } else {
                        onAdd(name.ifBlank { "가상 인물" }, seq, anchorDate.trim())
                    }
                }
            ) {
                Text("추가")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("취소") }
        }
    )
}

@Composable
private fun EditVirtualCellDialog(
    personName: String,
    dateIso: String,
    initialDia: String,
    initialStart: String,
    initialEnd: String,
    isCurrentlyOverridden: Boolean,
    onSave: (diaNo: String, startTime: String, endTime: String) -> Unit,
    onResetToPattern: () -> Unit,
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
                    TextButton(onClick = onResetToPattern, modifier = Modifier.align(Alignment.End)) {
                        Text("기본 순환 패턴으로 복원", fontSize = 11.5.sp)
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
