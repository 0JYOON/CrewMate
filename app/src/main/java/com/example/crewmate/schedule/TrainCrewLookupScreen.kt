package com.example.crewmate.schedule

import android.app.DatePickerDialog
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import com.example.crewmate.data.remote.xcrew.DiaDetailDataSource
import com.example.crewmate.schedule.WorkSchedule

private val NavyBadgeColor = Color(0xFF0F2C59)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrainCrewLookupScreen(
    viewModel: TrainCrewLookupViewModel,
    diaDetailDataSource: DiaDetailDataSource? = null, // 다이아 상세조회를 위한 데이터 통신 객체
    onBack: () -> Unit = {}
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    // 바텀시트와 다이아 번호 클릭을 관리하는 상태 변수들
    var selectedDiaNo by remember { mutableStateOf<String?>(null) }
    val bottomSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val detailCache = remember { mutableStateMapOf<String, DiaDetailLoadState>() }

    TrainCrewLookupContent(
        state = state,
        onDateClick = {
            DatePickerDialog(
                context,
                { _, year, month, day -> viewModel.setDate(LocalDate.of(year, month + 1, day)) },
                state.date.year,
                state.date.monthValue - 1,
                state.date.dayOfMonth,
            ).show()
        },
        onTrainNumberChange = viewModel::setTrainNumber,
        onSearch = viewModel::search,
        onDiaClick = { diaNo -> selectedDiaNo = diaNo } // 다이아 번호 클릭 시 팝업 띄우기
    )

    // 선택된 다이아 번호가 존재하면 바텀시트를 그립니다.
    if (selectedDiaNo != null) {
        ModalBottomSheet(
            onDismissRequest = { selectedDiaNo = null },
            sheetState = bottomSheetState,
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            // ScheduleDetailSheet 가 요구하는 형태에 맞춰 가짜 WorkSchedule 객체를 만듭니다.
            val mockSchedule = WorkSchedule(
                workDate = state.date.format(DateTimeFormatter.ofPattern("yyyyMMdd")),
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
                date = state.date,
                schedules = listOf(mockSchedule),
                deviceEvents = emptyList(), // 승무원 조회에서는 캘린더 일정이 불필요하므로 빈 리스트
                diaDetailDataSource = diaDetailDataSource,
                detailCache = detailCache,
                showTabs = false
            )
        }
    }
}

@Composable
private fun TrainCrewLookupContent(
    state: TrainCrewLookupUiState,
    onDateClick: () -> Unit,
    onTrainNumberChange: (String) -> Unit,
    onSearch: () -> Unit,
    onDiaClick: (String) -> Unit = {} // 클릭 이벤트를 받기 위한 파라미터 추가
) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.Top,
    ) {
        Text(
            text = "열차 승무원 조회",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.ExtraBold,
            color = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.height(14.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        ) {
            Column(Modifier.fillMaxWidth().padding(14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(modifier = Modifier.weight(1f)) {
                        OutlinedTextField(
                            value = state.date.format(DISPLAY_DATE),
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
                                .clickable(enabled = !state.isLoading, onClick = onDateClick),
                        )
                    }
                    OutlinedTextField(
                        value = state.trainNumber,
                        onValueChange = onTrainNumberChange,
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
                            onSearch = { onSearch() },
                            onDone = { onSearch() },
                        ),
                        singleLine = true,
                        enabled = !state.isLoading,
                    )
                }

                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = onSearch,
                    enabled = !state.isLoading,
                    modifier = Modifier.fillMaxWidth().height(46.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary),
                ) {
                    Text(
                        text = if (state.isLoading) "조회 중..." else "승무원 조회",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                    )
                }
            }
        }

        if (state.isLoading) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(24.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(12.dp))
                Text("날짜의 다이아와 상세 정보를 확인하고 있습니다.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        state.errorMessage?.let {
            Spacer(Modifier.height(12.dp))
            Text(it, color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Medium)
        }

        state.report?.let { report ->
            val activeResults = remember(report) {
                report.results.sortedBy { it.segment.dptTm.toTimeSortKey() }
            }

            Spacer(Modifier.height(14.dp))

            if (report.failedDiaCount > 0) {
                Text(
                    text = "서버 지연으로 다이아 ${report.failedDiaCount}건의 상세 조회가 누락되었습니다.",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                )
                Spacer(Modifier.height(8.dp))
            }

            if (activeResults.isEmpty()) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.surface,
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    val emptyMessage = if (report.failedDiaCount > 0) {
                        "서버 요청 지연으로 조회가 제한되었습니다.\n잠시 후 다시 시도해 주세요."
                    } else {
                        "해당 열차의 운전·입환 승무 정보가 없습니다."
                    }
                    Text(
                        text = emptyMessage,
                        modifier = Modifier.padding(18.dp),
                        color = if (report.failedDiaCount > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                TrainCrewResultTable(
                    results = activeResults,
                    fallbackTrainNo = report.normalizedTrainNumber,
                    reportPjtDt = report.pjtDt,
                    onDiaClick = onDiaClick
                )
            }
        }

        Spacer(Modifier.height(24.dp))
        Text(
            text = "• 조회 결과는 실제와 다를 수 있으므로 참고용으로만 활용해 주세요.\n• 조회가 원활하지 않은 경우, 우측 상단의 [재접속] 버튼을 눌러보세요.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
        )
    }
}

@Composable
private fun TrainCrewResultTable(
    results: List<TrainCrewSearchResult>,
    fallbackTrainNo: String,
    reportPjtDt: String,
    isDeadhead: Boolean = false,
    onDiaClick: (String) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isDeadhead) 0.dp else 2.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(Modifier.fillMaxWidth()) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant).padding(vertical = 6.dp, horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("다이아", modifier = Modifier.weight(1.3f), fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("열번", modifier = Modifier.weight(1f), fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("운행 구간", modifier = Modifier.weight(2.2f), fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("승무원", modifier = Modifier.weight(1.2f), fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, thickness = 1.dp)

            results.forEachIndexed { index, result ->
                val segment = result.segment
                val isPreviousDay = result.pjtDt != reportPjtDt
                val trainNo = segment.trnNo?.trim()?.takeIf(String::isNotEmpty) ?: fallbackTrainNo
                val dptStn = segment.dptStnNm?.trim()?.takeIf(String::isNotEmpty) ?: "-"
                val arvStn = segment.arvStnNm?.trim()?.takeIf(String::isNotEmpty) ?: "-"
                val dptTime = segment.dptTm.asClock() ?: "-"
                val arvTime = segment.arvTm.asClock() ?: "-"
                val crewText = result.crewMembers.joinToString("·").ifEmpty { "정보 없음" }

                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp, horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // 다이아
                    Column(modifier = Modifier.weight(1.3f)) {
                        Text(
                            text = "${result.pdiaNo} DIA",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.clickable { onDiaClick(result.pdiaNo) }
                        )
                        if (isPreviousDay) {
                            Spacer(Modifier.height(2.dp))
                            Text(
                                text = "전일 출근",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFD32F2F),
                                modifier = Modifier.background(Color(0xFFFFEBEE), RoundedCornerShape(2.dp)).padding(horizontal = 3.dp, vertical = 1.dp)
                            )
                        }
                    }
                    // 열번
                    Text(trainNo, modifier = Modifier.weight(1f), fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.onSurface)
                    // 운행 구간
                    Column(modifier = Modifier.weight(2.2f)) {
                        Text("$dptStn $dptTime", fontSize = 11.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface)
                        Text("→ $arvStn $arvTime", fontSize = 11.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    // 승무원
                    Text(crewText, modifier = Modifier.weight(1.2f), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                }
                
                if (index < results.lastIndex) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, thickness = 0.5.dp)
                }
            }
        }
    }
}




private fun String?.toTimeSortKey(): Int {
    val digits = this?.trim()?.replace(":", "") ?: return Int.MAX_VALUE
    if (digits.length != 4 || !digits.all(Char::isDigit)) return Int.MAX_VALUE
    return digits.toIntOrNull() ?: Int.MAX_VALUE
}

private fun String?.asClock(): String? {
    val text = this?.trim()?.replace(":", "") ?: return null
    if (text.length != 4 || !text.all(Char::isDigit)) return null
    val hour = text.substring(0, 2).toIntOrNull() ?: return null
    val minute = text.substring(2, 4).toIntOrNull() ?: return null
    if (hour !in 0..47 || minute !in 0..59) return null
    return "%02d:%02d".format(Locale.ROOT, hour, minute)
}

private val DISPLAY_DATE = DateTimeFormatter.ofPattern("yyyy.MM.dd", Locale.ROOT)

@Preview(showBackground = true)
@Composable
private fun TrainCrewLookupPreview() {
    TrainCrewLookupContent(TrainCrewLookupUiState(), {}, {}, {})
}