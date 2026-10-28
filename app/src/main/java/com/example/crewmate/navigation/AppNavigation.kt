package com.example.crewmate.navigation

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebStorage
import android.webkit.WebView
import android.webkit.WebViewClient
import com.example.crewmate.BuildConfig
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.*
import com.example.crewmate.data.remote.xcrew.XcrewWebViewSession
import com.example.crewmate.data.remote.xcrew.XcrewWebViewXhrDataSource
import com.example.crewmate.data.remote.xcrew.XcrewWorkScheduleParser
import com.example.crewmate.schedule.TrainCrewLookupV2Screen
import com.example.crewmate.login.LoginScreen
import com.example.crewmate.login.LoginViewModel
import com.example.crewmate.login.LoginViewModelFactory
import com.example.crewmate.login.SecureCredentialStore
import com.example.crewmate.schedule.*
import com.example.crewmate.settings.SettingsScreen
import com.example.crewmate.ui.theme.CrewMateLogo
import kotlinx.coroutines.launch

enum class MainScreenOption(val title: String, val route: String) {
    SCHEDULE("월별 승무근무표", Routes.Schedule),
    TODAY_WORK("오늘의 근무 다이아 확인", Routes.TodayWork),
    TRAIN_CREW_LOOKUP("열차 승무원 조회", Routes.TrainCrewLookup),
    DIA_LOOKUP("승무 다이아 조회", Routes.DiaLookup),
    CREW_WORK_STATUS("소속 승무원 근무 현황", Routes.CrewWorkStatus),
    NON_OP_STATUS("비실동 현황 조회", Routes.NonOpStatus)
}

object Routes {
    const val Login = "login"
    const val Schedule = "schedule"
    const val TodayWork = "today-work"
    const val TrainCrewLookup = "train-crew-lookup"
    const val DiaLookup = "dia-lookup"
    const val CrewWorkStatus = "crew-work-status"
    const val NonOpStatus = "non-op-status"
    const val PersonalSchedule = "personalSchedule"
    const val Settings = "settings"
    const val WorkAlarmSettings = "work-alarm-settings"
}

@Composable
fun AppNavigation(initialTargetRoute: String? = null) {
    val navController = rememberNavController()
    val xcrewSession = remember { XcrewWebViewSession() }
    val appContext = LocalContext.current.applicationContext
    val credentialStore = remember(appContext) { SecureCredentialStore(appContext) }
    var skipAutoLoginOnLoginScreen by remember { mutableStateOf(false) }

    val prefs = appContext.getSharedPreferences("crewmate_prefs", Context.MODE_PRIVATE)
    var isOfflineMode by remember { mutableStateOf(prefs.getBoolean("is_offline_mode", false)) }
    val coroutineScope = rememberCoroutineScope()
    var isPreFetchingOfflineData by remember { mutableStateOf(false) }

    DisposableEffect(prefs) {
        val listener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { sharedPreferences, key ->
            if (key == "is_offline_mode") {
                isOfflineMode = sharedPreferences.getBoolean("is_offline_mode", false)
            }
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose {
            prefs.unregisterOnSharedPreferenceChangeListener(listener)
        }
    }

    val globalCensusManager = remember(xcrewSession, appContext) {
        val dataSource = XcrewWebViewXhrDataSource(xcrewSession, XcrewWorkScheduleParser())
        val database = com.example.crewmate.database.CrewMateDatabase.getInstance(appContext)
        com.example.crewmate.schedule.DiaCensusManager(dataSource, dataSource, database.diaCensusDao())
    }

    val sharedDiaDetailRepository = remember(xcrewSession, appContext, isOfflineMode) {
        val dataSource = XcrewWebViewXhrDataSource(xcrewSession, XcrewWorkScheduleParser())
        val database = com.example.crewmate.database.CrewMateDatabase.getInstance(appContext)
        com.example.crewmate.schedule.DiaDetailRepository(dataSource, database.diaDetailDao()) { isOfflineMode }
    }
    
    var authRootRoute by remember(isOfflineMode) { 
        mutableStateOf(
            MainScreenOption.entries.find { 
                it.name == prefs.getString("main_screen", MainScreenOption.SCHEDULE.name) 
            }?.route ?: Routes.Schedule
        ) 
    }

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    DisposableEffect(xcrewSession) {
        onDispose { xcrewSession.destroy() }
    }
    LaunchedEffect(navController, xcrewSession, isOfflineMode) {
        xcrewSession.authenticationExpired.collect { expired ->
            if (expired && !isOfflineMode && navController.currentDestination?.route != Routes.Login) {
                navController.navigate(Routes.Login) {
                    popUpTo(0) { inclusive = true }
                    launchSingleTop = true
                }
            }
        }
    }
    
    LaunchedEffect(isOfflineMode) {
        if (isOfflineMode && navController.currentDestination?.route == Routes.Login) {
            navController.navigate(authRootRoute) {
                popUpTo(0) { inclusive = true }
                launchSingleTop = true
            }
        }
    }

    LaunchedEffect(initialTargetRoute) {
        if (!initialTargetRoute.isNullOrBlank()) {
            navController.navigate(initialTargetRoute) {
                launchSingleTop = true
            }
        }
    }

    val navigateFromMenu: (String) -> Unit = { targetRoute ->
        if (currentRoute != targetRoute) {
            if (targetRoute == authRootRoute) {
                navController.popBackStack(authRootRoute, inclusive = false)
            } else {
                navController.navigate(targetRoute) {
                    popUpTo(authRootRoute) { inclusive = false }
                    launchSingleTop = true
                }
            }
        }
    }

    val handleLogout: (Boolean) -> Unit = { isReconnect ->
        isOfflineMode = false
        prefs.edit().putBoolean("is_offline_mode", false).apply()
        
        if (isReconnect) {
            skipAutoLoginOnLoginScreen = false
        } else {
            skipAutoLoginOnLoginScreen = true
        }
        CookieManager.getInstance().removeAllCookies(null)
        CookieManager.getInstance().flush()
        WebStorage.getInstance().deleteAllData()
        xcrewSession.setMockReviewMode(false)
        xcrewSession.webView?.clearCache(true)
        xcrewSession.webView?.clearHistory()
        navController.navigate(Routes.Login) {
            popUpTo(0) { inclusive = true }
            launchSingleTop = true
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Column(modifier = Modifier.fillMaxSize()) {
            if (currentRoute != null && currentRoute != Routes.Login) {
                val userInfo by xcrewSession.userInfo.collectAsState()
                CrewMateTopBar(
                    currentRoute = currentRoute,
                    userInfo = userInfo,
                    isOfflineMode = isOfflineMode,
                    onSelectMenu = navigateFromMenu,
                    onLogoClick = {
                        val mainScreenName = prefs.getString("main_screen", MainScreenOption.SCHEDULE.name)
                        val targetRoute = MainScreenOption.entries.find { it.name == mainScreenName }?.route ?: Routes.Schedule
                        navigateFromMenu(targetRoute)
                    },
                    onLogout = { handleLogout(false) },
                    onReconnect = { handleLogout(true) },
                    onToggleOfflineMode = {
                        if (!isOfflineMode) {
                            coroutineScope.launch {
                                isPreFetchingOfflineData = true
                                runCatching {
                                    val today = java.time.LocalDate.now()
                                    val currentMonthStr = java.time.YearMonth.from(today).format(java.time.format.DateTimeFormatter.ofPattern("yyyyMM", java.util.Locale.ROOT))
                                    val todayStr = today.format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd", java.util.Locale.ROOT))
                                    
                                    val dataSource = XcrewWebViewXhrDataSource(xcrewSession, XcrewWorkScheduleParser())
                                    val database = com.example.crewmate.database.CrewMateDatabase.getInstance(appContext)
                                    
                                    val workRepo = WorkScheduleRepositoryImpl(dataSource, WorkScheduleParser(), database.workScheduleDao()) { false }
                                    workRepo.getSchedules(currentMonthStr)

                                    val diaDetailRepo = com.example.crewmate.schedule.DiaDetailRepository(dataSource, database.diaDetailDao()) { false }
                                    val crewStatusRepo = CrewWorkStatusRepository(dataSource, database.crewAssignmentDao()) { false }
                                    
                                    // 1. 실제 오늘 날짜(todayStr)의 소속 승무원 근무 현황 캐싱
                                    crewStatusRepo.getCrewWorkStatus(todayStr)

                                    // 2. 오늘의 근무 / 다가오는 가장 가까운 근무 다이아 조회 및 캐싱
                                    val todayRepo = TodayWorkRepository(workRepo, diaDetailRepo, dataSource, crewStatusRepo, xcrewSession, appContext) { false }
                                    val todayResult = todayRepo.getTodayOrNextWork(today)

                                    val targetDate = todayResult?.workDate ?: today
                                    val targetMonthStr = java.time.YearMonth.from(targetDate).format(java.time.format.DateTimeFormatter.ofPattern("yyyyMM", java.util.Locale.ROOT))
                                    if (targetMonthStr != currentMonthStr) {
                                        workRepo.getSchedules(targetMonthStr)
                                    }

                                    // 3. 만약 다가오는 근무일자가 오늘과 다른 날짜라면 해당 날짜의 소속 승무원 현황도 추가 캐싱
                                    if (targetDate != today) {
                                        val targetDateStr = targetDate.format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd", java.util.Locale.ROOT))
                                        crewStatusRepo.getCrewWorkStatus(targetDateStr)
                                    }
                                }
                                isOfflineMode = true
                                prefs.edit().putBoolean("is_offline_mode", true).apply()
                                isPreFetchingOfflineData = false
                                android.widget.Toast.makeText(appContext, "오프라인 모드로 전환되었습니다. (데이터 저장 완료)", android.widget.Toast.LENGTH_SHORT).show()
                            }
                        } else {
                            isOfflineMode = false
                            prefs.edit().putBoolean("is_offline_mode", false).apply()
                            android.widget.Toast.makeText(appContext, "온라인 모드로 전환되었습니다.", android.widget.Toast.LENGTH_SHORT).show()
                        }
                    }
                )
            }
            NavHost(
                navController = navController,
                startDestination = if (isOfflineMode) authRootRoute else Routes.Login,
                modifier = Modifier.weight(1f),
            ) {
                composable(Routes.Login) {
                    val loginViewModel: LoginViewModel = viewModel(
                        factory = LoginViewModelFactory(
                            xcrewSession = xcrewSession,
                            credentialStore = credentialStore,
                            autoLoginOnStart = !skipAutoLoginOnLoginScreen,
                        ),
                    )
                    val state by loginViewModel.uiState.collectAsState()
                    LaunchedEffect(state.isLoggedIn) {
                        if (state.isLoggedIn) {
                            skipAutoLoginOnLoginScreen = false
                            coroutineScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                                runCatching {
                                    com.example.crewmate.alarm.AutoOfflineManager.syncFromDatabase(appContext)
                                }
                            }
                            val mainScreenName = prefs.getString("main_screen", MainScreenOption.SCHEDULE.name)
                            val targetRoute = MainScreenOption.entries.find { it.name == mainScreenName }?.route ?: Routes.Schedule

                            authRootRoute = targetRoute

                            navController.navigate(targetRoute) {
                                popUpTo(Routes.Login) { inclusive = true }
                                launchSingleTop = true
                            }
                        }
                    }
                    LoginScreen(
                        isLoading = state.isLoading,
                        isAutoLogin = state.isAutoLogin,
                        isCheckingSavedCredentials = state.isCheckingSavedCredentials,
                        employeeNumber = state.employeeNumber,
                        password = state.password,
                        rememberLogin = state.rememberLogin,
                        errorMessage = state.errorMessage,
                        canRetryAutoLogin = state.canRetryAutoLogin,
                        onEmployeeNumberChange = loginViewModel::setEmployeeNumber,
                        onPasswordChange = loginViewModel::setPassword,
                        onRememberLoginChange = loginViewModel::setRememberLogin,
                        onLogin = { empNo, pwd ->
                            isOfflineMode = false
                            prefs.edit().putBoolean("is_offline_mode", false).apply()
                            loginViewModel.login(empNo, pwd)
                        },
                        onRetryAutoLogin = {
                            isOfflineMode = false
                            prefs.edit().putBoolean("is_offline_mode", false).apply()
                            loginViewModel.retryAutoLogin()
                        },
                        onSwitchToManualLogin = loginViewModel::switchToManualLogin,
                        onDeleteSavedLogin = loginViewModel::deleteSavedLogin,
                    )
                }
                composable(Routes.Schedule) {
                    val scheduleViewModel: ScheduleViewModel = viewModel(
                        factory = ScheduleViewModelFactory(
                            xcrewSession,
                            appContext,
                            isOfflineModeProvider = { isOfflineMode }
                        ),
                    )
                    ScheduleScreen(
                        viewModel = scheduleViewModel,
                        diaDetailDataSource = sharedDiaDetailRepository,
                    )
                }
                // composable(Routes.PersonalSchedule) {
                //     val diaDetailDataSource = remember(xcrewSession) {
                //         XcrewWebViewXhrDataSource(xcrewSession, XcrewWorkScheduleParser())
                //     }
                //     val memoRepository = remember(appContext) {
                //         com.example.crewmate.database.MemoRepository(com.example.crewmate.database.CrewMateDatabase.getInstance(appContext).memoDao())
                //     }
                //     val personalViewModel: com.example.crewmate.schedule.PersonalScheduleViewModel = androidx.lifecycle.viewmodel.compose.viewModel(
                //         factory = com.example.crewmate.schedule.PersonalScheduleViewModelFactory(
                //             diaDetailDataSource,
                //             xcrewSession,
                //             memoRepository
                //         )
                //     )
                //     com.example.crewmate.schedule.PersonalScheduleScreen(
                //         viewModel = personalViewModel,
                //         diaDetailDataSource = diaDetailDataSource
                //     )
                // }
                composable(Routes.TodayWork) {
                    val database = remember(appContext) { com.example.crewmate.database.CrewMateDatabase.getInstance(appContext) }
                    val todayWorkRepository = remember(xcrewSession, database, isOfflineMode) {
                        val scheduleDataSource = XcrewWebViewXhrDataSource(
                            xcrewSession,
                            XcrewWorkScheduleParser(),
                        )
                        val scheduleRepository = WorkScheduleRepositoryImpl(
                            scheduleDataSource,
                            WorkScheduleParser(),
                            database.workScheduleDao(),
                            isOfflineModeProvider = { isOfflineMode }
                        )
                        val diaDetailRepository = com.example.crewmate.schedule.DiaDetailRepository(
                            scheduleDataSource,
                            database.diaDetailDao(),
                            isOfflineModeProvider = { isOfflineMode }
                        )
                        val crewWorkStatusRepository = com.example.crewmate.schedule.CrewWorkStatusRepository(
                            scheduleDataSource,
                            database.crewAssignmentDao(),
                            isOfflineModeProvider = { isOfflineMode }
                        )
                        TodayWorkRepository(
                            scheduleRepository, 
                            diaDetailRepository, 
                            scheduleDataSource, 
                            crewWorkStatusRepository, 
                            xcrewSession,
                            appContext,
                            isOfflineModeProvider = { isOfflineMode }
                        )
                    }
                    val todayWorkViewModel: TodayWorkViewModel = viewModel(
                        factory = TodayWorkViewModelFactory(todayWorkRepository),
                    )
                    TodayWorkScreen(
                        viewModel = todayWorkViewModel,
                        onNavigateToAlarmSettings = { navController.navigate(Routes.WorkAlarmSettings) },
                        onBack = { navController.popBackStack() },
                    )
                }
                composable(Routes.TrainCrewLookup) {
                    val dataSource = remember(xcrewSession) {
                        XcrewWebViewXhrDataSource(xcrewSession, XcrewWorkScheduleParser())
                    }
                    val database = remember(appContext) { com.example.crewmate.database.CrewMateDatabase.getInstance(appContext) }

                    com.example.crewmate.schedule.TrainCrewLookupV2Screen(
                        censusManager = globalCensusManager,
                        diaCensusDao = database.diaCensusDao(),
                        crewWorkStatusDataSource = dataSource,
                        diaDetailDataSource = sharedDiaDetailRepository,
                        onBack = { navController.popBackStack() }
                    )
                }
                composable(Routes.DiaLookup) {
                    com.example.crewmate.schedule.DiaLookupScreen(
                        diaDetailDataSource = sharedDiaDetailRepository,
                        censusManager = globalCensusManager
                    )
                }
                composable(Routes.CrewWorkStatus) {
                    val crewWorkStatusDataSource = remember(xcrewSession) {
                        XcrewWebViewXhrDataSource(xcrewSession, XcrewWorkScheduleParser())
                    }
                    val database = remember(appContext) { com.example.crewmate.database.CrewMateDatabase.getInstance(appContext) }
                    val crewWorkStatusRepository = remember(crewWorkStatusDataSource, database, isOfflineMode) {
                        com.example.crewmate.schedule.CrewWorkStatusRepository(
                            crewWorkStatusDataSource, 
                            database.crewAssignmentDao(),
                            isOfflineModeProvider = { isOfflineMode }
                        )
                    }
                    com.example.crewmate.schedule.CrewWorkStatusScreen(
                        crewWorkStatusRepository = crewWorkStatusRepository,
                        diaDetailDataSource = sharedDiaDetailRepository
                    )
                }
                composable(Routes.NonOpStatus) {
                    val dataSource = remember(xcrewSession) {
                        XcrewWebViewXhrDataSource(xcrewSession, XcrewWorkScheduleParser())
                    }
                    val leaveRepository = remember(appContext) {
                        com.example.crewmate.database.LeaveRepository(com.example.crewmate.database.CrewMateDatabase.getInstance(appContext).leaveDao())
                    }
                    val viewModel: com.example.crewmate.schedule.NonOpStatusViewModel = androidx.lifecycle.viewmodel.compose.viewModel(
                        factory = com.example.crewmate.schedule.NonOpStatusViewModelFactory(dataSource, leaveRepository)
                    )
                    com.example.crewmate.schedule.NonOpStatusScreen(viewModel)
                }
                composable(Routes.Settings) {
                    SettingsScreen()
                }
                composable(Routes.WorkAlarmSettings) {
                    com.example.crewmate.alarm.WorkAlarmSettingsScreen(
                        onBack = { navController.popBackStack() },
                        xcrewSession = xcrewSession
                    )
                }
            }
        }

        if (isPreFetchingOfflineData) {
            AlertDialog(
                onDismissRequest = {},
                containerColor = MaterialTheme.colorScheme.surface,
                title = { Text("오프라인 데이터 저장 중", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary) },
                text = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary, strokeWidth = 3.dp, modifier = Modifier.size(24.dp))
                        Spacer(Modifier.width(16.dp))
                        Text("최소 필수 근무 데이터를 기기에 저장하고 있습니다...", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
                    }
                },
                confirmButton = {}
            )
        }

        HiddenXcrewWebView(session = xcrewSession)
    }
}

@Composable
private fun CrewMateTopBar(
    currentRoute: String,
    userInfo: Pair<String, String>?,
    isOfflineMode: Boolean,
    onSelectMenu: (String) -> Unit,
    onLogoClick: () -> Unit = {},
    onLogout: () -> Unit,
    onReconnect: () -> Unit,
    onToggleOfflineMode: () -> Unit,
) {
    var menuExpanded by remember { mutableStateOf(false) }

    Surface(
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 4.dp
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box {
                        IconButton(
                            onClick = { menuExpanded = !menuExpanded },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Text("☰", fontSize = 20.sp, color = MaterialTheme.colorScheme.onSurface)
                        }
                        DropdownMenu(
                            expanded = menuExpanded,
                            onDismissRequest = { menuExpanded = false },
                            modifier = Modifier.background(MaterialTheme.colorScheme.surface),
                        ) {
                            MenuDropdownRow(
                                title = "월별 승무근무표",
                                selected = currentRoute == Routes.Schedule,
                                onClick = {
                                    menuExpanded = false
                                    onSelectMenu(Routes.Schedule)
                                },
                            )
                            MenuDropdownRow(
                                title = "오늘의 근무 다이아 확인",
                                selected = currentRoute == Routes.TodayWork,
                                onClick = {
                                    menuExpanded = false
                                    onSelectMenu(Routes.TodayWork)
                                },
                            )
                            MenuDropdownRow(
                                title = "열차 승무원 조회",
                                selected = currentRoute == Routes.TrainCrewLookup,
                                enabled = !isOfflineMode,
                                onClick = {
                                    menuExpanded = false
                                    onSelectMenu(Routes.TrainCrewLookup)
                                },
                            )
                            MenuDropdownRow(
                                title = "승무 다이아 조회",
                                selected = currentRoute == Routes.DiaLookup,
                                enabled = !isOfflineMode,
                                onClick = {
                                    menuExpanded = false
                                    onSelectMenu(Routes.DiaLookup)
                                },
                            )
                            MenuDropdownRow(
                                title = "소속 승무원 근무 현황",
                                selected = currentRoute == Routes.CrewWorkStatus,
                                onClick = {
                                    menuExpanded = false
                                    onSelectMenu(Routes.CrewWorkStatus)
                                },
                            )
                            MenuDropdownRow(
                                title = "비실동 현황 조회",
                                selected = currentRoute == Routes.NonOpStatus,
                                enabled = !isOfflineMode,
                                onClick = {
                                    menuExpanded = false
                                    onSelectMenu(Routes.NonOpStatus)
                                },
                            )
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, thickness = 1.dp, modifier = Modifier.padding(vertical = 4.dp))
                            MenuDropdownRow(
                                title = "설정",
                                selected = currentRoute == Routes.Settings,
                                onClick = {
                                    menuExpanded = false
                                    onSelectMenu(Routes.Settings)
                                },
                            )
                            MenuDropdownRow(
                                title = "정밀 알람",
                                selected = currentRoute == Routes.WorkAlarmSettings,
                                onClick = {
                                    menuExpanded = false
                                    onSelectMenu(Routes.WorkAlarmSettings)
                                },
                            )
                            MenuDropdownRow(
                                title = if (isOfflineMode) "온라인 모드 전환" else "오프라인 모드 전환",
                                selected = false,
                                onClick = {
                                    menuExpanded = false
                                    onToggleOfflineMode()
                                },
                            )
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, thickness = 1.dp, modifier = Modifier.padding(vertical = 4.dp))
                            MenuDropdownRow(
                                title = "로그아웃",
                                selected = false,
                                onClick = {
                                    menuExpanded = false
                                    onLogout()
                                },
                            )
                        }
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clickable(onClick = onLogoClick)
                            .padding(vertical = 8.dp)
                    ) {
                        Spacer(Modifier.width(10.dp))
                        
                        CrewMateLogo(size = 28.dp)
                        
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "CrewMate",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        if (isOfflineMode) {
                            Spacer(Modifier.width(6.dp))
                            Surface(
                                color = MaterialTheme.colorScheme.errorContainer,
                                shape = RoundedCornerShape(4.dp)
                            ) {
                                Text(
                                    text = "OFFLINE",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                }
                
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (userInfo != null && !isOfflineMode) {
                        Column(
                            horizontalAlignment = Alignment.End,
                            modifier = Modifier.padding(end = 8.dp),
                            verticalArrangement = Arrangement.spacedBy((-2).dp)
                        ) {
                            Text(
                                text = userInfo.first,
                                fontSize = 12.sp,
                                lineHeight = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            if (userInfo.second.isNotEmpty()) {
                                Text(
                                    text = userInfo.second,
                                    fontSize = 10.sp,
                                    lineHeight = 10.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    OutlinedButton(
                        onClick = onReconnect,
                        shape = RoundedCornerShape(20.dp),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 0.dp),
                        modifier = Modifier.height(34.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                    ) {
                        Text(
                            text = "재접속",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, thickness = 1.dp)
        }
    }
}

@Composable
private fun MenuDropdownRow(
    title: String,
    selected: Boolean,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    DropdownMenuItem(
        modifier = if (selected) Modifier.background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f), MaterialTheme.shapes.small) else Modifier,
        text = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (selected) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(3.dp)),
                    )
                    Spacer(Modifier.width(8.dp))
                }
                Text(
                    text = title,
                    fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.Medium,
                    color = if (enabled) (if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    fontSize = 14.sp,
                )
            }
        },
        onClick = { if (enabled) onClick() },
    )
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun HiddenXcrewWebView(session: XcrewWebViewSession) {
    AndroidView(
        modifier = Modifier
            .size(1.dp)
            .alpha(0f)
            .clearAndSetSemantics {},
        factory = { context ->
            CookieManager.getInstance().setAcceptCookie(true)
            WebView.setWebContentsDebuggingEnabled(BuildConfig.DEBUG)
            WebView(context).apply {
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                settings.allowFileAccess = false
                settings.allowContentAccess = false
                @Suppress("DEPRECATION")
                settings.allowFileAccessFromFileURLs = false
                @Suppress("DEPRECATION")
                settings.allowUniversalAccessFromFileURLs = false
                @Suppress("DEPRECATION")
                settings.savePassword = false
                settings.saveFormData = false
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    settings.safeBrowsingEnabled = true
                }
                CookieManager.getInstance().setAcceptThirdPartyCookies(this, false)
                webChromeClient = WebChromeClient()
                webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView?, url: String?) {
                        super.onPageFinished(view, url)
                        if (url != null && url.startsWith("https://xcrew.korail.com") && (view?.progress ?: 100) >= 100) {
                            session.markPageReady(url)
                        }
                    }
                }
                session.attach(this)
                loadUrl("https://xcrew.korail.com/loginView.do")
            }
        },
        onRelease = {
            it.destroy()
        },
    )
}
