package com.example.crewmate.alarm

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.runtime.DisposableEffect
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.crewmate.data.remote.xcrew.DiaDetailParser
import com.example.crewmate.database.CrewMateDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
private fun NumberInputBox(
    value: Int,
    onValueChange: (Int) -> Unit
) {
    var text by remember(value) { mutableStateOf(value.toString()) }

    Box(
        modifier = Modifier
            .width(50.dp)
            .height(34.dp)
            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(6.dp))
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(6.dp)),
        contentAlignment = Alignment.Center
    ) {
        BasicTextField(
            value = text,
            onValueChange = { newStr ->
                if (newStr.length <= 3 && newStr.all { it.isDigit() }) {
                    text = newStr
                    val num = newStr.toIntOrNull()
                    if (num != null) {
                        onValueChange(num)
                    }
                }
            },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            singleLine = true,
            textStyle = androidx.compose.ui.text.TextStyle(
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        )
    }
}

private fun checkAlarmPrerequisites(context: Context): Triple<Boolean, Boolean, Boolean> {
    val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? android.app.AlarmManager
    val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager

    val notifEnabled = NotificationManagerCompat.from(context).areNotificationsEnabled()
    val hasNotif = notifEnabled && if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    } else true

    val hasExact = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        alarmManager?.canScheduleExactAlarms() == true
    } else true

    val isIgnoringBattery = powerManager?.isIgnoringBatteryOptimizations(context.packageName) == true

    return Triple(hasNotif, hasExact, isIgnoringBattery)
}

@Composable
fun WorkAlarmSettingsScreen(
    onBack: () -> Unit = {},
    xcrewSession: com.example.crewmate.data.remote.xcrew.XcrewWebViewSession = remember { com.example.crewmate.data.remote.xcrew.XcrewWebViewSession() }
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val alarmSettingsStore = remember { WorkAlarmSettingsStore(context) }
    var alarmSettings by remember { mutableStateOf(alarmSettingsStore.loadSettings()) }

    var showSafetyDialog by remember { mutableStateOf(false) }
    var showHistoryDialog by remember { mutableStateOf(false) }

    val db = remember { CrewMateDatabase.getInstance(context) }
    val alarmHistoryDao = remember { db.alarmHistoryDao() }
    val alarmHistories by alarmHistoryDao.getAllHistories().collectAsState(initial = emptyList())

    // Evaluate today's upcoming alarms
    var todayUpcomingAlarms by remember { mutableStateOf<List<WorkAlarmItem>>(emptyList()) }
    var isEvaluatingToday by remember { mutableStateOf(false) }

    fun refreshTodayAlarms() {
        isEvaluatingToday = true
        coroutineScope.launch(Dispatchers.IO) {
            val today = com.example.crewmate.util.AppDateProvider.today
            val todayStr = today.format(DateTimeFormatter.ofPattern("yyyyMMdd", Locale.ROOT))
            val monthStr = today.format(DateTimeFormatter.ofPattern("yyyyMM", Locale.ROOT))

            val schedules = db.workScheduleDao().getSchedulesForMonth(monthStr)
            val todaySchedules = schedules.filter { it.date == todayStr && !it.diaNo.isNullOrBlank() }

            val allSegments = mutableListOf<com.example.crewmate.data.remote.xcrew.DiaDetailSegment>()

            todaySchedules.forEach { sched ->
                val segs = WorkDutySyncCoordinator.loadCachedSegmentsForDia(
                    db = db,
                    dateStr = todayStr,
                    diaNo = sched.diaNo.orEmpty()
                )
                allSegments.addAll(segs)
            }

            val prefs = context.getSharedPreferences("crewmate_prefs", Context.MODE_PRIVATE)
            val isOffline = prefs.getBoolean("is_offline_mode", false)
            val empNm = xcrewSession.userInfo.value?.first ?: ""
            val cachedStatuses = AttendanceSnapshotStore.load(context, todayStr)?.statuses.orEmpty()
            val attendanceStatuses = if (!isOffline && empNm.isNotBlank()) {
                val scheduleDataSource = com.example.crewmate.data.remote.xcrew.XcrewWebViewXhrDataSource(
                    xcrewSession,
                    com.example.crewmate.data.remote.xcrew.XcrewWorkScheduleParser()
                )
                val fetched = runCatching { scheduleDataSource.fetchAttendanceStatus(empNm, todayStr) }.getOrDefault(emptyList())
                if (fetched.isNotEmpty()) {
                    AttendanceSnapshotStore.save(context, todayStr, fetched)
                    fetched
                } else {
                    cachedStatuses
                }
            } else {
                cachedStatuses
            }

            val syncResult = if (allSegments.isNotEmpty() || attendanceStatuses.isNotEmpty()) {
                WorkDutySyncCoordinator.sync(
                    context = context,
                    workDate = today,
                    segments = allSegments,
                    attendanceStatuses = attendanceStatuses
                )
            } else {
                WorkDutySyncCoordinator.syncFromLocalDatabase(context, today)
            }
            MidnightCaptureReceiver.scheduleNextMidnight(context)

            val upcoming = syncResult?.alarms ?: WorkAlarmRuleEvaluator.evaluate(
                segments = allSegments,
                workDate = today,
                settings = alarmSettings,
                attendanceStatuses = attendanceStatuses,
                checkedInKeys = AttendanceCheckInLatch.allKeys(context)
            )
            withContext(Dispatchers.Main) {
                todayUpcomingAlarms = upcoming
                isEvaluatingToday = false
            }
        }
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    var prerequisites by remember { mutableStateOf(checkAlarmPrerequisites(context)) }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        val updatedPrereqs = checkAlarmPrerequisites(context)
        prerequisites = updatedPrereqs
        if (isGranted) {
            Toast.makeText(context, "알림 권한이 허용되었습니다.", Toast.LENGTH_SHORT).show()
            if (updatedPrereqs.first && updatedPrereqs.second && updatedPrereqs.third) {
                val updated = alarmSettings.copy(alarmEnabled = true)
                alarmSettings = updated
                alarmSettingsStore.saveSettings(updated)
                refreshTodayAlarms()
                showSafetyDialog = false
            }
        } else {
            Toast.makeText(context, "알림 권한이 허용되지 않았습니다. 설정 화면에서 알림을 켜주세요.", Toast.LENGTH_SHORT).show()
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                val updatedPrereqs = checkAlarmPrerequisites(context)
                prerequisites = updatedPrereqs
                if (showSafetyDialog && updatedPrereqs.first && updatedPrereqs.second && updatedPrereqs.third) {
                    val updated = alarmSettings.copy(alarmEnabled = true)
                    alarmSettings = updated
                    alarmSettingsStore.saveSettings(updated)
                    refreshTodayAlarms()
                    showSafetyDialog = false
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    fun handleNotificationPermissionClick() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val permStatus = ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
            if (permStatus != PackageManager.PERMISSION_GRANTED) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                return
            }
        }
        try {
            val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        } catch (_: Exception) {
            try {
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.parse("package:${context.packageName}")
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(intent)
            } catch (_: Exception) {
                Toast.makeText(context, "알림 설정 화면을 열 수 없습니다.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    LaunchedEffect(Unit) {
        refreshTodayAlarms()
    }

    var isUpcomingExpanded by remember { mutableStateOf(false) }
    var isRulesExpanded by remember { mutableStateOf(true) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.Top
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "정밀 알람",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.primary
            )
        }

        Spacer(Modifier.height(14.dp))

        // Card 1: Master Alarm Switch & System Health Check
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.fillMaxWidth().padding(18.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("오늘의 근무 정밀 알람", fontWeight = FontWeight.ExtraBold, fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurface)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "출근, 운전, 출고준비, 편승, 입환 등 세부 구간 시각에 맞춰 정밀 알람을 발동합니다.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Switch(
                        checked = alarmSettings.alarmEnabled,
                        onCheckedChange = { checked ->
                            if (checked) {
                                val currentPrereqs = checkAlarmPrerequisites(context)
                                prerequisites = currentPrereqs
                                val (hasNotif, hasExact, isIgnoringBattery) = currentPrereqs
                                if (!hasNotif || !hasExact || !isIgnoringBattery) {
                                    val updated = alarmSettings.copy(alarmEnabled = false)
                                    alarmSettings = updated
                                    alarmSettingsStore.saveSettings(updated)
                                    if (!hasNotif && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                                        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
                                    ) {
                                        notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                        if (!hasExact || !isIgnoringBattery) {
                                            showSafetyDialog = true
                                        }
                                    } else {
                                        showSafetyDialog = true
                                    }
                                } else {
                                    val updated = alarmSettings.copy(alarmEnabled = true)
                                    alarmSettings = updated
                                    alarmSettingsStore.saveSettings(updated)
                                    refreshTodayAlarms()
                                }
                            } else {
                                val updated = alarmSettings.copy(alarmEnabled = false)
                                alarmSettings = updated
                                alarmSettingsStore.saveSettings(updated)
                                WorkAlarmScheduler.cancelAllAlarms(context)
                                MidnightCaptureReceiver.scheduleNextMidnight(context)
                            }
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = MaterialTheme.colorScheme.primary
                        )
                    )
                }

                if (alarmSettings.alarmEnabled) {
                    HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), color = MaterialTheme.colorScheme.outlineVariant)

                    // Sound Mode
                    Text("알람 소리 / 진동 형태", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        AlarmSoundMode.entries.forEach { mode ->
                            FilterChip(
                                selected = alarmSettings.soundMode == mode,
                                onClick = {
                                    val updated = alarmSettings.copy(soundMode = mode)
                                    alarmSettings = updated
                                    alarmSettingsStore.saveSettings(updated)
                                },
                                label = { Text(mode.title) }
                            )
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        // Card 2 (Middle Position): Action Tests & History Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
            Column(modifier = Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("🧪 알람 테스트 및 작동 기록", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                Button(
                    onClick = {
                        AlarmLoopService.stop(context)
                        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
                        notificationManager.cancelAll()
                        Toast.makeText(context, "알람이 강제 해제되었습니다.", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.fillMaxWidth().height(42.dp),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    )
                ) {
                    Text("🔕 알람 강제 해제 (소리/진동 즉시 정지)", fontWeight = FontWeight.Bold)
                }

                OutlinedButton(
                    onClick = { showHistoryDialog = true },
                    modifier = Modifier.fillMaxWidth().height(42.dp),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("알람 작동 기록 (${alarmHistories.size}건)", fontWeight = FontWeight.Bold)
                }

                OutlinedButton(
                    onClick = { showSafetyDialog = true },
                    modifier = Modifier.fillMaxWidth().height(42.dp),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("⌚ 갤럭시 워치 / 알람 환경 점검", fontWeight = FontWeight.Bold)
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            WorkAlarmScheduler.scheduleTestAlarm(context, 5)
                            Toast.makeText(context, "5초 후 알람이 울립니다. 화면을 끈 상태에서 확인해보세요.", Toast.LENGTH_LONG).show()
                        },
                        modifier = Modifier.weight(1f).height(44.dp),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    ) {
                        Text("🔔 5초 후 테스트", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }

                    Button(
                        onClick = {
                            WorkAlarmScheduler.scheduleTestAlarm(context, 60)
                            Toast.makeText(context, "1분 후 알람이 울립니다. 화면을 끈 상태에서 확인해보세요.", Toast.LENGTH_LONG).show()
                        },
                        modifier = Modifier.weight(1f).height(44.dp),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                            contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    ) {
                        Text("⏱️ 1분 후 테스트", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        // Card 3: Detailed Alarm Rules Settings Card (Default Open)
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.fillMaxWidth().padding(18.dp)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { isRulesExpanded = !isRulesExpanded },
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("⚙️ 정밀 알람 세부 규칙 설정", fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.primary)
                    Text(
                        text = if (isRulesExpanded) "▲" else "▼",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                androidx.compose.animation.AnimatedVisibility(visible = isRulesExpanded) {
                    Column(modifier = Modifier.fillMaxWidth().padding(top = 10.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                        // Rule 0-A: Attendance
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(
                                checked = alarmSettings.attendanceEnabled,
                                onCheckedChange = { checked ->
                                    val updated = alarmSettings.copy(attendanceEnabled = checked)
                                    alarmSettings = updated
                                    alarmSettingsStore.saveSettings(updated)
                                    refreshTodayAlarms()
                                }
                            )
                            Text("출무 ", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                            NumberInputBox(
                                value = alarmSettings.attendanceLeadMinutes,
                                onValueChange = { newMin ->
                                    val updated = alarmSettings.copy(attendanceLeadMinutes = newMin)
                                    alarmSettings = updated
                                    alarmSettingsStore.saveSettings(updated)
                                    refreshTodayAlarms()
                                }
                            )
                            Text(" 분 전 (실제 출무 완료 시 자동 해제)", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        }

                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                        // Rule 0: Commute
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(
                                checked = alarmSettings.commuteEnabled,
                                onCheckedChange = { checked ->
                                    val updated = alarmSettings.copy(commuteEnabled = checked)
                                    alarmSettings = updated
                                    alarmSettingsStore.saveSettings(updated)
                                    refreshTodayAlarms()
                                }
                            )
                            Text("출근 ", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                            NumberInputBox(
                                value = alarmSettings.commuteLeadMinutes,
                                onValueChange = { newMin ->
                                    val updated = alarmSettings.copy(commuteLeadMinutes = newMin)
                                    alarmSettings = updated
                                    alarmSettingsStore.saveSettings(updated)
                                    refreshTodayAlarms()
                                }
                            )
                            Text(" 분 전", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        }

                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                        // Rule 1: Driving
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = alarmSettings.driveEnabled,
                        onCheckedChange = { checked ->
                            val updated = alarmSettings.copy(driveEnabled = checked)
                            alarmSettings = updated
                            alarmSettingsStore.saveSettings(updated)
                            refreshTodayAlarms()
                        }
                    )
                    Text("운전/승무 ", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    NumberInputBox(
                        value = alarmSettings.driveLeadMinutes,
                        onValueChange = { newMin ->
                            val updated = alarmSettings.copy(driveLeadMinutes = newMin)
                            alarmSettings = updated
                            alarmSettingsStore.saveSettings(updated)
                            refreshTodayAlarms()
                        }
                    )
                    Text(" 분 전", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                }
                if (alarmSettings.driveEnabled) {
                    Column(modifier = Modifier.padding(start = 18.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(
                                checked = alarmSettings.driveCheckGamsi,
                                onCheckedChange = { checked ->
                                    val updated = alarmSettings.copy(driveCheckGamsi = checked)
                                    alarmSettings = updated
                                    alarmSettingsStore.saveSettings(updated)
                                    refreshTodayAlarms()
                                }
                            )
                            Text("직전 감시 항목이 있으면 감시 ${alarmSettings.driveLeadMinutes}분 전 알람", fontSize = 13.sp, fontWeight = FontWeight.Medium)
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(
                                checked = alarmSettings.driveSkipIfPrepWithinMinutes,
                                onCheckedChange = { checked ->
                                    val updated = alarmSettings.copy(driveSkipIfPrepWithinMinutes = checked)
                                    alarmSettings = updated
                                    alarmSettingsStore.saveSettings(updated)
                                    refreshTodayAlarms()
                                }
                            )
                            Text("직전 ", fontSize = 13.sp, fontWeight = FontWeight.Medium)
                            NumberInputBox(
                                value = alarmSettings.drivePrepThresholdMinutes,
                                onValueChange = { newMin ->
                                    val updated = alarmSettings.copy(drivePrepThresholdMinutes = newMin)
                                    alarmSettings = updated
                                    alarmSettingsStore.saveSettings(updated)
                                    refreshTodayAlarms()
                                }
                            )
                            Text(" 분 이내 출고준비/입환 시 예외", fontSize = 13.sp, fontWeight = FontWeight.Medium)
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(
                                checked = alarmSettings.driveSkipIfDeadheadWithinMinutes,
                                onCheckedChange = { checked ->
                                    val updated = alarmSettings.copy(driveSkipIfDeadheadWithinMinutes = checked)
                                    alarmSettings = updated
                                    alarmSettingsStore.saveSettings(updated)
                                    refreshTodayAlarms()
                                }
                            )
                            Text("직전 ", fontSize = 13.sp, fontWeight = FontWeight.Medium)
                            NumberInputBox(
                                value = alarmSettings.driveDeadheadThresholdMinutes,
                                onValueChange = { newMin ->
                                    val updated = alarmSettings.copy(driveDeadheadThresholdMinutes = newMin)
                                    alarmSettings = updated
                                    alarmSettingsStore.saveSettings(updated)
                                    refreshTodayAlarms()
                                }
                            )
                            Text(" 분 이내 편승 시 예외", fontSize = 13.sp, fontWeight = FontWeight.Medium)
                        }
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                // Rule 2: Prep
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = alarmSettings.prepEnabled,
                        onCheckedChange = { checked ->
                            val updated = alarmSettings.copy(prepEnabled = checked)
                            alarmSettings = updated
                            alarmSettingsStore.saveSettings(updated)
                            refreshTodayAlarms()
                        }
                    )
                    Text("출고준비 ", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    NumberInputBox(
                        value = alarmSettings.prepLeadMinutes,
                        onValueChange = { newMin ->
                            val updated = alarmSettings.copy(prepLeadMinutes = newMin)
                            alarmSettings = updated
                            alarmSettingsStore.saveSettings(updated)
                            refreshTodayAlarms()
                        }
                    )
                    Text(" 분 전", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                // Rule 3: Deadhead
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = alarmSettings.deadheadEnabled,
                        onCheckedChange = { checked ->
                            val updated = alarmSettings.copy(deadheadEnabled = checked)
                            alarmSettings = updated
                            alarmSettingsStore.saveSettings(updated)
                            refreshTodayAlarms()
                        }
                    )
                    Text("편승 ", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    NumberInputBox(
                        value = alarmSettings.deadheadLeadMinutes,
                        onValueChange = { newMin ->
                            val updated = alarmSettings.copy(deadheadLeadMinutes = newMin)
                            alarmSettings = updated
                            alarmSettingsStore.saveSettings(updated)
                            refreshTodayAlarms()
                        }
                    )
                    Text(" 분 전", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                }
                if (alarmSettings.deadheadEnabled) {
                    Column(modifier = Modifier.padding(start = 18.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(
                                checked = alarmSettings.deadheadSkipIfPrevDuty,
                                onCheckedChange = { checked ->
                                    val updated = alarmSettings.copy(deadheadSkipIfPrevDuty = checked)
                                    alarmSettings = updated
                                    alarmSettingsStore.saveSettings(updated)
                                    refreshTodayAlarms()
                                }
                            )
                            Text("직전 ", fontSize = 13.sp, fontWeight = FontWeight.Medium)
                            NumberInputBox(
                                value = alarmSettings.deadheadPrevThresholdMinutes,
                                onValueChange = { newMin ->
                                    val updated = alarmSettings.copy(deadheadPrevThresholdMinutes = newMin)
                                    alarmSettings = updated
                                    alarmSettingsStore.saveSettings(updated)
                                    refreshTodayAlarms()
                                }
                            )
                            Text(" 분 이내 운전/입환/편승 시 예외", fontSize = 13.sp, fontWeight = FontWeight.Medium)
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(
                                checked = alarmSettings.deadheadSkipIfNextDuty,
                                onCheckedChange = { checked ->
                                    val updated = alarmSettings.copy(deadheadSkipIfNextDuty = checked)
                                    alarmSettings = updated
                                    alarmSettingsStore.saveSettings(updated)
                                    refreshTodayAlarms()
                                }
                            )
                            Text("직후 ", fontSize = 13.sp, fontWeight = FontWeight.Medium)
                            NumberInputBox(
                                value = alarmSettings.deadheadNextThresholdMinutes,
                                onValueChange = { newMin ->
                                    val updated = alarmSettings.copy(deadheadNextThresholdMinutes = newMin)
                                    alarmSettings = updated
                                    alarmSettingsStore.saveSettings(updated)
                                    refreshTodayAlarms()
                                }
                            )
                            Text(" 분 이내 운전/입환/출고준비 시 예외", fontSize = 13.sp, fontWeight = FontWeight.Medium)
                        }
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                // Rule 4: Shunting
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = alarmSettings.shuntingEnabled,
                        onCheckedChange = { checked ->
                            val updated = alarmSettings.copy(shuntingEnabled = checked)
                            alarmSettings = updated
                            alarmSettingsStore.saveSettings(updated)
                            refreshTodayAlarms()
                        }
                    )
                    Text("입환 ", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    NumberInputBox(
                        value = alarmSettings.shuntingLeadMinutes,
                        onValueChange = { newMin ->
                            val updated = alarmSettings.copy(shuntingLeadMinutes = newMin)
                            alarmSettings = updated
                            alarmSettingsStore.saveSettings(updated)
                            refreshTodayAlarms()
                        }
                    )
                    Text(" 분 전", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                }
                if (alarmSettings.shuntingEnabled) {
                    Column(modifier = Modifier.padding(start = 18.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(
                                checked = alarmSettings.shuntingSkipIfPrevDuty,
                                onCheckedChange = { checked ->
                                    val updated = alarmSettings.copy(shuntingSkipIfPrevDuty = checked)
                                    alarmSettings = updated
                                    alarmSettingsStore.saveSettings(updated)
                                    refreshTodayAlarms()
                                }
                            )
                            Text("직전 ", fontSize = 13.sp, fontWeight = FontWeight.Medium)
                            NumberInputBox(
                                value = alarmSettings.shuntingPrevThresholdMinutes,
                                onValueChange = { newMin ->
                                    val updated = alarmSettings.copy(shuntingPrevThresholdMinutes = newMin)
                                    alarmSettings = updated
                                    alarmSettingsStore.saveSettings(updated)
                                    refreshTodayAlarms()
                                }
                            )
                            Text(" 분 이내 운전/출고준비/편승 시 예외", fontSize = 13.sp, fontWeight = FontWeight.Medium)
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(20.dp))
    }

    // Safety Prerequisites Dialog
    if (showSafetyDialog) {
        val (hasNotif, hasExact, isIgnoringBattery) = prerequisites
        AlertDialog(
            onDismissRequest = { showSafetyDialog = false },
            containerColor = MaterialTheme.colorScheme.surface,
            shape = MaterialTheme.shapes.extraLarge,
            tonalElevation = 6.dp,
            title = { Text("⚠️ 알람 시스템 준비 필요", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary) },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text("알람이 백그라운드 및 초절전 모드에서 지연 없이 완벽하게 울리려면 아래 권한/설정이 필요합니다.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface)

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                    Text(
                        text = if (hasNotif) "✅ 🔔 알림 권한: 허용됨" else "❌ 🔔 알림 권한: 미허용",
                        fontWeight = FontWeight.Bold,
                        color = if (hasNotif) Color(0xFF16A34A) else Color(0xFFDC2626),
                        fontSize = 13.sp
                    )
                    Text(
                        text = if (hasExact) "✅ ⏰ 정확한 알람 권한: 허용됨" else "❌ ⏰ 정확한 알람 권한: 미허용",
                        fontWeight = FontWeight.Bold,
                        color = if (hasExact) Color(0xFF16A34A) else Color(0xFFDC2626),
                        fontSize = 13.sp
                    )
                    Text(
                        text = if (isIgnoringBattery) "✅ 🔋 배터리 최적화 제외: 설정됨" else "❌ 🔋 배터리 최적화 제외: 미설정",
                        fontWeight = FontWeight.Bold,
                        color = if (isIgnoringBattery) Color(0xFF16A34A) else Color(0xFFDC2626),
                        fontSize = 13.sp
                    )

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                    Text("💡 갤럭시 워치 / 스마트워치 손목 진동 수신 가이드:", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                    Text("• 알람 테스트 시 스마트폰 화면을 끈 상태(잠금 화면)에서 확인해 주세요. (휴대전화 사용 중에는 워치 알림을 울리지 않는 기본 설정 때문)", style = MaterialTheme.typography.bodySmall, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("• Galaxy Wearable 앱 -> 워치 설정 -> 알림 -> 앱 알림 목록에서 'CrewMate' 스위치가 ON인지 확인해 주세요.", style = MaterialTheme.typography.bodySmall, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("• 갤럭시 워치 기기 설정 -> 소리 및 진동 -> '진동 모드' 및 '알림 진동 세기'가 켜져 있는지 확인해 주세요.", style = MaterialTheme.typography.bodySmall, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)

                    Spacer(Modifier.height(8.dp))

                    Button(
                        onClick = {
                            val wearableIntent = context.packageManager.getLaunchIntentForPackage("com.samsung.android.app.watchmanager")
                            if (wearableIntent != null) {
                                context.startActivity(wearableIntent)
                            } else {
                                try {
                                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=com.samsung.android.app.watchmanager")))
                                } catch (_: Exception) {
                                    Toast.makeText(context, "Galaxy Wearable 앱을 찾을 수 없습니다.", Toast.LENGTH_SHORT).show()
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                            contentColor = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                    ) {
                        Text("📱 Galaxy Wearable 앱 알림 설정 열기", fontWeight = FontWeight.Bold)
                    }

                    Spacer(Modifier.height(8.dp))

                    if (!hasNotif) {
                        Button(
                            onClick = { handleNotificationPermissionClick() },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF2563EB),
                                contentColor = Color.White
                            )
                        ) {
                            Text("🔔 알림 권한 허용 (직통 연결)", fontWeight = FontWeight.Bold)
                        }
                    }

                    if (!hasExact) {
                        Button(
                            onClick = {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                                    try {
                                        context.startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${context.packageName}")))
                                    } catch (_: Exception) {
                                        context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}")))
                                    }
                                } else {
                                    context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}")))
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF0D9488),
                                contentColor = Color.White
                            )
                        ) {
                            Text("⏰ 정확한 알람 권한 설정 (직통 연결)", fontWeight = FontWeight.Bold)
                        }
                    }

                    if (!isIgnoringBattery) {
                        OutlinedButton(
                            onClick = {
                                val pkgUri = Uri.parse("package:${context.packageName}")
                                try {
                                    val intent = Intent(
                                        Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS
                                    ).apply {
                                        data = pkgUri
                                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                    }
                                    context.startActivity(intent)
                                } catch (_: Exception) {
                                    try {
                                        val intent = Intent(
                                            Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS
                                        ).apply {
                                            flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                        }
                                        context.startActivity(intent)
                                    } catch (_: Exception) {
                                        val intent = Intent(
                                            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                            pkgUri
                                        ).apply {
                                            flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                        }
                                        context.startActivity(intent)
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("🔋 배터리 최적화 제외 설정 (직통 연결)", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showSafetyDialog = false }) {
                    Text("닫기", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                }
            }
        )
    }

    // Alarm History Dialog
    if (showHistoryDialog) {
        AlertDialog(
            onDismissRequest = { showHistoryDialog = false },
            containerColor = MaterialTheme.colorScheme.surface,
            shape = MaterialTheme.shapes.extraLarge,
            tonalElevation = 6.dp,
            title = { Text("알람 작동 기록 (최근 14일)", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary) },
            text = {
                if (alarmHistories.isEmpty()) {
                    Text("작동된 알람 기록이 없습니다.", modifier = Modifier.padding(vertical = 16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 300.dp)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        alarmHistories.forEach { item ->
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Column(modifier = Modifier.padding(10.dp)) {
                                    Text("${item.title} - ${item.firedAt}", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                    Text("${item.message} (${item.dptStnNm})", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface)
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (alarmHistories.isNotEmpty()) {
                        TextButton(onClick = {
                            coroutineScope.launch {
                                alarmHistoryDao.clearAllHistories()
                            }
                        }) {
                            Text("기록 삭제", color = MaterialTheme.colorScheme.error)
                        }
                    }
                    TextButton(onClick = { showHistoryDialog = false }) {
                        Text("확인", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        )
    }
}
}

}

