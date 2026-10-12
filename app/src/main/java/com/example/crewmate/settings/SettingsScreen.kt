package com.example.crewmate.settings

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.crewmate.navigation.Routes
import com.example.crewmate.ui.theme.AppThemeMode

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

    var hideSandwichedGamsi by remember { mutableStateOf(prefs.getBoolean("hide_sandwiched_gamsi", false)) }

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
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(Modifier.height(16.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
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
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
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
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = MaterialTheme.colorScheme.onSurface,
                            unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
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
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
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
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = MaterialTheme.colorScheme.onSurface,
                            unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
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
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("운전 구간 사이 '감시' 숨기기", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "운전과 운전 사이에 낀 '감시' 구간을 기본적으로 접어둡니다.",
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
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
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
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = MaterialTheme.colorScheme.onSurface,
                            unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
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
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
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
    }
}
