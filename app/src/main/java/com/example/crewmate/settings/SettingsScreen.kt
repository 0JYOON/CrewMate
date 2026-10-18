package com.example.crewmate.settings

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.crewmate.alarm.*
import com.example.crewmate.database.CrewMateDatabase
import com.example.crewmate.navigation.Routes
import com.example.crewmate.ui.theme.AppThemeMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private fun checkAlarmPrerequisites(context: Context): Triple<Boolean, Boolean, Boolean> {
    val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? android.app.AlarmManager
    val powerManager = context.getSystemService(Context.POWER_SERVICE) as? android.os.PowerManager

    val hasNotif = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen() {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("crewmate_prefs", Context.MODE_PRIVATE) }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            WorkAlarmScheduler.scheduleTestAlarm(context)
            android.widget.Toast.makeText(context, "3초 후 테스트 알람이 울립니다. 화면을 꺼두고 확인해 보세요.", android.widget.Toast.LENGTH_LONG).show()
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
    var showAdminAuthDialog by remember { mutableStateOf(false) }
    var showAdminDateDialog by remember { mutableStateOf(false) }
    var adminPasswordInput by remember { mutableStateOf("") }
    var passwordError by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        if (overrideDate != null) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp)
                    .clickable { showAdminDateDialog = true },
                color = Color(0xFFDC2626),
                shape = RoundedCornerShape(8.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "🧪 가상 날짜 모드: ${overrideDate?.format(DateTimeFormatter.ofPattern("yyyy-MM-dd (E)", Locale.KOREAN))}",
                        fontWeight = FontWeight.ExtraBold,
                        color = Color.White,
                        fontSize = 13.sp
                    )
                    Text(
                        text = "관리자 설정 ⚙️",
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        fontSize = 12.sp
                    )
                }
            }
        }
        Text(
            text = "설정",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.ExtraBold,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(Modifier.height(16.dp))

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
                    Text("기기 캘린더 연동", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "스마트폰의 개인 일정을 달력과 상세 화면에 표시합니다. 연동을 해제하면 더 이상 표시되지 않습니다.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(Modifier.width(16.dp))
                Switch(
                    checked = isSyncEnabled && hasPermission,
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

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.large,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
            Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                Text("시작 화면 설정", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                Spacer(Modifier.height(4.dp))
                Text(
                    "로그인 성공 시 가장 먼저 표시할 화면을 선택합니다.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(12.dp))

                var dropdownExpanded by remember { mutableStateOf(false) }

                ExposedDropdownMenuBox(
                    expanded = dropdownExpanded,
                    onExpandedChange = { dropdownExpanded = !dropdownExpanded }
                ) {
                    OutlinedTextField(
                        value = selectedMainScreen.title,
                        onValueChange = {},
                        readOnly = true,
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = dropdownExpanded) },
                        modifier = Modifier.fillMaxWidth().menuAnchor(),
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
                        expanded = dropdownExpanded,
                        onDismissRequest = { dropdownExpanded = false },
                        modifier = Modifier.background(MaterialTheme.colorScheme.surface)
                    ) {
                        MainScreenOption.entries.forEach { option ->
                            DropdownMenuItem(
                                text = { Text(text = option.title, color = MaterialTheme.colorScheme.onSurface) },
                                onClick = {
                                    selectedMainScreen = option
                                    prefs.edit().putString("main_screen", option.name).apply()
                                    dropdownExpanded = false
                                }
                            )
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.large,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
            Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                Text("캘린더 글자 크기 설정", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                Spacer(Modifier.height(4.dp))
                Text(
                    "월별 승무근무표 내 글자 크기를 조절합니다.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(12.dp))

                var fontDropdownExpanded by remember { mutableStateOf(false) }

                ExposedDropdownMenuBox(
                    expanded = fontDropdownExpanded,
                    onExpandedChange = { fontDropdownExpanded = !fontDropdownExpanded }
                ) {
                    OutlinedTextField(
                        value = selectedFontScale.title,
                        onValueChange = {},
                        readOnly = true,
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = fontDropdownExpanded) },
                        modifier = Modifier.fillMaxWidth().menuAnchor(),
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
                        "운전, 출고준비, 입환, 승무 등 주요 항목 사이에 낀 '감시' 구간을 기본적으로 접어둡니다.",
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

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clickable {
                    val now = System.currentTimeMillis()
                    if (now - lastAdminTapTime < 2000) {
                        adminTapCount++
                    } else {
                        adminTapCount = 1
                    }
                    lastAdminTapTime = now

                    if (adminTapCount >= 5) {
                        adminTapCount = 0
                        adminPasswordInput = ""
                        passwordError = false
                        showAdminAuthDialog = true
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
                    "화면 테마(다크 모드 / 라이트 모드 / 시스템 설정)를 변경합니다.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(12.dp))

                var themeDropdownExpanded by remember { mutableStateOf(false) }

                ExposedDropdownMenuBox(
                    expanded = themeDropdownExpanded,
                    onExpandedChange = { themeDropdownExpanded = !themeDropdownExpanded }
                ) {
                    OutlinedTextField(
                        value = selectedThemeMode.title,
                        onValueChange = {},
                        readOnly = true,
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = themeDropdownExpanded) },
                        modifier = Modifier.fillMaxWidth().menuAnchor(),
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
                Text("〉", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        if (showAppInfoDialog) {
            AlertDialog(
                onDismissRequest = { showAppInfoDialog = false },
                containerColor = MaterialTheme.colorScheme.surface,
                title = {
                    Text(
                        text = "앱 정보",
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                },
                text = {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState())
                    ) {
                        Text(
                            text = "이 어플리케이션은 제작자 개인의 업무 편의를 목적으로 개발된 비공식 앱입니다.\n기능상 다소 오류가 있거나 예고 없이 서비스가 중단될 수 있으니 너른 양해 부탁드립니다.\n\n본 앱에서 제공하는 모든 조회 결과 및 데이터는 참고용일 뿐입니다.\n앱의 정보 오류로 인해 발생한 근태 문제나 불이익 등에 대해서도 제작자는 책임지지 않습니다.\n정확한 근무 내역은 반드시 공식 시스템을 통해 확인하시기 바랍니다.\n\n문의사항이 있다면 사내 파발마(업무메일)로 전달 부탁드립니다.\n다만, 일일이 답장을 드리거나 즉각적인 조치는 어려울 수 있는 점 미리 사과드립니다.\n\n안산승무 윤영준 드림",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            lineHeight = 20.sp
                        )
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showAppInfoDialog = false }) {
                        Text("확인", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    }
                }
            )
        }

        if (showAdminAuthDialog) {
            AlertDialog(
                onDismissRequest = { showAdminAuthDialog = false },
                containerColor = MaterialTheme.colorScheme.surface,
                title = { Text("🔒 관리자 인증", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("가상 날짜 디버그 모드 진입을 위해 비밀번호를 입력해 주세요.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface)
                        OutlinedTextField(
                            value = adminPasswordInput,
                            onValueChange = {
                                adminPasswordInput = it
                                passwordError = false
                            },
                            label = { Text("비밀번호") },
                            placeholder = { Text("비밀번호 4자리") },
                            singleLine = true,
                            isError = passwordError,
                            visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.fillMaxWidth()
                        )
                        if (passwordError) {
                            Text("비밀번호가 올바르지 않습니다.", color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                        }
                    }
                },
                confirmButton = {
                    Button(onClick = {
                        if (adminPasswordInput.trim() == "5078") {
                            showAdminAuthDialog = false
                            showAdminDateDialog = true
                        } else {
                            passwordError = true
                        }
                    }) {
                        Text("확인", fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showAdminAuthDialog = false }) {
                        Text("취소")
                    }
                }
            )
        }

        if (showAdminDateDialog) {
            val currentVirtualDate = overrideDate ?: LocalDate.now()
            AlertDialog(
                onDismissRequest = { showAdminDateDialog = false },
                containerColor = MaterialTheme.colorScheme.surface,
                title = { Text("🧪 관리자 가상 날짜 설정", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary) },
                text = {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = if (overrideDate != null) "현재 가상 날짜: ${overrideDate?.format(DateTimeFormatter.ofPattern("yyyy-MM-dd (E)", Locale.KOREAN))}"
                            else "현재 상태: 실제 오늘 날짜 사용 중 (${LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd (E)", Locale.KOREAN))})",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = if (overrideDate != null) Color(0xFFDC2626) else MaterialTheme.colorScheme.onSurface
                        )

                        Button(
                            onClick = {
                                android.app.DatePickerDialog(
                                    context,
                                    { _, year, month, day ->
                                        val selected = LocalDate.of(year, month + 1, day)
                                        com.example.crewmate.util.AppDateProvider.setCustomDate(selected)
                                        android.widget.Toast.makeText(context, "가상 날짜가 ${selected}로 설정되었습니다.", android.widget.Toast.LENGTH_SHORT).show()
                                        showAdminDateDialog = false
                                    },
                                    currentVirtualDate.year,
                                    currentVirtualDate.monthValue - 1,
                                    currentVirtualDate.dayOfMonth
                                ).show()
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("📅 가상 날짜 선택 (DatePicker)", fontWeight = FontWeight.Bold)
                        }

                        if (overrideDate != null) {
                            OutlinedButton(
                                onClick = {
                                    com.example.crewmate.util.AppDateProvider.resetToRealToday()
                                    android.widget.Toast.makeText(context, "실제 오늘 날짜로 복구되었습니다.", android.widget.Toast.LENGTH_SHORT).show()
                                    showAdminDateDialog = false
                                },
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                            ) {
                                Text("↺ 실제 오늘 날짜로 복구", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showAdminDateDialog = false }) {
                        Text("닫기", fontWeight = FontWeight.Bold)
                    }
                }
            )
        }
    }
}
