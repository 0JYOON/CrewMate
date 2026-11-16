package com.example.crewmate.schedule

import androidx.compose.foundation.layout.Box

import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.TextButton
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
    val isRefreshingAttendance by viewModel.isRefreshingAttendance.collectAsState()
    val context = LocalContext.current
    TodayWorkContent(
        state = state,
        onNavigateToAlarmSettings = onNavigateToAlarmSettings,
        onRetry = viewModel::retry,
        isRefreshingAttendance = isRefreshingAttendance,
        onRefreshAttendance = {
            val prefs = context.getSharedPreferences("crewmate_prefs", Context.MODE_PRIVATE)
            if (prefs.getBoolean("is_offline_mode", false)) {
                android.widget.Toast.makeText(context, "오프라인 모드에서는 출무현황을 새로고침할 수 없습니다.", android.widget.Toast.LENGTH_SHORT).show()
            } else {
                viewModel.refreshAttendanceOnly { _, message ->
                    android.widget.Toast.makeText(context, message, android.widget.Toast.LENGTH_SHORT).show()
                }
            }
        }
    )
}

@Composable
private fun TodayWorkContent(
    state: TodayWorkUiState,
    onNavigateToAlarmSettings: () -> Unit,
    onRetry: () -> Unit,
    isRefreshingAttendance: Boolean = false,
    onRefreshAttendance: () -> Unit = {},
) {
    val overrideDate by com.example.crewmate.util.AppDateProvider.overrideDate.collectAsState()
    val today = overrideDate ?: com.example.crewmate.util.AppDateProvider.today
    val scrollState = rememberScrollState()
    val currentContext = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val prefs = remember { currentContext.getSharedPreferences("crewmate_prefs", Context.MODE_PRIVATE) }
    var isOfflineMode by remember { mutableStateOf(prefs.getBoolean("is_offline_mode", false)) }
    var autoOfflineEnabled by remember { mutableStateOf(com.example.crewmate.alarm.AutoOfflineManager.isEnabled(currentContext)) }
    var isReturnSkipped by remember { mutableStateOf(prefs.getBoolean(com.example.crewmate.alarm.AutoOfflineManager.PREF_RETURN_SKIPPED, false)) }

    DisposableEffect(prefs) {
        val listener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { sp, key ->
            if (key == "is_offline_mode") isOfflineMode = sp.getBoolean("is_offline_mode", false)
            if (key == com.example.crewmate.alarm.AutoOfflineManager.PREF_AUTO_OFFLINE) {
                autoOfflineEnabled = com.example.crewmate.alarm.AutoOfflineManager.isEnabled(currentContext)
            }
            if (key == com.example.crewmate.alarm.AutoOfflineManager.PREF_RETURN_SKIPPED) {
                isReturnSkipped = sp.getBoolean(com.example.crewmate.alarm.AutoOfflineManager.PREF_RETURN_SKIPPED, false)
            }
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }

    var showCaptureDialog by remember { mutableStateOf(false) }
    var activeCaptureOption by remember { mutableStateOf<com.example.crewmate.util.DiaCaptureOption?>(null) }
    var isCapturing by remember { mutableStateOf(false) }

    LaunchedEffect(activeCaptureOption) {
        val option = activeCaptureOption ?: return@LaunchedEffect
        val successState = state as? TodayWorkUiState.Success ?: return@LaunchedEffect
        isCapturing = true
        try {
            val captureItems = successState.result.dias.map {
                com.example.crewmate.util.DiaCaptureItem(
                    schedule = it.schedule,
                    detail = it.detail
                )
            }
            val badgeText = if (!successState.result.isToday) {
                "${successState.result.workDate.format(java.time.format.DateTimeFormatter.ofPattern("MM.dd"))} 근무"
            } else null
            com.example.crewmate.util.DiaCaptureHelper.executeCapture(
                context = currentContext,
                headerTitle = "승무 다이아 상세",
                workDate = successState.result.workDate,
                dias = captureItems,
                option = option,
                badgeText = badgeText
            )
        } finally {
            isCapturing = false
            activeCaptureOption = null
        }
    }

    if (showCaptureDialog) {
        com.example.crewmate.util.DiaCaptureOptionDialog(
            onDismissRequest = { showCaptureDialog = false },
            onOptionSelected = { option ->
                showCaptureDialog = false
                activeCaptureOption = option
            }
        )
    }

    if (isCapturing) {
        com.example.crewmate.util.DiaCaptureProgressDialog()
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
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
            
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
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



                Surface(
                    onClick = { showCaptureDialog = true },
                    shape = RoundedCornerShape(8.dp),
                    color = Color.Transparent,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier.size(28.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text("✂️", fontSize = 13.sp)
                    }
                }
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
                                enableHandoverLookup = true
                            )
                            if (index < result.dias.lastIndex) Spacer(Modifier.height(14.dp))
                        }
                        
                        Spacer(Modifier.height(16.dp))
                        AttendanceStatusSection(
                            attendanceList = result.attendanceStatusList,
                            state = targetState,
                            onNavigateToAlarmSettings = onNavigateToAlarmSettings,
                            isRefreshingAttendance = isRefreshingAttendance,
                            onRefreshAttendance = onRefreshAttendance
                        )
                        if (isOfflineMode) {
                            Spacer(Modifier.height(12.dp))
                            Text(
                                text = "ℹ️ 오프라인 모드에서는 저장된 기초자료·캐시 기준으로 표시되며 실시간 Xcrew 변동사항이 반영되지 않습니다.",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                lineHeight = 15.sp,
                                modifier = Modifier.padding(horizontal = 4.dp)
                            )
                        }
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
    onNavigateToAlarmSettings: () -> Unit,
    isRefreshingAttendance: Boolean = false,
    onRefreshAttendance: () -> Unit = {}
) {
    var isExpanded by remember { mutableStateOf(false) }

    // Move alarm chip right above Attendance Status Section
    if (state is TodayWorkUiState.Success) {
        val context = LocalContext.current
        var upcomingAlarmsCount by remember { androidx.compose.runtime.mutableIntStateOf(0) }
        var isAlarmEnabled by remember { mutableStateOf(false) }
        var showUpcomingAlarmsDialog by remember { mutableStateOf(false) }
        var upcomingAlarmsList by remember { mutableStateOf<List<com.example.crewmate.alarm.WorkAlarmItem>>(emptyList()) }
        var isTelegramEnabled by remember { mutableStateOf(com.example.crewmate.telegram.SubwayNowManager.isConfigured(context)) }
        var showTelegramControlDialog by remember { mutableStateOf(false) }
        var todayTrackTargets by remember { mutableStateOf<List<com.example.crewmate.telegram.SubwayNowTrackTarget>>(emptyList()) }
        var currentDiaSegments by remember { mutableStateOf<List<com.example.crewmate.data.remote.xcrew.DiaDetailSegment>>(emptyList()) }
        val dateKey = state.result.workDate.format(DateTimeFormatter.ofPattern("yyyyMMdd", Locale.ROOT))
        val alarmStore = remember(context) { com.example.crewmate.alarm.WorkAlarmSettingsStore(context) }
        var bypassedAlarmKeys by remember(dateKey) { mutableStateOf(alarmStore.getBypassedAlarmKeys(dateKey)) }

        androidx.compose.runtime.LaunchedEffect(state, bypassedAlarmKeys) {
            val db = com.example.crewmate.database.CrewMateDatabase.getInstance(context)
            val alarmStore = com.example.crewmate.alarm.WorkAlarmSettingsStore(context)
            val alarmSettings = alarmStore.loadSettings()
            isAlarmEnabled = alarmSettings.alarmEnabled
            isTelegramEnabled = com.example.crewmate.telegram.SubwayNowManager.isConfigured(context)
            
            val allSegments = mutableListOf<com.example.crewmate.data.remote.xcrew.DiaDetailSegment>()
            state.result.dias.forEach { dia ->
                if (dia.detail.segments.isNotEmpty()) {
                    allSegments.addAll(dia.detail.segments)
                } else {
                    val diaDateStr = dia.schedule.workDate?.filter { it.isDigit() }
                        ?: state.result.workDate.format(DateTimeFormatter.BASIC_ISO_DATE)
                    val segs = com.example.crewmate.alarm.WorkDutySyncCoordinator.loadCachedSegmentsForDia(
                        db = db,
                        dateStr = diaDateStr,
                        diaNo = dia.schedule.diaNo.orEmpty()
                    )
                    allSegments.addAll(segs)
                }
            }
            currentDiaSegments = allSegments

            val syncResult = com.example.crewmate.alarm.WorkDutySyncCoordinator.sync(
                context = context,
                workDate = state.result.workDate,
                segments = allSegments,
                attendanceStatuses = state.result.attendanceStatusList
            )
            upcomingAlarmsList = syncResult.alarms
            upcomingAlarmsCount = syncResult.alarms.size
            todayTrackTargets = syncResult.telegramTargets
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

        if (isTelegramEnabled && todayTrackTargets.isNotEmpty()) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showTelegramControlDialog = true },
                color = MaterialTheme.colorScheme.secondaryContainer,
                shape = MaterialTheme.shapes.medium
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "🤖 텔레그램 열차 추적 (${todayTrackTargets.size}건 연동/예정)",
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
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
                                val isBypassed = bypassedAlarmKeys.contains(item.uniqueKey)
                                Surface(
                                    color = if (isBypassed) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f) else MaterialTheme.colorScheme.surfaceVariant,
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.fillMaxWidth(),
                                    border = if (isBypassed) BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant) else null
                                ) {
                                    Row(
                                        modifier = Modifier.padding(10.dp).fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Column(
                                            modifier = Modifier.weight(1f),
                                            verticalArrangement = Arrangement.spacedBy(2.dp)
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Text(
                                                    text = "⏰ ${item.triggerDateTime.format(DateTimeFormatter.ofPattern("MM.dd (E) HH:mm", Locale.KOREAN))} | ${item.ruleTitle}",
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 13.sp,
                                                    color = if (isBypassed) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f) else MaterialTheme.colorScheme.primary,
                                                    style = if (isBypassed) androidx.compose.ui.text.TextStyle(textDecoration = androidx.compose.ui.text.style.TextDecoration.LineThrough) else androidx.compose.ui.text.TextStyle.Default
                                                )
                                                if (isBypassed) {
                                                    Spacer(Modifier.width(6.dp))
                                                    Surface(
                                                        color = MaterialTheme.colorScheme.errorContainer,
                                                        shape = RoundedCornerShape(4.dp)
                                                    ) {
                                                        Text(
                                                            text = "제외됨",
                                                            fontSize = 10.sp,
                                                            fontWeight = FontWeight.Bold,
                                                            color = MaterialTheme.colorScheme.onErrorContainer,
                                                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                                        )
                                                    }
                                                }
                                            }
                                            Text(
                                                text = item.message,
                                                fontSize = 12.sp,
                                                color = if (isBypassed) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f) else MaterialTheme.colorScheme.onSurface,
                                                style = if (isBypassed) androidx.compose.ui.text.TextStyle(textDecoration = androidx.compose.ui.text.style.TextDecoration.LineThrough) else androidx.compose.ui.text.TextStyle.Default
                                            )
                                        }

                                        Spacer(Modifier.width(8.dp))
                                        OutlinedButton(
                                            onClick = {
                                                val newBypass = !isBypassed
                                                alarmStore.setAlarmBypassed(dateKey, item.uniqueKey, newBypass)
                                                val updatedSet = alarmStore.getBypassedAlarmKeys(dateKey)
                                                bypassedAlarmKeys = updatedSet

                                                // Reschedule active alarms immediately
                                                val activeAlarms = upcomingAlarmsList.filter { !updatedSet.contains(it.uniqueKey) }
                                                com.example.crewmate.alarm.WorkAlarmScheduler.scheduleAlarms(context, activeAlarms)
                                            },
                                            shape = RoundedCornerShape(8.dp),
                                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                            modifier = Modifier.height(32.dp),
                                            colors = if (isBypassed) ButtonDefaults.outlinedButtonColors(containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)) else ButtonDefaults.outlinedButtonColors()
                                        ) {
                                            Text(
                                                text = if (isBypassed) "Skip 해제" else "Skip",
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = if (isBypassed) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                                            )
                                        }
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

        if (showTelegramControlDialog) {
            val scope = rememberCoroutineScope()
            var editingTarget by remember { mutableStateOf<com.example.crewmate.telegram.SubwayNowTrackTarget?>(null) }
            var stoppedTrainNos by remember(dateKey) {
                mutableStateOf(todayTrackTargets.filter {
                    com.example.crewmate.telegram.SubwayNowSettings.isTrainStopped(context, it.trainNo, dateKey)
                }.map { it.trainNo }.toSet())
            }

            androidx.compose.material3.AlertDialog(
                onDismissRequest = { showTelegramControlDialog = false },
                containerColor = MaterialTheme.colorScheme.surface,
                shape = MaterialTheme.shapes.extraLarge,
                tonalElevation = 6.dp,
                title = {
                    Text(
                        "🤖 텔레그램 열차 위치 추적 (${todayTrackTargets.size}건)",
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                },
                text = {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = "설정된 시각표에 맞춰 SubwayNowBot으로 명령어가 자동 전송되어 텔레그램으로 열차 현위치를 안내받습니다. (카드를 터치하면 명령어 및 시각을 직접 수정할 수 있습니다)",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        todayTrackTargets.forEach { target ->
                            val isTargetStopped = stoppedTrainNos.contains(target.trainNo) ||
                                    com.example.crewmate.telegram.SubwayNowSettings.isTrainStopped(context, target.trainNo, dateKey)

                            Surface(
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { editingTarget = target },
                                border = BorderStroke(
                                    1.dp,
                                    if (isTargetStopped) MaterialTheme.colorScheme.error.copy(alpha = 0.5f)
                                    else if (target.isUserOverridden) MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)
                                    else MaterialTheme.colorScheme.outlineVariant
                                )
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Text(
                                                text = "${target.trainNo} (${target.targetStation}역)",
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 14.sp,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                            if (target.isUserOverridden) {
                                                Surface(
                                                    color = MaterialTheme.colorScheme.primaryContainer,
                                                    shape = RoundedCornerShape(4.dp)
                                                ) {
                                                    Text(
                                                        text = "수정됨",
                                                        fontSize = 10.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                                    )
                                                }
                                            }
                                        }
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            Text(
                                                text = "출발: ${target.baseDepartureTime}",
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                            Text(
                                                text = "✏️",
                                                fontSize = 11.sp
                                            )
                                        }
                                    }

                                    Spacer(Modifier.height(4.dp))
                                    Text(
                                        text = "명령어: ${target.rawCommand}",
                                        fontSize = 11.sp,
                                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                        color = MaterialTheme.colorScheme.secondary
                                    )
                                    Text(
                                        text = "추적 개시: ${target.trackStartTimeStr} (전송: ${target.sendDateTime.format(DateTimeFormatter.ofPattern("HH:mm"))})",
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )

                                    Spacer(Modifier.height(8.dp))

                                    if (isTargetStopped) {
                                        Surface(
                                            color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.6f),
                                            shape = RoundedCornerShape(4.dp),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Text(
                                                text = "🛑 추적 중지됨 (재개를 누르기 전까지 자동 발송 차단)",
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onErrorContainer,
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                                textAlign = TextAlign.Center
                                            )
                                        }
                                        Spacer(Modifier.height(6.dp))
                                        Button(
                                            onClick = {
                                                scope.launch {
                                                    val res = com.example.crewmate.telegram.SubwayNowManager.resumeTarget(
                                                        context = context,
                                                        target = target,
                                                        workDate = state.result.workDate
                                                    )
                                                    if (res.isSuccess) {
                                                        stoppedTrainNos = stoppedTrainNos - target.trainNo
                                                        android.widget.Toast.makeText(context, "${target.trainNo} 추적이 재개되었습니다.", android.widget.Toast.LENGTH_SHORT).show()
                                                    } else {
                                                        android.widget.Toast.makeText(context, "재개 실패: ${res.exceptionOrNull()?.message}", android.widget.Toast.LENGTH_LONG).show()
                                                    }
                                                }
                                            },
                                            modifier = Modifier.fillMaxWidth().height(32.dp),
                                            shape = RoundedCornerShape(6.dp),
                                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                                        ) {
                                            Text("▶️ 추적 재개", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                        }
                                    } else {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Button(
                                                onClick = {
                                                    scope.launch {
                                                        val res = com.example.crewmate.telegram.SubwayNowManager.dispatchTarget(
                                                            context = context,
                                                            target = target,
                                                            isManual = true
                                                        )
                                                        if (res.isSuccess) {
                                                            android.widget.Toast.makeText(context, "${target.trainNo} 봇 전송 완료!", android.widget.Toast.LENGTH_SHORT).show()
                                                        } else {
                                                            android.widget.Toast.makeText(context, "전송 실패: ${res.exceptionOrNull()?.message}", android.widget.Toast.LENGTH_LONG).show()
                                                        }
                                                    }
                                                },
                                                modifier = Modifier.weight(1f).height(32.dp),
                                                shape = RoundedCornerShape(6.dp),
                                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                                            ) {
                                                Text("🚀 즉시 전송", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                            }

                                            OutlinedButton(
                                                onClick = {
                                                    scope.launch {
                                                        val res = com.example.crewmate.telegram.SubwayNowManager.cancelTarget(
                                                            context = context,
                                                            trainNo = target.trainNo,
                                                            workDate = state.result.workDate
                                                        )
                                                        if (res.isSuccess) {
                                                            stoppedTrainNos = stoppedTrainNos + target.trainNo
                                                            android.widget.Toast.makeText(context, "${target.trainNo} 추적이 중지되었습니다.", android.widget.Toast.LENGTH_SHORT).show()
                                                        } else {
                                                            android.widget.Toast.makeText(context, "취소 실패: ${res.exceptionOrNull()?.message}", android.widget.Toast.LENGTH_SHORT).show()
                                                        }
                                                    }
                                                },
                                                modifier = Modifier.weight(1f).height(32.dp),
                                                shape = RoundedCornerShape(6.dp),
                                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.error)
                                            ) {
                                                Text("🛑 추적 중지", fontSize = 11.sp, color = MaterialTheme.colorScheme.error)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    androidx.compose.material3.TextButton(onClick = { showTelegramControlDialog = false }) {
                        Text("닫기", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    }
                }
            )

            if (editingTarget != null) {
                val targetToEdit = editingTarget!!
                val tokens = targetToEdit.rawCommand.trim().split(" ")
                val initialPrefix = if (tokens.size > 1) tokens.dropLast(1).joinToString(" ") else targetToEdit.rawCommand
                var editCommandPrefix by remember(targetToEdit) { mutableStateOf(initialPrefix) }
                var editTrackStartTime by remember(targetToEdit) { mutableStateOf(targetToEdit.trackStartTimeStr) }
                var editSendTime by remember(targetToEdit) {
                    mutableStateOf(targetToEdit.sendDateTime.format(DateTimeFormatter.ofPattern("HH:mm", Locale.ROOT)))
                }

                androidx.compose.material3.AlertDialog(
                    onDismissRequest = { editingTarget = null },
                    title = {
                        Text(
                            "✏️ ${targetToEdit.trainNo} 추적 설정 직접 수정",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    },
                    text = {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(
                                text = "이 열차 1건에 대해 전송될 봇 명령어와 시각표를 원하는 값으로 덮어씁니다(Override).",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            OutlinedTextField(
                                value = editCommandPrefix,
                                onValueChange = { editCommandPrefix = it },
                                label = { Text("전송 명령어 (열차/목표역/주기)") },
                                trailingIcon = {
                                    Surface(
                                        color = MaterialTheme.colorScheme.primaryContainer,
                                        shape = RoundedCornerShape(4.dp),
                                        modifier = Modifier.padding(end = 8.dp)
                                    ) {
                                        Text(
                                            text = editTrackStartTime,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 12.sp,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                },
                                singleLine = true,
                                textStyle = androidx.compose.ui.text.TextStyle(
                                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                    fontSize = 12.sp
                                ),
                                modifier = Modifier.fillMaxWidth()
                            )

                            Text(
                                text = "최종 전송 명령어: ${editCommandPrefix.trim()} ${editTrackStartTime.trim()}",
                                fontSize = 11.sp,
                                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.secondary
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedTextField(
                                    value = editTrackStartTime,
                                    onValueChange = { editTrackStartTime = it },
                                    label = { Text("추적 개시 (HH:mm)") },
                                    placeholder = { Text("14:15") },
                                    singleLine = true,
                                    modifier = Modifier.weight(1f)
                                )
                                OutlinedTextField(
                                    value = editSendTime,
                                    onValueChange = { editSendTime = it },
                                    label = { Text("예약 전송 (HH:mm)") },
                                    placeholder = { Text("13:45") },
                                    singleLine = true,
                                    modifier = Modifier.weight(1f)
                                )
                            }

                            Text(
                                text = "기준 출발: ${targetToEdit.baseDepartureTime} | 현재 설정: 전송 ${targetToEdit.sendDateTime.format(DateTimeFormatter.ofPattern("HH:mm"))} ➔ 개시 ${targetToEdit.trackStartTimeStr}",
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.secondary
                            )
                        }
                    },
                    confirmButton = {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            if (targetToEdit.isUserOverridden) {
                                OutlinedButton(
                                    onClick = {
                                        com.example.crewmate.telegram.SubwayNowSettings.removeTargetOverride(
                                            context,
                                            dateKey,
                                            targetToEdit.trainNo
                                        )
                                        val overrides = com.example.crewmate.telegram.SubwayNowSettings.getTargetOverrides(context, dateKey)
                                        val config = com.example.crewmate.telegram.SubwayNowSettings.getConfig(context)
                                        val replacements = com.example.crewmate.telegram.SubwayNowSettings.getStationReplacements(context)
                                        val baseTargets = com.example.crewmate.telegram.SubwayNowRuleEvaluator.evaluateTargets(
                                            segments = currentDiaSegments,
                                            workDate = state.result.workDate,
                                            sendLeadMinutes = config.sendLeadMinutes,
                                            trackLeadMinutes = config.trackLeadMinutes,
                                            trackInterval = config.trackInterval,
                                            stationReplacements = replacements
                                        )
                                        val updated = com.example.crewmate.telegram.SubwayNowRuleEvaluator.applyOverrides(baseTargets, overrides)
                                        todayTrackTargets = updated
                                        val resetTarget = updated.find { it.trainNo == targetToEdit.trainNo }
                                        if (resetTarget != null) {
                                            com.example.crewmate.telegram.SubwayNowManager.rescheduleTarget(context, resetTarget, state.result.workDate)
                                        }
                                        android.widget.Toast.makeText(context, "${targetToEdit.trainNo} 기본 계산값으로 초기화되었습니다.", android.widget.Toast.LENGTH_SHORT).show()
                                        editingTarget = null
                                    },
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                                ) {
                                    Text("초기화", fontSize = 12.sp)
                                }
                            }

                            Button(
                                onClick = {
                                    val prefix = editCommandPrefix.trim()
                                    val trackSt = editTrackStartTime.trim()
                                    val sendSt = editSendTime.trim()
                                    if (prefix.isBlank() || trackSt.isBlank() || sendSt.isBlank()) {
                                        android.widget.Toast.makeText(context, "모든 입력값을 입력해주세요.", android.widget.Toast.LENGTH_SHORT).show()
                                        return@Button
                                    }
                                    val finalCommand = "$prefix $trackSt"
                                    val overrideObj = com.example.crewmate.telegram.TrackTargetOverride(
                                        rawCommand = finalCommand,
                                        trackStartTimeStr = trackSt,
                                        sendTimeStr = sendSt
                                    )
                                    com.example.crewmate.telegram.SubwayNowSettings.setTargetOverride(
                                        context,
                                        dateKey,
                                        targetToEdit.trainNo,
                                        overrideObj
                                    )

                                    val overrides = com.example.crewmate.telegram.SubwayNowSettings.getTargetOverrides(context, dateKey)
                                    val config = com.example.crewmate.telegram.SubwayNowSettings.getConfig(context)
                                    val replacements = com.example.crewmate.telegram.SubwayNowSettings.getStationReplacements(context)
                                    val baseTargets = com.example.crewmate.telegram.SubwayNowRuleEvaluator.evaluateTargets(
                                        segments = currentDiaSegments,
                                        workDate = state.result.workDate,
                                        sendLeadMinutes = config.sendLeadMinutes,
                                        trackLeadMinutes = config.trackLeadMinutes,
                                        trackInterval = config.trackInterval,
                                        stationReplacements = replacements
                                    )
                                    val updated = com.example.crewmate.telegram.SubwayNowRuleEvaluator.applyOverrides(baseTargets, overrides)
                                    todayTrackTargets = updated
                                    val savedTarget = updated.find { it.trainNo == targetToEdit.trainNo }
                                    if (savedTarget != null) {
                                        com.example.crewmate.telegram.SubwayNowManager.rescheduleTarget(context, savedTarget, state.result.workDate)
                                    }
                                    android.widget.Toast.makeText(context, "${targetToEdit.trainNo} 추적 설정이 수정되었습니다.", android.widget.Toast.LENGTH_SHORT).show()
                                    editingTarget = null
                                }
                            ) {
                                Text("저장 및 적용", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { editingTarget = null }) {
                            Text("취소")
                        }
                    }
                )
            }
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
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            isExpanded = true
                            onRefreshAttendance()
                        },
                        enabled = !isRefreshingAttendance,
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                        modifier = Modifier.height(28.dp)
                    ) {
                        if (isRefreshingAttendance) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(12.dp),
                                strokeWidth = 1.8.dp,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                text = "조회 중",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        } else {
                            Text(
                                text = "🔄 새로고침",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                    Text(
                        text = if (isExpanded) "▲" else "▼",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
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