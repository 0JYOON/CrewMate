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
import androidx.compose.ui.platform.LocalContext
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
fun TodayWorkScreen(
    viewModel: TodayWorkViewModel,
    onNavigateToAlarmSettings: () -> Unit = {},
    onBack: () -> Unit = {}
) {
    val state by viewModel.uiState.collectAsState()
    TodayWorkContent(state, onNavigateToAlarmSettings, viewModel::retry)
}

@Composable
private fun TodayWorkContent(
    state: TodayWorkUiState,
    onNavigateToAlarmSettings: () -> Unit,
    onRetry: () -> Unit,
) {
    val overrideDate by com.example.crewmate.util.AppDateProvider.overrideDate.collectAsState()
    val today = overrideDate ?: com.example.crewmate.util.AppDateProvider.today

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
    ) {
        if (overrideDate != null) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 10.dp)
                    .clickable { com.example.crewmate.util.AppDateProvider.resetToRealToday() },
                color = Color(0xFFDC2626),
                shape = RoundedCornerShape(8.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "🧪 가상 날짜 모드: ${overrideDate?.format(DateTimeFormatter.ofPattern("yyyy-MM-dd (E)", Locale.KOREAN))}",
                        fontWeight = FontWeight.ExtraBold,
                        color = Color.White,
                        fontSize = 12.sp
                    )
                    Text(
                        text = "↺ 실제 오늘로 복구",
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        fontSize = 11.sp
                    )
                }
            }
        }
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
                            color = MaterialTheme.colorScheme.errorContainer,
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                text = "$formatted 근무",
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                    }
                }
            }
            
            Surface(
                color = MaterialTheme.colorScheme.secondaryContainer,
                shape = RoundedCornerShape(8.dp),
            ) {
                Text(
                    text = today.format(DISPLAY_DATE),
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
            }
        }
        Spacer(Modifier.height(12.dp))

        androidx.compose.animation.Crossfade(
            targetState = state,
            animationSpec = androidx.compose.animation.core.tween(durationMillis = 300, easing = androidx.compose.animation.core.FastOutSlowInEasing)
        ) { targetState ->
            when (targetState) {
                TodayWorkUiState.Loading -> {
                    Column(
                        Modifier.fillMaxWidth().padding(40.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.height(16.dp))
                        Text("근무와 다이아 상세를 확인하고 있습니다.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                TodayWorkUiState.NoUpcomingWork -> {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = MaterialTheme.colorScheme.surface,
                        shape = MaterialTheme.shapes.large,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        Text(
                            text = "예정된 근무가 없습니다.",
                            modifier = Modifier.padding(20.dp),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                is TodayWorkUiState.Error -> {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = MaterialTheme.colorScheme.surface,
                        shape = MaterialTheme.shapes.large,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        Column(Modifier.padding(20.dp)) {
                            Text(targetState.message, color = MaterialTheme.colorScheme.error)
                            Spacer(Modifier.height(10.dp))
                            Button(onClick = onRetry) { Text("다시 시도") }
                        }
                    }
                }
                is TodayWorkUiState.Success -> {
                    Column {
                        val result = targetState.result
                        result.dias.forEachIndexed { index, item ->
                            DiaDetailCard(
                                schedule = item.schedule,
                                detail = item.detail,
                                workDate = result.workDate,
                            )
                            if (index < result.dias.lastIndex) Spacer(Modifier.height(14.dp))
                        }
                        
                        Spacer(Modifier.height(16.dp))
                        AttendanceStatusSection(result.attendanceStatusList, targetState, onNavigateToAlarmSettings)
                    }
                }
            }
        }
    }
}

@Composable
private fun AttendanceStatusSection(
    attendanceList: List<AttendanceStatusDto>,
    state: TodayWorkUiState,
    onNavigateToAlarmSettings: () -> Unit
) {
    var isExpanded by remember { mutableStateOf(false) }

    // Move alarm chip right above Attendance Status Section
    if (state is TodayWorkUiState.Success) {
        val context = LocalContext.current
        var upcomingAlarmsCount by remember { androidx.compose.runtime.mutableIntStateOf(0) }
        var isAlarmEnabled by remember { mutableStateOf(false) }
        var showUpcomingAlarmsDialog by remember { mutableStateOf(false) }
        var upcomingAlarmsList by remember { mutableStateOf<List<com.example.crewmate.alarm.WorkAlarmItem>>(emptyList()) }

        androidx.compose.runtime.LaunchedEffect(state) {
            val db = com.example.crewmate.database.CrewMateDatabase.getInstance(context)
            val alarmStore = com.example.crewmate.alarm.WorkAlarmSettingsStore(context)
            val alarmSettings = alarmStore.loadSettings()
            isAlarmEnabled = alarmSettings.alarmEnabled
            
            if (alarmSettings.alarmEnabled) {
                val parser = com.example.crewmate.data.remote.xcrew.DiaDetailParser()
                val allSegments = mutableListOf<com.example.crewmate.data.remote.xcrew.DiaDetailSegment>()
                state.result.dias.forEach { dia ->
                    val key = "${state.result.workDate.format(DateTimeFormatter.ofPattern("yyyyMMdd"))}|${dia.schedule.diaNo.orEmpty()}"
                    val cachedDetail = db.diaDetailDao().getDiaDetail(key)
                    if (cachedDetail != null && cachedDetail.detailJson.isNotBlank()) {
                        runCatching {
                            val detailResponse = parser.parse(cachedDetail.detailJson)
                            allSegments.addAll(detailResponse.segments)
                        }
                    }
                }
                val upcoming = com.example.crewmate.alarm.WorkAlarmRuleEvaluator.evaluate(
                    segments = allSegments,
                    workDate = state.result.workDate,
                    settings = alarmSettings,
                    attendanceStatuses = state.result.attendanceStatusList
                )
                upcomingAlarmsCount = upcoming.size
                upcomingAlarmsList = upcoming
            }
        }

        if (isAlarmEnabled) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showUpcomingAlarmsDialog = true },
                color = MaterialTheme.colorScheme.tertiaryContainer,
                shape = MaterialTheme.shapes.medium
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "⏰ 오늘의 예정 정밀 알람 ${upcomingAlarmsCount}건",
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                        fontSize = 13.sp
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
        }

        if (showUpcomingAlarmsDialog) {
            androidx.compose.material3.AlertDialog(
                onDismissRequest = { showUpcomingAlarmsDialog = false },
                containerColor = MaterialTheme.colorScheme.surface,
                shape = MaterialTheme.shapes.extraLarge,
                tonalElevation = 6.dp,
                title = { Text("오늘의 예정 알람 (${upcomingAlarmsList.size}건)", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary) },
                text = {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (upcomingAlarmsList.isEmpty()) {
                            Text("오늘 울릴 예정인 알람이 없거나 출무 정보가 조회되지 않았습니다.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        } else {
                            upcomingAlarmsList.forEach { item ->
                                Surface(
                                    color = MaterialTheme.colorScheme.surfaceVariant,
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                        Text(
                                            text = "⏰ ${item.triggerDateTime.format(DateTimeFormatter.ofPattern("MM.dd (E) HH:mm", Locale.KOREAN))} | ${item.ruleTitle}",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.sp,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                        Text(
                                            text = item.message,
                                            fontSize = 12.sp,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    androidx.compose.material3.TextButton(onClick = { showUpcomingAlarmsDialog = false }) {
                        Text("닫기", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    }
                }
            )
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(Modifier.fillMaxWidth().padding(14.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(
                        interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                        indication = null
                    ) { isExpanded = !isExpanded },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "출무현황 확인하기",
                    fontSize = 15.sp,
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
    TodayWorkContent(TodayWorkUiState.NoUpcomingWork, {}, onRetry = {})
}