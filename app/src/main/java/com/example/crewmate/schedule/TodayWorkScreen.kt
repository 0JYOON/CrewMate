package com.example.crewmate.schedule

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.crewmate.data.remote.xcrew.AttendanceStatusDto
import com.example.crewmate.data.remote.xcrew.toGtwcGubunText
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun TodayWorkScreen(viewModel: TodayWorkViewModel, onBack: () -> Unit = {}) {
    val state by viewModel.uiState.collectAsState()
    TodayWorkContent(state, viewModel::retry)
}

@Composable
private fun TodayWorkContent(
    state: TodayWorkUiState,
    onRetry: () -> Unit,
) {
    val today = remember { LocalDate.now() }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "오늘의 근무",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.primary,
                )
                if (state is TodayWorkUiState.Success) {
                    val result = state.result
                    if (!result.isToday) {
                        val formatted = result.workDate.format(DateTimeFormatter.ofPattern("MM.dd"))
                        Surface(
                            color = Color(0xFFFEE2E2),
                            shape = RoundedCornerShape(999.dp)
                        ) {
                            Text(
                                text = "$formatted 근무",
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFC81E1E)
                            )
                        }
                    }
                }
            }
            
            Surface(
                color = MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(999.dp),
                shadowElevation = 1.dp,
            ) {
                Text(
                    text = today.format(DISPLAY_DATE),
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
        Spacer(Modifier.height(16.dp))

        when (state) {
            TodayWorkUiState.Loading -> {
                Column(
                    Modifier.fillMaxWidth().padding(36.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    CircularProgressIndicator(color = Color(0xFF1E6FB8))
                    Spacer(Modifier.height(14.dp))
                    Text("근무와 다이아 상세를 확인하고 있습니다.", color = Color(0xFF64748B))
                }
            }
            TodayWorkUiState.NoUpcomingWork -> {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = Color.White,
                    shape = RoundedCornerShape(16.dp),
                ) {
                    Text(
                        text = "예정된 근무가 없습니다.",
                        modifier = Modifier.padding(20.dp),
                        style = MaterialTheme.typography.bodyLarge,
                        color = Color(0xFF64748B),
                    )
                }
            }
            is TodayWorkUiState.Error -> {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = Color.White,
                    shape = RoundedCornerShape(16.dp),
                ) {
                    Column(Modifier.padding(20.dp)) {
                        Text(state.message, color = MaterialTheme.colorScheme.error)
                        Spacer(Modifier.height(10.dp))
                        Button(onClick = onRetry) { Text("다시 시도") }
                    }
                }
            }
            is TodayWorkUiState.Success -> {
                val result = state.result
                result.dias.forEachIndexed { index, item ->
                    DiaDetailCard(
                        schedule = item.schedule,
                        detail = item.detail,
                        workDate = result.workDate,
                    )
                    if (index < result.dias.lastIndex) Spacer(Modifier.height(14.dp))
                }
                
                Spacer(Modifier.height(16.dp))
                AttendanceStatusSection(result.attendanceStatusList)
            }
        }
    }
}

@Composable
private fun AttendanceStatusSection(attendanceList: List<AttendanceStatusDto>) {
    var isExpanded by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { isExpanded = !isExpanded },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "출무현황 확인하기",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = if (isExpanded) "▲" else "▼",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            AnimatedVisibility(visible = isExpanded) {
                Column(Modifier.padding(top = 16.dp)) {
                    if (attendanceList.isEmpty()) {
                        Text("조회된 출무현황이 없습니다.", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else {
                        val groupedByDate = attendanceList.groupBy { it.pjtDt }
                        groupedByDate.entries.forEachIndexed { dateIdx, (pjtDt, items) ->
                            if (dateIdx > 0) {
                                HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), color = MaterialTheme.colorScheme.outlineVariant)
                            }
                            if (pjtDt != null && pjtDt.length == 8) {
                                val formattedDate = "${pjtDt.substring(0, 4)}.${pjtDt.substring(4, 6)}.${pjtDt.substring(6, 8)}"
                                Text(
                                    text = "사업일자: $formattedDate",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(bottom = 8.dp)
                                )
                            }
                            items.forEachIndexed { itemIdx, item ->
                                if (itemIdx > 0) Spacer(Modifier.height(8.dp))
                                Surface(
                                    modifier = Modifier.fillMaxWidth(),
                                    color = MaterialTheme.colorScheme.surfaceVariant,
                                    shape = RoundedCornerShape(8.dp),
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                                ) {
                                    Column(Modifier.padding(12.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text(
                                                text = "열차번호: ${item.trnNo ?: "-"}",
                                                fontSize = 14.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                            val gtwcText = item.toGtwcGubunText()
                                            Text(
                                                text = gtwcText,
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = if (gtwcText == "적합성완료" || gtwcText == "도중출무완료") Color(0xFF0D9488) else MaterialTheme.colorScheme.primary
                                            )
                                        }
                                        Spacer(Modifier.height(4.dp))
                                        Text("출무소속: ${item.gtwcEpctBlgNm ?: "-"}", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        
                                        val tm = formatActualTime(item.gtwcEpctTm) ?: (item.gtwcEpctTm ?: "-")
                                        Text("예정출무시각: $tm", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)

                                        val actlTm = formatActualTime(item.actualTime)
                                        if (actlTm != null) {
                                            Text("실제출무시각: $actlTm", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color(0xFF0D9488))
                                        }

                                        Text("열차상태: ${item.trnSttNm ?: "-"}", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private val DISPLAY_DATE: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy.MM.dd", Locale.ROOT)

private fun formatActualTime(raw: String?): String? {
    val s = raw?.trim() ?: return null
    if (s.isEmpty() || s == "null" || s == "-") return null

    if (s.contains(":")) {
        val parts = s.split(" ").last().split(":")
        if (parts.size >= 2) {
            val h = parts[0].filter(Char::isDigit).padStart(2, '0')
            val m = parts[1].filter(Char::isDigit).padStart(2, '0')
            if (h.isNotEmpty() && m.isNotEmpty()) return "$h:$m"
        }
    }

    val digits = s.filter(Char::isDigit)
    if (digits.length >= 4) {
        val timePart = if (digits.length >= 12) {
            digits.substring(8, 12)
        } else {
            digits.takeLast(4)
        }
        return "${timePart.substring(0, 2)}:${timePart.substring(2, 4)}"
    }

    return s
}

@Preview(showBackground = true)
@Composable
private fun TodayWorkContentPreview() {
    TodayWorkContent(TodayWorkUiState.NoUpcomingWork, onRetry = {})
}