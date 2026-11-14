package com.example.crewmate.settings

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.crewmate.alarm.*
import com.example.crewmate.database.CrewMateDatabase
import com.example.crewmate.navigation.Routes
import com.example.crewmate.ui.theme.AppThemeMode
import com.example.crewmate.util.DiaCaptureOption
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private fun checkAlarmPrerequisites(context: Context): Triple<Boolean, Boolean, Boolean> {
    val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? android.app.AlarmManager
    val powerManager = context.getSystemService(Context.POWER_SERVICE) as? android.os.PowerManager

    val notifEnabled = NotificationManagerCompat.from(context).areNotificationsEnabled()
    val hasNotif = notifEnabled && if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    } else true

    val hasExact = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
        alarmManager?.canScheduleExactAlarms() == true
    } else true

    val isIgnoringBattery = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
        powerManager?.isIgnoringBatteryOptimizations(context.packageName) == true
    } else true

    return Triple(hasNotif, hasExact, isIgnoringBattery)
}

enum class MainScreenOption(val route: String, val title: String) {
    SCHEDULE(Routes.Schedule, "월별 승무근무표"),
    TODAY_WORK(Routes.TodayWork, "오늘의 근무"),
    TRAIN_CREW(Routes.TrainCrewLookup, "열차 승무원 조회"),
    DIA_LOOKUP(Routes.DiaLookup, "승무 다이아 조회"),
    CREW_WORK_STATUS(Routes.CrewWorkStatus, "소속 승무원 근무 현황"),
    NON_OP_STATUS(Routes.NonOpStatus, "비실동 현황 조회")
}

enum class CalendarFontScaleOption(val scale: Float, val title: String) {
    SMALL(0.85f, "작게"),
    NORMAL(1.0f, "보통"),
    LARGE(1.15f, "크게")
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen() {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("crewmate_prefs", Context.MODE_PRIVATE) }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            WorkAlarmScheduler.scheduleTestAlarm(context)
            android.widget.Toast.makeText(context, "3초 뒤 테스트 알람이 울립니다. 화면을 끄고 확인해 보세요.", android.widget.Toast.LENGTH_LONG).show()
        } else {
            android.widget.Toast.makeText(context, "알림 권한이 허용되지 않았습니다.", android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    var isSyncEnabled by remember { mutableStateOf(prefs.getBoolean("calendar_sync", false)) }

    var selectedMainScreen by remember {
        mutableStateOf(
            MainScreenOption.entries.find { it.name == prefs.getString("main_screen", MainScreenOption.SCHEDULE.name) }
                ?: MainScreenOption.SCHEDULE
        )
    }

    var selectedFontScale by remember {
        mutableStateOf(
            CalendarFontScaleOption.entries.find { it.name == prefs.getString("calendar_font_scale", CalendarFontScaleOption.NORMAL.name) }
                ?: CalendarFontScaleOption.NORMAL
        )
    }

    var selectedThemeMode by remember {
        mutableStateOf(
            AppThemeMode.entries.find { it.name == prefs.getString("app_theme", AppThemeMode.SYSTEM.name) }
                ?: AppThemeMode.SYSTEM
        )
    }

    var hasPermission by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED)
    }

    var hideSandwichedGamsi by remember { mutableStateOf(prefs.getBoolean("hide_sandwiched_gamsi", true)) }

    var midnightAutoCapture by remember { mutableStateOf(prefs.getBoolean("midnight_auto_capture", false)) }
    var midnightAutoCaptureOption by remember {
        mutableStateOf<DiaCaptureOption>(DiaCaptureOption.fromName(prefs.getString("midnight_auto_capture_option", DiaCaptureOption.TIMELINE_SUMMARY.name)))
    }

    var autoOfflineMode by remember { mutableStateOf(AutoOfflineManager.isEnabled(context)) }
    var isTimeRecordExpanded by remember { mutableStateOf(false) }
    var autoOfflineScheduledSummary by remember {
        mutableStateOf(prefs.getString(AutoOfflineManager.PREF_SCHEDULED_SUMMARY, null) ?: "확인 중...")
    }
    var autoOfflineLastOfflineTime by remember {
        mutableStateOf(prefs.getString(AutoOfflineManager.PREF_LAST_OFFLINE_TIME, null) ?: "기록 없음")
    }
    var autoOfflineLastOnlineTime by remember {
        mutableStateOf(prefs.getString(AutoOfflineManager.PREF_LAST_ONLINE_TIME, null) ?: "기록 없음")
    }
    var autoOfflineReturnDelay by remember {
        mutableStateOf(prefs.getInt(AutoOfflineManager.PREF_RETURN_DELAY_MINUTES, 10))
    }
    var autoOfflineReturnSkipped by remember {
        mutableStateOf(prefs.getBoolean(AutoOfflineManager.PREF_RETURN_SKIPPED, false))
    }
    val coroutineScope = rememberCoroutineScope()

    DisposableEffect(prefs) {
        val listener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { sharedPreferences, key ->
            when (key) {
                AutoOfflineManager.PREF_AUTO_OFFLINE -> {
                    autoOfflineMode = sharedPreferences.getBoolean(AutoOfflineManager.PREF_AUTO_OFFLINE, false)
                }
                AutoOfflineManager.PREF_SCHEDULED_SUMMARY -> {
                    autoOfflineScheduledSummary = sharedPreferences.getString(AutoOfflineManager.PREF_SCHEDULED_SUMMARY, null) ?: "없음"
                }
                AutoOfflineManager.PREF_LAST_OFFLINE_TIME -> {
                    autoOfflineLastOfflineTime = sharedPreferences.getString(AutoOfflineManager.PREF_LAST_OFFLINE_TIME, null) ?: "기록 없음"
                }
                AutoOfflineManager.PREF_LAST_ONLINE_TIME -> {
                    autoOfflineLastOnlineTime = sharedPreferences.getString(AutoOfflineManager.PREF_LAST_ONLINE_TIME, null) ?: "기록 없음"
                }
                AutoOfflineManager.PREF_RETURN_DELAY_MINUTES -> {
                    autoOfflineReturnDelay = sharedPreferences.getInt(AutoOfflineManager.PREF_RETURN_DELAY_MINUTES, 10)
                }
                AutoOfflineManager.PREF_RETURN_SKIPPED -> {
                    autoOfflineReturnSkipped = sharedPreferences.getBoolean(AutoOfflineManager.PREF_RETURN_SKIPPED, false)
                }
            }
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose {
            prefs.unregisterOnSharedPreferenceChangeListener(listener)
        }
    }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            AutoOfflineManager.syncFromDatabase(context.applicationContext)
        }
        autoOfflineScheduledSummary = prefs.getString(AutoOfflineManager.PREF_SCHEDULED_SUMMARY, null) ?: "없음"
        autoOfflineLastOfflineTime = prefs.getString(AutoOfflineManager.PREF_LAST_OFFLINE_TIME, null) ?: "기록 없음"
        autoOfflineLastOnlineTime = prefs.getString(AutoOfflineManager.PREF_LAST_ONLINE_TIME, null) ?: "기록 없음"
        autoOfflineReturnDelay = prefs.getInt(AutoOfflineManager.PREF_RETURN_DELAY_MINUTES, 10)
        autoOfflineReturnSkipped = prefs.getBoolean(AutoOfflineManager.PREF_RETURN_SKIPPED, false)
    }

    var patternDataState by remember { mutableStateOf(com.example.crewmate.schedule.WorkPatternSettings.load(context)) }

    val backupExportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            coroutineScope.launch {
                try {
                    val json = com.example.crewmate.data.backup.DataBackupManager.createBackupJson(context)
                    context.contentResolver.openOutputStream(uri)?.use { os ->
                        os.write(json.toByteArray(Charsets.UTF_8))
                    }
                    android.widget.Toast.makeText(context, "데이터 백업 파일이 저장되었습니다.", android.widget.Toast.LENGTH_SHORT).show()
                } catch (e: Exception) {
                    android.util.Log.e("SettingsScreen", "Failed to export backup", e)
                    android.widget.Toast.makeText(context, "백업 내보내기 실패: ${e.localizedMessage}", android.widget.Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    val backupImportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            coroutineScope.launch {
                try {
                    val json = context.contentResolver.openInputStream(uri)?.use { stream ->
                        stream.bufferedReader(Charsets.UTF_8).readText()
                    }
                    if (json.isNullOrBlank()) {
                        android.widget.Toast.makeText(context, "선택한 백업 파일이 비어 있습니다.", android.widget.Toast.LENGTH_SHORT).show()
                        return@launch
                    }
                    val result = com.example.crewmate.data.backup.DataBackupManager.restoreBackupJson(context, json)
                    if (result.isSuccess) {
                        patternDataState = com.example.crewmate.schedule.WorkPatternSettings.load(context)
                        android.widget.Toast.makeText(
                            context,
                            "복원 완료: 메모 ${result.memoCount}건, 연차 ${result.annualLeaveCount + result.carriedOverCount}건, 설정 ${result.preferenceCount}개",
                            android.widget.Toast.LENGTH_LONG
                        ).show()
                    } else {
                        android.widget.Toast.makeText(context, "복원 실패: ${result.errorMessage}", android.widget.Toast.LENGTH_LONG).show()
                    }
                } catch (e: Exception) {
                    android.util.Log.e("SettingsScreen", "Failed to import backup", e)
                    android.widget.Toast.makeText(context, "복원 가져오기 실패: ${e.localizedMessage}", android.widget.Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
        hasPermission = isGranted
        if (isGranted) {
            isSyncEnabled = true
            prefs.edit().putBoolean("calendar_sync", true).apply()
        } else {
            isSyncEnabled = false
            prefs.edit().putBoolean("calendar_sync", false).apply()
        }
    }

    val overrideDate by com.example.crewmate.util.AppDateProvider.overrideDate.collectAsState()

    var adminTapCount by remember { mutableIntStateOf(0) }
    var lastAdminTapTime by remember { mutableLongStateOf(0L) }
    var showAdminPasswordDialog by remember { mutableStateOf(false) }
    var adminPasswordInput by remember { mutableStateOf("") }
    var adminPasswordError by remember { mutableStateOf(false) }
    var showCustomDatePicker by remember { mutableStateOf(false) }

    var settingsTapCount by remember { mutableStateOf(0) }
    var settingsLastTapMs by remember { mutableStateOf(0L) }
    var showLabUnlockDialog by remember { mutableStateOf(false) }
    val labsUnlocked = com.example.crewmate.labs.rememberLabsUnlocked()

    if (showLabUnlockDialog) {
        AlertDialog(
            onDismissRequest = { showLabUnlockDialog = false },
            title = {
                Text("🧪 실험실 (CrewMate Labs)", fontWeight = FontWeight.ExtraBold)
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Surface(
                        shape = MaterialTheme.shapes.medium,
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.25f))
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("실험실 기능 활성화", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                Text(
                                    "정식 도입 전 실험 기능을 켜고 끌 수 있습니다.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = labsUnlocked,
                                onCheckedChange = { checked ->
                                    com.example.crewmate.labs.LabsManager.setLabsUnlocked(context, checked)
                                }
                            )
                        }
                    }
                    Text(
                        text = if (labsUnlocked) {
                            "✅ 실험실이 활성화되었습니다.\n좌측 상단 ☰ 메뉴의 '설정' 아래에서 '실험실' 기능을 확인하실 수 있습니다."
                        } else {
                            "실험실 기능을 활성화하면 좌측 상단 ☰ 메뉴의 '설정' 아래에 '실험실' 메뉴가 나타납니다."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showLabUnlockDialog = false }) {
                    Text("닫기", fontWeight = FontWeight.Bold)
                }
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Text(
            text = "설정",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.ExtraBold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.clickable(
                interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                indication = null
            ) {
                val now = System.currentTimeMillis()
                if (now - settingsLastTapMs > 2000L) {
                    settingsTapCount = 1
                } else {
                    settingsTapCount += 1
                    if (settingsTapCount >= 5) {
                        settingsTapCount = 0
                        showLabUnlockDialog = true
                    }
                }
                settingsLastTapMs = now
            }
        )
        Spacer(Modifier.height(16.dp))

        // 기기 캘린더 연동 카드
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("스마트폰 캘린더 일정 동기화", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "근무 일정을 기기 기본 캘린더에 자동으로 동기화합니다.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(Modifier.width(16.dp))
                Switch(
                    checked = isSyncEnabled,
                    onCheckedChange = { checked ->
                        if (checked) {
                            if (!hasPermission) {
                                permissionLauncher.launch(Manifest.permission.READ_CALENDAR)
                            } else {
                                isSyncEnabled = true
                                prefs.edit().putBoolean("calendar_sync", true).apply()
                            }
                        } else {
                            isSyncEnabled = false
                            prefs.edit().putBoolean("calendar_sync", false).apply()
                        }
                    },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = MaterialTheme.colorScheme.primary
                    )
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        // 첫 화면 설정 카드
        var mainDropdownExpanded by remember { mutableStateOf(false) }

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
            Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                Text("앱 실행 시 첫 화면 설정", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                Spacer(Modifier.height(4.dp))
                Text(
                    "앱이 켜질 때 가장 먼저 보여줄 화면을 선택합니다.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(12.dp))

                ExposedDropdownMenuBox(
                    expanded = mainDropdownExpanded,
                    onExpandedChange = { mainDropdownExpanded = !mainDropdownExpanded },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedTextField(
                        value = selectedMainScreen.title,
                        onValueChange = {},
                        readOnly = true,
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = mainDropdownExpanded) },
                        modifier = Modifier
                            .menuAnchor()
                            .fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = MaterialTheme.colorScheme.onSurface,
                            unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                            focusedBorderColor = MaterialTheme.colorScheme.outline,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                            focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    )
                    ExposedDropdownMenu(
                        expanded = mainDropdownExpanded,
                        onDismissRequest = { mainDropdownExpanded = false },
                        modifier = Modifier.background(MaterialTheme.colorScheme.surface)
                    ) {
                        MainScreenOption.entries.forEach { option ->
                            DropdownMenuItem(
                                text = { Text(text = option.title, color = MaterialTheme.colorScheme.onSurface) },
                                onClick = {
                                    selectedMainScreen = option
                                    prefs.edit().putString("main_screen", option.name).apply()
                                    mainDropdownExpanded = false
                                }
                            )
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        // 근무표 글씨 크기 설정 카드
        var fontDropdownExpanded by remember { mutableStateOf(false) }

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
            Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                Text("근무표 글씨 크기 설정", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                Spacer(Modifier.height(4.dp))
                Text(
                    "월별 승무근무표 달력에 표시되는 글씨의 크기를 조절합니다.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(12.dp))

                ExposedDropdownMenuBox(
                    expanded = fontDropdownExpanded,
                    onExpandedChange = { fontDropdownExpanded = !fontDropdownExpanded },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedTextField(
                        value = selectedFontScale.title,
                        onValueChange = {},
                        readOnly = true,
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = fontDropdownExpanded) },
                        modifier = Modifier
                            .menuAnchor()
                            .fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = MaterialTheme.colorScheme.onSurface,
                            unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                            focusedBorderColor = MaterialTheme.colorScheme.outline,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                            focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    )
                    ExposedDropdownMenu(
                        expanded = fontDropdownExpanded,
                        onDismissRequest = { fontDropdownExpanded = false },
                        modifier = Modifier.background(MaterialTheme.colorScheme.surface)
                    ) {
                        CalendarFontScaleOption.entries.forEach { option ->
                            DropdownMenuItem(
                                text = { Text(text = option.title, color = MaterialTheme.colorScheme.onSurface) },
                                onClick = {
                                    selectedFontScale = option
                                    prefs.edit().putString("calendar_font_scale", option.name).apply()
                                    fontDropdownExpanded = false
                                }
                            )
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        // --- 1. 오늘의 근무 자동 캡쳐 (자정) ---
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.large,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
            Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("오늘의 근무 자동 캡쳐", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "매일 자정(00:01)에 당일 근무 다이아를 갤러리에 자동으로 저장합니다.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(Modifier.width(16.dp))
                    Switch(
                        checked = midnightAutoCapture,
                        onCheckedChange = { checked ->
                            midnightAutoCapture = checked
                            prefs.edit().putBoolean("midnight_auto_capture", checked).apply()
                            if (checked) {
                                com.example.crewmate.alarm.MidnightCaptureReceiver.scheduleMidnightCapture(context)
                                android.widget.Toast.makeText(context, "자정 자동 캡처가 활성화되었습니다.", android.widget.Toast.LENGTH_SHORT).show()
                            } else {
                                com.example.crewmate.alarm.MidnightCaptureReceiver.cancelMidnightCapture(context)
                                android.widget.Toast.makeText(context, "자정 자동 캡처가 비활성화되었습니다.", android.widget.Toast.LENGTH_SHORT).show()
                            }
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = MaterialTheme.colorScheme.primary
                        )
                    )
                }

                if (midnightAutoCapture) {
                    Spacer(Modifier.height(12.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    Spacer(Modifier.height(12.dp))

                    Text(
                        "자동 캡처 형태 선택",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        DiaCaptureOption.entries.forEach { opt ->
                            val isSelected = midnightAutoCaptureOption == opt
                            Surface(
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable {
                                        midnightAutoCaptureOption = opt
                                        prefs.edit().putString("midnight_auto_capture_option", opt.name).apply()
                                    },
                                shape = RoundedCornerShape(8.dp),
                                color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                border = BorderStroke(
                                    1.dp,
                                    if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent
                                )
                            ) {
                                Column(
                                    modifier = Modifier.padding(vertical = 10.dp, horizontal = 10.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(
                                        text = opt.title,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        fontSize = 13.sp,
                                        color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Spacer(Modifier.height(2.dp))
                                    Text(
                                        text = if (opt == DiaCaptureOption.TIMELINE_SUMMARY) "운전/승무 위주 요약" else "휴게/승계 전 구간 포함",
                                        fontSize = 11.sp,
                                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        // --- 2. 운전 시간 자동 오프라인 전환 설정 ---
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.large,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
            Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("운전 시간 자동 오프라인 전환", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "주요 업무(운전·승무·출고준비·입환) 시작 1분 전(직전 감시 포함) 자동으로 오프라인으로 전환하고, 정리/입고정리 시작 시 온라인으로 복귀합니다.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(Modifier.width(16.dp))
                    Switch(
                        checked = autoOfflineMode,
                        onCheckedChange = { checked ->
                            autoOfflineMode = checked
                            prefs.edit().putBoolean(AutoOfflineManager.PREF_AUTO_OFFLINE, checked).apply()
                            coroutineScope.launch(Dispatchers.IO) {
                                AutoOfflineManager.syncFromDatabase(context.applicationContext)
                            }
                            android.widget.Toast.makeText(
                                context,
                                if (checked) "운전 시간 자동 오프라인 모드가 활성화되었습니다." else "운전 시간 자동 오프라인 모드가 비활성화되었습니다.",
                                android.widget.Toast.LENGTH_SHORT
                            ).show()
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = MaterialTheme.colorScheme.primary
                        )
                    )
                }

                if (autoOfflineMode) {
                    Spacer(Modifier.height(12.dp))

                    Text(
                        text = "⏱️ 온라인 복귀 시점 (정리/입고정리 시작 후)",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.height(4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        val delays = listOf(0 to "0분", 5 to "5분", 10 to "10분", 15 to "15분", 20 to "20분")
                        delays.forEach { (mins, label) ->
                            val selected = autoOfflineReturnDelay == mins
                            FilterChip(
                                selected = selected,
                                onClick = {
                                    autoOfflineReturnDelay = mins
                                    prefs.edit().putInt(AutoOfflineManager.PREF_RETURN_DELAY_MINUTES, mins).apply()
                                    coroutineScope.launch(Dispatchers.IO) {
                                        AutoOfflineManager.syncFromDatabase(context.applicationContext)
                                    }
                                    android.widget.Toast.makeText(context, "온라인 복귀 시점이 [정리/입고정리 +${mins}분]으로 설정되었습니다.", android.widget.Toast.LENGTH_SHORT).show()
                                },
                                label = {
                                    Text(
                                        text = label,
                                        modifier = Modifier.fillMaxWidth(),
                                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                        fontSize = 10.sp,
                                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                                    )
                                },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    Spacer(Modifier.height(12.dp))

                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        Column(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { isTimeRecordExpanded = !isTimeRecordExpanded },
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "📋 자동 오프라인/온라인 전환 시각 기록",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = if (isTimeRecordExpanded) "접기 ▲" else "펼치기 ▼",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }

                            if (isTimeRecordExpanded) {
                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, thickness = 0.5.dp)

                                Column(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Text("예정 스케줄", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text(
                                        text = autoOfflineScheduledSummary,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        lineHeight = 16.sp
                                    )
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("최근 오프라인 전환 시각", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text(
                                        text = autoOfflineLastOfflineTime,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFFDC2626)
                                    )
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("최근 온라인 복귀 시각", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text(
                                        text = autoOfflineLastOnlineTime,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF0D9488)
                                    )
                                }

                                Spacer(Modifier.height(4.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    OutlinedButton(
                                        onClick = {
                                            coroutineScope.launch {
                                                withContext(Dispatchers.IO) {
                                                    AutoOfflineManager.syncFromDatabase(context.applicationContext)
                                                }
                                                autoOfflineScheduledSummary = prefs.getString(AutoOfflineManager.PREF_SCHEDULED_SUMMARY, null) ?: "없음"
                                                android.widget.Toast.makeText(context, "당일 자동 오프라인 스케줄을 재계산했습니다.", android.widget.Toast.LENGTH_SHORT).show()
                                            }
                                        },
                                        modifier = Modifier.weight(1f).height(32.dp),
                                        shape = RoundedCornerShape(8.dp),
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                                    ) {
                                        Text("🔄 스케줄 재계산", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }
                                    OutlinedButton(
                                        onClick = {
                                            prefs.edit()
                                                .remove(AutoOfflineManager.PREF_LAST_OFFLINE_TIME)
                                                .remove(AutoOfflineManager.PREF_LAST_ONLINE_TIME)
                                                .remove(AutoOfflineManager.PREF_LAST_TRIGGERED_WINDOW)
                                                .apply()
                                            autoOfflineLastOfflineTime = "기록 없음"
                                            autoOfflineLastOnlineTime = "기록 없음"
                                            android.widget.Toast.makeText(context, "전환 시각 기록이 초기화되었습니다.", android.widget.Toast.LENGTH_SHORT).show()
                                        },
                                        modifier = Modifier.weight(1f).height(32.dp),
                                        shape = RoundedCornerShape(8.dp),
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                                    ) {
                                        Text("🗑️ 기록 초기화", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        // --- SubwayNowBot 텔레그램 열차 추적 연동 카드 ---
        com.example.crewmate.telegram.ui.SubwayNowBotSettingsCard()

        Spacer(Modifier.height(16.dp))

        // --- 3. 근무 미제공 달 맞춤형 순환 패턴 인터랙티브 빌더 ---
        var showDatePickerDialog by remember { mutableStateOf(false) }

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.large,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
            Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("미배정 달 순환 패턴 표시", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "서버에 근무표가 없는 달에 사용자가 정한 순환 패턴([주간], [야간], [~], [S])을 달력에 표시합니다.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Switch(
                        checked = patternDataState.enabled,
                        onCheckedChange = { checked ->
                            val updated = patternDataState.copy(enabled = checked)
                            patternDataState = updated
                            com.example.crewmate.schedule.WorkPatternSettings.save(context, updated)
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = MaterialTheme.colorScheme.primary
                        )
                    )
                }

                if (patternDataState.enabled) {
                    Spacer(Modifier.height(14.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, thickness = 0.5.dp)
                    Spacer(Modifier.height(12.dp))

                    // 기준 일자 선택
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("기준 일자", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        OutlinedButton(
                            onClick = { showDatePickerDialog = true },
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            modifier = Modifier.height(34.dp)
                        ) {
                            Text(patternDataState.anchorDate.format(DateTimeFormatter.ofPattern("yyyy-MM-dd (E)", Locale.KOREAN)), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    Spacer(Modifier.height(12.dp))
                    Text("근무 뱃지 팔레트 (터치하여 패턴에 추가)", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                    Spacer(Modifier.height(6.dp))

                    // 4대 필수 뱃지 팔레트 ([주간], [야간], [~], [S])
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        com.example.crewmate.schedule.WorkPatternSettings.AVAILABLE_BADGES.forEach { badge ->
                            val isNight = badge == "야간" || badge == "~"
                            val isRest = badge == "S"
                            val bg = if (isRest) MaterialTheme.colorScheme.primaryContainer else if (isNight) Color(0xFF333333) else Color(0xFFFFD54F)
                            val fg = if (isRest) MaterialTheme.colorScheme.primary else if (isNight) Color.White else Color.Black

                            Surface(
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable {
                                        val newList = patternDataState.patternList + badge
                                        val updated = patternDataState.copy(patternList = newList)
                                        patternDataState = updated
                                        com.example.crewmate.schedule.WorkPatternSettings.save(context, updated)
                                    },
                                color = bg,
                                shape = RoundedCornerShape(8.dp),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                            ) {
                                Box(modifier = Modifier.padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
                                    Text("+ $badge", fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, color = fg)
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(14.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("설정된 패턴 순서 (${patternDataState.patternList.size}일 주기)", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                        if (patternDataState.patternList.isNotEmpty()) {
                            TextButton(
                                onClick = {
                                    val updated = patternDataState.copy(patternList = emptyList())
                                    patternDataState = updated
                                    com.example.crewmate.schedule.WorkPatternSettings.save(context, updated)
                                },
                                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp),
                                modifier = Modifier.height(26.dp)
                            ) {
                                Text("전체 삭제", fontSize = 11.sp, color = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                    Spacer(Modifier.height(6.dp))

                    if (patternDataState.patternList.isEmpty()) {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                "위 팔레트에서 뱃지를 터치하여 교대 순서를 추가해 주세요.\n(예: 주간 -> 야간 -> ~ -> 주간 -> S -> S)",
                                modifier = Modifier.padding(12.dp),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else {
                        // 순환 패턴 뱃지 목록 (터치 시 삭제)
                        androidx.compose.foundation.layout.FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            patternDataState.patternList.forEachIndexed { index, badge ->
                                val isNight = badge == "야간" || badge == "~"
                                val isRest = badge == "S"
                                val bg = if (isRest) MaterialTheme.colorScheme.primaryContainer else if (isNight) Color(0xFF333333) else Color(0xFFFFD54F)
                                val fg = if (isRest) MaterialTheme.colorScheme.primary else if (isNight) Color.White else Color.Black

                                Surface(
                                    modifier = Modifier.clickable {
                                        val mutable = patternDataState.patternList.toMutableList()
                                        mutable.removeAt(index)
                                        val updated = patternDataState.copy(patternList = mutable)
                                        patternDataState = updated
                                        com.example.crewmate.schedule.WorkPatternSettings.save(context, updated)
                                    },
                                    color = bg,
                                    shape = RoundedCornerShape(6.dp),
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(badge, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = fg)
                                        Spacer(Modifier.width(4.dp))
                                        Text("✕", fontSize = 10.sp, color = fg.copy(alpha = 0.7f))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        if (showDatePickerDialog) {
            val currentYear = patternDataState.anchorDate.year
            val currentMonth = patternDataState.anchorDate.monthValue - 1
            val currentDay = patternDataState.anchorDate.dayOfMonth

            android.app.DatePickerDialog(
                context,
                { _, year, month, dayOfMonth ->
                    val selected = LocalDate.of(year, month + 1, dayOfMonth)
                    val updated = patternDataState.copy(anchorDate = selected)
                    patternDataState = updated
                    com.example.crewmate.schedule.WorkPatternSettings.save(context, updated)
                    showDatePickerDialog = false
                },
                currentYear,
                currentMonth,
                currentDay
            ).apply {
                setOnDismissListener { showDatePickerDialog = false }
                show()
            }
        }

        Spacer(Modifier.height(16.dp))

        // --- 4. 데이터 백업 및 복원 ---
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.large,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
            Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                Text(
                    text = "💾 데이터 백업 및 복원",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "기기 변경이나 앱 재설치 시에도 작성한 메모, 연차/이월연차 정보, 앱 설정값 등을 안전하게 보존하고 가져올 수 있습니다. (계정 암호 등 민감 정보는 백업 파일에 포함되지 않습니다)",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(14.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = {
                            val timeStr = java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd_HHmm"))
                            backupExportLauncher.launch("CrewMate_Backup_$timeStr.json")
                        },
                        modifier = Modifier.weight(1f).height(40.dp),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Text("💾 백업 내보내기", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    }

                    OutlinedButton(
                        onClick = {
                            backupImportLauncher.launch(arrayOf("application/json", "*/*"))
                        },
                        modifier = Modifier.weight(1f).height(40.dp),
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary)
                    ) {
                        Text("📂 복원 가져오기", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        // 감시 숨기기 카드
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.large,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("주요 항목 사이 '감시' 숨기기", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "운전, 출고준비, 입환, 승무 등 주요 항목 사이에 낀 '감시' 구간을 기본적으로 숨깁니다.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(Modifier.width(16.dp))
                Switch(
                    checked = hideSandwichedGamsi,
                    onCheckedChange = { checked ->
                        hideSandwichedGamsi = checked
                        prefs.edit().putBoolean("hide_sandwiched_gamsi", checked).apply()
                    },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = MaterialTheme.colorScheme.primary
                    )
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        // 앱 테마 설정 카드
        var themeDropdownExpanded by remember { mutableStateOf(false) }

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clickable {
                    val now = System.currentTimeMillis()
                    if (now - lastAdminTapTime < 1000) {
                        adminTapCount++
                    } else {
                        adminTapCount = 1
                    }
                    lastAdminTapTime = now

                    if (adminTapCount >= 5) {
                        adminTapCount = 0
                        adminPasswordInput = ""
                        adminPasswordError = false
                        showAdminPasswordDialog = true
                    }
                },
            shape = MaterialTheme.shapes.large,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
            Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                Text("앱 테마 설정", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                Spacer(Modifier.height(4.dp))
                Text(
                    "화면 모드를 설정합니다. (라이트 / 다크 / 시스템 설정 동기화)",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(12.dp))

                ExposedDropdownMenuBox(
                    expanded = themeDropdownExpanded,
                    onExpandedChange = { themeDropdownExpanded = !themeDropdownExpanded },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedTextField(
                        value = selectedThemeMode.title,
                        onValueChange = {},
                        readOnly = true,
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = themeDropdownExpanded) },
                        modifier = Modifier
                            .menuAnchor()
                            .fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = MaterialTheme.colorScheme.onSurface,
                            unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                            focusedBorderColor = MaterialTheme.colorScheme.outline,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                            focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    )
                    ExposedDropdownMenu(
                        expanded = themeDropdownExpanded,
                        onDismissRequest = { themeDropdownExpanded = false },
                        modifier = Modifier.background(MaterialTheme.colorScheme.surface)
                    ) {
                        AppThemeMode.entries.forEach { mode ->
                            DropdownMenuItem(
                                text = { Text(text = mode.title, color = MaterialTheme.colorScheme.onSurface) },
                                onClick = {
                                    selectedThemeMode = mode
                                    prefs.edit().putString("app_theme", mode.name).apply()
                                    themeDropdownExpanded = false
                                }
                            )
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))
        var showAppInfoDialog by remember { mutableStateOf(false) }

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { showAppInfoDialog = true },
            shape = MaterialTheme.shapes.large,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("앱 정보 및 만든 이", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "CrewMate 앱에 대한 정보와 안내 사항을 확인합니다.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text("›", fontSize = 20.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        if (showAppInfoDialog) {
            AlertDialog(
                onDismissRequest = { showAppInfoDialog = false },
                title = { Text("ℹ️ 앱 정보", fontWeight = FontWeight.Bold) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("CrewMate (크루메이트) v1.0.10", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        Text("한국철도공사 코레일 수도권동부본부 승무원 전용 스마트 오프라인 통합 업무 지원 솔루션입니다.")
                        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                        Text("• 기기 캘린더 자동 동기화 지원")
                        Text("• 근무표 / 다이아 / 출무현황 오프라인 캐싱")
                        Text("• 초정밀 알람 및 워치 진동 알림 엔진 탑재")
                        Text("• 운전 시간 자동 오프라인 전환 및 QA 관리")
                        Text("• 미조회 월 순환 근무 패턴 빌더 탑재")
                        Text("• 다이아 전체/요약 무결점 캡처 및 데이터 백업/복원")
                        Spacer(Modifier.height(4.dp))
                        Text("만든 이: 승무원 pair programming 프로젝트", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showAppInfoDialog = false }) {
                        Text("닫기", fontWeight = FontWeight.Bold)
                    }
                }
            )
        }

        if (showAdminPasswordDialog) {
            AlertDialog(
                onDismissRequest = {
                    showAdminPasswordDialog = false
                    adminPasswordInput = ""
                    adminPasswordError = false
                },
                containerColor = MaterialTheme.colorScheme.surface,
                shape = MaterialTheme.shapes.extraLarge,
                tonalElevation = 6.dp,
                title = { Text("🛠️ 관리자 디버그 모드", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("가상 날짜 설정 메뉴에 진입하려면 비밀번호 4자리를 입력하세요.", fontSize = 13.sp)
                        BasicTextField(
                            value = adminPasswordInput,
                            onValueChange = {
                                adminPasswordInput = it
                                adminPasswordError = false
                            },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp))
                                .border(1.dp, if (adminPasswordError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp))
                                .padding(12.dp)
                        )
                        if (adminPasswordError) {
                            Text("비밀번호가 올바르지 않습니다.", color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                        }
                    }
                },
                confirmButton = {
                    Button(onClick = {
                        if (adminPasswordInput.trim() == "5078") {
                            showAdminPasswordDialog = false
                            adminPasswordInput = ""
                            adminPasswordError = false
                            showCustomDatePicker = true
                        } else {
                            adminPasswordError = true
                        }
                    }) {
                        Text("확인")
                    }
                },
                dismissButton = {
                    TextButton(onClick = {
                        showAdminPasswordDialog = false
                        adminPasswordInput = ""
                        adminPasswordError = false
                    }) {
                        Text("취소")
                    }
                }
            )
        }

        if (showCustomDatePicker) {
            AlertDialog(
                onDismissRequest = { showCustomDatePicker = false },
                containerColor = MaterialTheme.colorScheme.surface,
                shape = MaterialTheme.shapes.extraLarge,
                tonalElevation = 6.dp,
                title = { Text("📅 가상 오늘 날짜 지정 (디버그)", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            text = if (overrideDate != null) "현재 가상 날짜: $overrideDate" else "현재 실제 날짜 사용 중 (${LocalDate.now()})",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (overrideDate != null) Color(0xFFDC2626) else MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            "선택한 날짜로 오늘 날짜를 강제 변경하여 '오늘의 근무' 및 '다이아' 화면을 테스트합니다.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Spacer(Modifier.height(4.dp))

                        Button(
                            onClick = {
                                val current = overrideDate ?: LocalDate.now()
                                android.app.DatePickerDialog(
                                    context,
                                    { _, year, month, dayOfMonth ->
                                        val picked = LocalDate.of(year, month + 1, dayOfMonth)
                                        com.example.crewmate.util.AppDateProvider.setOverrideDate(picked)
                                        showCustomDatePicker = false
                                    },
                                    current.year,
                                    current.monthValue - 1,
                                    current.dayOfMonth
                                ).show()
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("🗓️ 가상 날짜 선택 (달력 열기)")
                        }

                        if (overrideDate != null) {
                            OutlinedButton(
                                onClick = {
                                    com.example.crewmate.util.AppDateProvider.setOverrideDate(null)
                                    showCustomDatePicker = false
                                },
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                            ) {
                                Text("🔄 실제 날짜로 초기화")
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showCustomDatePicker = false }) {
                        Text("닫기")
                    }
                }
            )
        }
    }
}
