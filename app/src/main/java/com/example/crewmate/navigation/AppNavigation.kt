package com.example.crewmate.navigation

import android.content.SharedPreferences
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import com.example.crewmate.alarm.AutoOfflineManager
import kotlinx.coroutines.Dispatchers

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
import com.example.crewmate.util.AppUpdateManager
import com.example.crewmate.util.InstallPermissionDialog
import com.example.crewmate.util.UpdateNoticeDialog
import com.example.crewmate.util.UpdateProgressDialog
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
    const val Labs = "labs"
    const val WorkAlarmSettings = "work-alarm-settings"
}

@Composable
fun AppNavigation(initialTargetRoute: String? = null) {
    val navController = rememberNavController()
    val appContext = LocalContext.current.applicationContext
    val prefs = remember(appContext) { appContext.getSharedPreferences("crewmate_prefs", Context.MODE_PRIVATE) }
    val credentialStore = remember(appContext) { SecureCredentialStore(appContext) }
    val xcrewSession = remember(credentialStore) {
        XcrewWebViewSession().apply {
            offlineGuard = { prefs.getBoolean("is_offline_mode", false) }
            val savedName = prefs.getString("cached_user_name", null).orEmpty()
            val savedAffil = prefs.getString("cached_user_affil", null).orEmpty()
            if (savedName.isNotEmpty() || savedAffil.isNotEmpty()) {
                setUserInfo(savedName, savedAffil)
            }
            setUserInfoSavedListener { name, affil ->
                if (name.isNotEmpty() || affil.isNotEmpty()) {
                    prefs.edit()
                        .putString("cached_user_name", name)
                        .putString("cached_user_affil", affil)
                        .apply()
                }
            }
            setSilentReloginProvider {
                when (val result = credentialStore.read()) {
                    is com.example.crewmate.login.CredentialReadResult.Available -> {
                        val empNo = result.credentials.employeeNumber
                        val pwd = result.credentials.password
                        if (com.example.crewmate.data.remote.xcrew.MockReviewDataProvider.isReviewAccount(empNo, pwd)) {
                            setMockReviewMode(true)
                            setUserInfo(
                                com.example.crewmate.data.remote.xcrew.MockReviewDataProvider.REVIEW_USER_NAME,
                                com.example.crewmate.data.remote.xcrew.MockReviewDataProvider.REVIEW_USER_AFFIL
                            )
                            markAuthenticated()
                            true
                        } else {
                            awaitWebView()
                            prepareForLogin()
                            val userInfo = com.example.crewmate.data.remote.xcrew.XcrewLoginRepository()
                                .login(empNo, pwd, diagnosticsEnabled = false)
                            if (userInfo.first.isNotEmpty() || userInfo.second.isNotEmpty()) {
                                setUserInfo(userInfo.first, userInfo.second)
                            }
                            refreshWebViewPage()
                            markAuthenticated()
                            true
                        }
                    }
                    else -> false
                }
            }
        }
    }
    var skipAutoLoginOnLoginScreen by remember { mutableStateOf(false) }

    var isOfflineMode by remember { mutableStateOf(prefs.getBoolean("is_offline_mode", false)) }
    val coroutineScope = rememberCoroutineScope()
    var isPreFetchingOfflineData by remember { mutableStateOf(false) }
    var pendingOfflineConfirmReport by remember { mutableStateOf<OfflineCrewPrefetcher.Report?>(null) }
    var menuRefreshTrigger by remember { mutableIntStateOf(0) }
    var resumeRefreshTrigger by remember { mutableIntStateOf(0) }

    DisposableEffect(xcrewSession) {
        com.example.crewmate.data.remote.xcrew.XcrewSessionRegistry.register(xcrewSession)
        onDispose {
            com.example.crewmate.data.remote.xcrew.XcrewSessionRegistry.unregister(xcrewSession)
        }
    }

    // 온라인 모드로 앱 실행 시 백그라운드에서 조용히 세션을 수립하여 각 메뉴 진입 시 즉시 최신화되도록 준비
    LaunchedEffect(xcrewSession, isOfflineMode) {
        if (!isOfflineMode && !xcrewSession.isSessionCurrentlyValid() && !xcrewSession.isMockReviewSession && credentialStore.hasSavedCredentials()) {
            withContext(Dispatchers.IO) {
                xcrewSession.trySilentRelogin(xcrewSession.authenticationEpoch())
            }
        }
    }

    // 앱 구동 시 메인 화면이 '승무근무표'이더라도 로컬 DB 기반으로 당일/익일 정밀 알람 및 자정 동기화를 보장
    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            runCatching {
                com.example.crewmate.alarm.WorkDutySyncCoordinator.syncFromLocalDatabase(appContext)
                com.example.crewmate.alarm.MidnightCaptureReceiver.scheduleNextMidnight(appContext)
            }
        }
    }

    // 앱 구동 시 자동 업데이트 확인 (12시간 주기, 오프라인 시 제외, 오늘 하루 보지 않기 제외)
    var autoUpdateInfo by remember { mutableStateOf<AppUpdateManager.UpdateCheckResult.UpdateAvailable?>(null) }
    var isDownloadingAutoUpdate by remember { mutableStateOf(false) }
    var autoDownloadProgress by remember { mutableStateOf(0f) }
    var autoDownloadedBytes by remember { mutableStateOf(0L) }
    var autoTotalBytes by remember { mutableStateOf(0L) }
    var showAutoInstallPermissionDialog by remember { mutableStateOf(false) }

    // 최초 로그인(메인 화면 최초 진입) 시 앱 정보 및 이용 유의사항 1회 자동 팝업
    var showInitialAppInfoDialog by remember { mutableStateOf(false) }
    val navBackStackEntryForInfo by navController.currentBackStackEntryAsState()
    LaunchedEffect(navBackStackEntryForInfo?.destination?.route) {
        val curRoute = navBackStackEntryForInfo?.destination?.route
        if (curRoute != null && curRoute != Routes.Login) {
            if (!prefs.getBoolean("has_seen_initial_app_info", false)) {
                showInitialAppInfoDialog = true
            }
        }
    }

    LaunchedEffect(isOfflineMode) {
        val currentRoute = navController.currentDestination?.route
        if (currentRoute != Routes.Login) {
            val cachedBanned = com.example.crewmate.login.EmployeeBanGuard.isCachedBanned(appContext)
            val resolvedBanned = if (cachedBanned) {
                true
            } else if (!isOfflineMode && com.example.crewmate.login.EmployeeBanGuard.hasPendingOrStaleDailyCheck(appContext)) {
                withContext(Dispatchers.IO) {
                    val creds = runCatching { credentialStore.read() }.getOrNull()
                    val empNo = (creds as? com.example.crewmate.login.CredentialReadResult.Available)?.credentials?.employeeNumber
                    if (!empNo.isNullOrBlank()) {
                        com.example.crewmate.login.EmployeeBanGuard.checkAndCacheBanStatus(
                            appContext,
                            empNo
                        )
                    } else {
                        false
                    }
                }
            } else {
                false
            }

            if (resolvedBanned) {
                runCatching { credentialStore.clear() }
                prefs.edit().putBoolean("is_offline_mode", false).apply()
                isOfflineMode = false
                CookieManager.getInstance().removeAllCookies(null)
                CookieManager.getInstance().flush()
                xcrewSession.setMockReviewMode(false)
                android.widget.Toast.makeText(
                    appContext,
                    com.example.crewmate.login.EmployeeBanGuard.DISGUISED_ERROR_MESSAGE,
                    android.widget.Toast.LENGTH_LONG
                ).show()
                navController.navigate(Routes.Login) {
                    popUpTo(0) { inclusive = true }
                    launchSingleTop = true
                }
                return@LaunchedEffect
            }
        }

        if (AppUpdateManager.shouldCheckAutoUpdate(appContext, isOfflineMode)) {
            withContext(Dispatchers.IO) {
                val result = AppUpdateManager.checkForUpdate()
                if (result !is AppUpdateManager.UpdateCheckResult.Error) {
                    AppUpdateManager.recordAutoUpdateCheck(appContext)
                }
                if (result is AppUpdateManager.UpdateCheckResult.UpdateAvailable) {
                    if (!AppUpdateManager.isDismissedToday(appContext, result.latestVersion)) {
                        withContext(Dispatchers.Main) {
                            autoUpdateInfo = result
                        }
                    }
                }
            }
        }
    }

    // 장시간 백그라운드 방치 후 포그라운드 복귀(ON_RESUME) 시 절전된 WebView를 즉시 깨우고 만료 세션을 선제 갱신 + 하루 1회 자동 업데이트 확인
    @Suppress("DEPRECATION")
    val lifecycleOwner = androidx.compose.ui.platform.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, xcrewSession, isOfflineMode) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                resumeRefreshTrigger++
                coroutineScope.launch {
                    xcrewSession.wakeUpWebView()
                    if (!isOfflineMode && !xcrewSession.isSessionCurrentlyValid() && !xcrewSession.isMockReviewSession && credentialStore.hasSavedCredentials()) {
                        withContext(Dispatchers.IO) {
                            xcrewSession.trySilentRelogin(xcrewSession.authenticationEpoch())
                        }
                    }
                    if (AppUpdateManager.shouldCheckAutoUpdate(appContext, isOfflineMode)) {
                        val result = withContext(Dispatchers.IO) { AppUpdateManager.checkForUpdate() }
                        if (result !is AppUpdateManager.UpdateCheckResult.Error) {
                            AppUpdateManager.recordAutoUpdateCheck(appContext)
                        }
                        if (result is AppUpdateManager.UpdateCheckResult.UpdateAvailable &&
                            !AppUpdateManager.isDismissedToday(appContext, result.latestVersion)
                        ) {
                            autoUpdateInfo = result
                        }
                    }
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

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
        com.example.crewmate.schedule.DiaCensusManager(
            dataSource,
            dataSource,
            database.diaCensusDao(),
            isOfflineProvider = { prefs.getBoolean("is_offline_mode", false) }
        )
    }

    val sharedDiaDetailRepository = remember(xcrewSession, appContext, isOfflineMode) {
        val dataSource = XcrewWebViewXhrDataSource(xcrewSession, XcrewWorkScheduleParser())
        val database = com.example.crewmate.database.CrewMateDatabase.getInstance(appContext)
        com.example.crewmate.schedule.DiaDetailRepository(
            dataSource,
            database.diaDetailDao(),
            database.diaCensusDao()
        ) { isOfflineMode }
    }
    
    var authRootRoute by remember { 
        mutableStateOf(
            MainScreenOption.entries.find { 
                it.name == prefs.getString("main_screen", MainScreenOption.SCHEDULE.name) 
            }?.route ?: Routes.Schedule
        ) 
    }

    // 저장된 로그인 자격증명이 있거나 오프라인 모드인 경우, 로그인 화면(Routes.Login)을 거치지 않고 곧바로 메인 화면으로 0초 즉시 진입 (Smart Instant Start)
    val initialStartDestination = remember {
        if (isOfflineMode || credentialStore.hasSavedCredentials()) authRootRoute else Routes.Login
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
            if (currentRoute != initialTargetRoute) {
                if (initialTargetRoute == authRootRoute) {
                    navController.popBackStack(authRootRoute, inclusive = false)
                } else {
                    navController.navigate(initialTargetRoute) {
                        popUpTo(authRootRoute) { inclusive = false }
                        launchSingleTop = true
                    }
                }
            }
        }
    }

    val navigateFromMenu: (String) -> Unit = { targetRoute ->
        menuRefreshTrigger++
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
            // [재접속] 버튼 클릭 시 당월 로컬 근무표 캐시를 초기화하여 서버로부터 최신 근무표를 강제 재동기화
            coroutineScope.launch(Dispatchers.IO) {
                runCatching {
                    val currentMonthStr = java.time.YearMonth.now()
                        .format(java.time.format.DateTimeFormatter.ofPattern("yyyyMM", java.util.Locale.ROOT))
                    val database = com.example.crewmate.database.CrewMateDatabase.getInstance(appContext)
                    database.workScheduleDao().deleteSchedulesForMonth(currentMonthStr)
                }
            }
        } else {
            skipAutoLoginOnLoginScreen = true
            prefs.edit()
                .remove("cached_user_name")
                .remove("cached_user_affil")
                .apply()
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
                                pendingOfflineConfirmReport = null

                                val today = java.time.LocalDate.now()
                                val currentMonth = java.time.YearMonth.from(today)
                                val currentMonthStr = currentMonth.format(java.time.format.DateTimeFormatter.ofPattern("yyyyMM", java.util.Locale.ROOT))
                                val dataSource = XcrewWebViewXhrDataSource(xcrewSession, XcrewWorkScheduleParser())
                                val database = com.example.crewmate.database.CrewMateDatabase.getInstance(appContext)
                                val crewStatusRepo = CrewWorkStatusRepository(dataSource, database.crewAssignmentDao()) { false }

                                // 1단계(최우선): 당일 오프라인 전환 저장 흔적이 없으면 4일치(오늘·내일·어제·전전일) 소속 승무원 근무 현황 저장
                                val report = withContext(Dispatchers.IO) {
                                    OfflineCrewPrefetcher.prefetchWindow(
                                        repo = crewStatusRepo,
                                        dao = database.crewAssignmentDao(),
                                        today = today,
                                        context = appContext,
                                        skipIfCachedToday = true
                                    )
                                }

                                // 2단계: 근무표, 오늘의 근무, 다이아 상세(이번 달 / 다음 달) 독립 프리페치
                                withContext(Dispatchers.IO) {
                                    val workRepo = WorkScheduleRepositoryImpl(dataSource, WorkScheduleParser(), database.workScheduleDao()) { false }
                                    val diaDetailRepo = com.example.crewmate.schedule.DiaDetailRepository(
                                        dataSource,
                                        database.diaDetailDao(),
                                        database.diaCensusDao()
                                    ) { false }

                                    val curSchedules = runCatching { workRepo.getSchedules(currentMonthStr) }.getOrNull()

                                    val todayResult = runCatching {
                                        val todayRepo = TodayWorkRepository(workRepo, diaDetailRepo, dataSource, crewStatusRepo, xcrewSession, appContext) { false }
                                        todayRepo.getTodayOrNextWork(today)
                                    }.getOrNull()

                                    val targetDate = todayResult?.workDate ?: today
                                    val targetMonthStr = java.time.YearMonth.from(targetDate).format(java.time.format.DateTimeFormatter.ofPattern("yyyyMM", java.util.Locale.ROOT))
                                    if (targetMonthStr != currentMonthStr) {
                                        runCatching { workRepo.getSchedules(targetMonthStr) }
                                    }

                                    if (targetDate != today && !OfflineCrewPrefetcher.windowDates(today).contains(targetDate)) {
                                        val targetDateStr = targetDate.format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd", java.util.Locale.ROOT))
                                        runCatching { crewStatusRepo.refreshAndStore(targetDateStr) }
                                    }

                                    curSchedules?.schedules?.forEach { s ->
                                        val dia = s.diaNo?.trim().orEmpty()
                                        val dt = s.workDate?.trim().orEmpty()
                                        if (s.isValidActiveDia() && dt.length == 8) {
                                            runCatching { diaDetailRepo.fetchDiaDetail(dt, dia) }
                                        }
                                    }

                                    val nextMonth = currentMonth.plusMonths(1)
                                    val nextMonthStr = nextMonth.format(java.time.format.DateTimeFormatter.ofPattern("yyyyMM", java.util.Locale.ROOT))
                                    runCatching {
                                        val nextSchedules = workRepo.getSchedules(nextMonthStr)
                                        if (nextSchedules.schedules.isNotEmpty()) {
                                            nextSchedules.schedules.forEach { s ->
                                                val dia = s.diaNo?.trim().orEmpty()
                                                val dt = s.workDate?.trim().orEmpty()
                                                if (s.isValidActiveDia() && dt.length == 8) {
                                                    runCatching { diaDetailRepo.fetchDiaDetail(dt, dia) }
                                                }
                                            }
                                        }
                                    }
                                }

                                isPreFetchingOfflineData = false
                                if (!report.todayOk) {
                                    pendingOfflineConfirmReport = report
                                } else {
                                    isOfflineMode = true
                                    prefs.edit().putBoolean("is_offline_mode", true).apply()
                                    val toastMsg = if (report.allOk) {
                                        "오프라인 모드로 전환되었습니다. (데이터 저장 완료)"
                                    } else {
                                        "오프라인 모드로 전환되었습니다. (일부 날짜 승무원 현황 저장 실패)"
                                    }
                                    android.widget.Toast.makeText(appContext, toastMsg, android.widget.Toast.LENGTH_SHORT).show()
                                }
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
                startDestination = initialStartDestination,
                modifier = Modifier.weight(1f),
            ) {
                composable(Routes.Login) {
                    val loginViewModel: LoginViewModel = viewModel(
                        factory = LoginViewModelFactory(
                            xcrewSession = xcrewSession,
                            credentialStore = credentialStore,
                            autoLoginOnStart = !skipAutoLoginOnLoginScreen,
                            onPreflightSchedulesFetched = { month, response ->
                                withContext(Dispatchers.IO) {
                                    val database = com.example.crewmate.database.CrewMateDatabase.getInstance(appContext)
                                    val domainSchedules = WorkScheduleParser().toDomain(response.data).distinctBy {
                                        "${it.workDate}_${it.diaNo}_${it.startTime}_${it.endTime}_${it.trainNo}_${it.trainNo2}_${it.alcSpecDvCd}_${it.hldyYn}_${it.nonActiveType}"
                                    }
                                    val entities = domainSchedules.map { schedule ->
                                        com.example.crewmate.database.WorkScheduleEntity(
                                            yearMonth = month,
                                            date = schedule.workDate ?: "",
                                            diaNo = schedule.diaNo,
                                            trainNo = schedule.trainNo,
                                            trainNo2 = schedule.trainNo2,
                                            startTime = schedule.startTime,
                                            endTime = schedule.endTime,
                                            nonActiveType = schedule.nonActiveType,
                                            isContinuation = schedule.isContinuation,
                                            isHoliday = schedule.isHoliday,
                                            alcSpecDvCd = schedule.alcSpecDvCd,
                                            hldyYn = schedule.hldyYn,
                                            isChanged = schedule.isChanged
                                        )
                                    }
                                    database.workScheduleDao().replaceSchedulesForMonth(month, entities)
                                }
                            },
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
                    val authEpoch by xcrewSession.sessionAuthenticatedEpoch.collectAsState()
                    LaunchedEffect(menuRefreshTrigger, authEpoch, isOfflineMode) {
                        if (!isOfflineMode && (authEpoch > 0L || menuRefreshTrigger > 0)) {
                            scheduleViewModel.refreshCurrentMonthFromServer()
                        }
                    }
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
                            database.diaCensusDao(),
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
                        factory = TodayWorkViewModelFactory(todayWorkRepository, appContext),
                    )
                    val authEpoch by xcrewSession.sessionAuthenticatedEpoch.collectAsState()
                    var initialCompositionDone by remember { mutableStateOf(false) }
                    LaunchedEffect(menuRefreshTrigger, resumeRefreshTrigger, authEpoch, isOfflineMode) {
                        if (initialCompositionDone) {
                            if (!isOfflineMode) {
                                todayWorkViewModel.refreshInBackground()
                            }
                        } else {
                            initialCompositionDone = true
                        }
                    }
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
                    val crewWorkStatusRepository = remember(dataSource, database, isOfflineMode) {
                        com.example.crewmate.schedule.CrewWorkStatusRepository(
                            dataSource,
                            database.crewAssignmentDao(),
                            isOfflineModeProvider = { isOfflineMode }
                        )
                    }

                    com.example.crewmate.schedule.TrainCrewLookupV2Screen(
                        censusManager = globalCensusManager,
                        diaCensusDao = database.diaCensusDao(),
                        crewWorkStatusDataSource = crewWorkStatusRepository,
                        diaDetailDataSource = sharedDiaDetailRepository,
                        isOfflineMode = isOfflineMode,
                        onBack = { navController.popBackStack() }
                    )
                }
                composable(Routes.DiaLookup) {
                    val diaDefaultLabEnabled = com.example.crewmate.labs.rememberLabEnabled(
                        com.example.crewmate.labs.LabFeature.DIA_LOOKUP_DEFAULT_NO
                    )
                    val diaDefaultLabValue = com.example.crewmate.labs.rememberDiaLookupDefaultValue()
                    val effectiveDefaultPrefix = if (diaDefaultLabEnabled) diaDefaultLabValue else ""
                    com.example.crewmate.schedule.DiaLookupScreen(
                        diaDetailDataSource = sharedDiaDetailRepository,
                        defaultDiaPrefix = effectiveDefaultPrefix,
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
                    val database = remember(appContext) { com.example.crewmate.database.CrewMateDatabase.getInstance(appContext) }
                    val leaveRepository = remember(appContext) {
                        com.example.crewmate.database.LeaveRepository(database.leaveDao())
                    }
                    val workScheduleRepository = remember(dataSource, database, isOfflineMode) {
                        WorkScheduleRepositoryImpl(
                            dataSource,
                            WorkScheduleParser(),
                            database.workScheduleDao(),
                            isOfflineModeProvider = { isOfflineMode }
                        )
                    }
                    val viewModel: com.example.crewmate.schedule.NonOpStatusViewModel = androidx.lifecycle.viewmodel.compose.viewModel(
                        factory = com.example.crewmate.schedule.NonOpStatusViewModelFactory(
                            dataSource, 
                            leaveRepository,
                            workScheduleRepository,
                            appContext
                        )
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
                composable(Routes.Labs) {
                    com.example.crewmate.labs.LabsSettingsScreen(
                        onBack = { navController.popBackStack() }
                    )
                }
                composable(com.example.crewmate.labs.LabFeature.PAY_STATEMENT.route!!) {
                    val dataSource = remember(xcrewSession) {
                        XcrewWebViewXhrDataSource(xcrewSession, XcrewWorkScheduleParser())
                    }
                    val payStorage = remember(appContext) {
                        com.example.crewmate.labs.paystatement.PayStatementStorage(appContext)
                    }
                        val viewModel: com.example.crewmate.labs.paystatement.PayStatementViewModel =
                        androidx.lifecycle.viewmodel.compose.viewModel(
                            factory = com.example.crewmate.labs.paystatement.PayStatementViewModelFactory(
                                dataSource = dataSource,
                                storage = payStorage,
                                isOfflineModeProvider = { isOfflineMode }
                            )
                        )
                    LaunchedEffect(isOfflineMode) {
                        viewModel.syncOfflineMode(isOfflineMode)
                    }
                    com.example.crewmate.labs.LabSafeContainer(
                        feature = com.example.crewmate.labs.LabFeature.PAY_STATEMENT,
                        onDisable = { navController.popBackStack() }
                    ) {
                        com.example.crewmate.labs.paystatement.PayStatementScreen(viewModel = viewModel)
                    }
                }
                composable(com.example.crewmate.labs.LabFeature.COLLEAGUE_COMPARE.route!!) {
                    val dataSource = remember(xcrewSession) {
                        XcrewWebViewXhrDataSource(xcrewSession, XcrewWorkScheduleParser())
                    }
                    val database = remember(appContext) {
                        com.example.crewmate.database.CrewMateDatabase.getInstance(appContext)
                    }
                    val storage = remember(appContext) {
                        com.example.crewmate.labs.colleaguecompare.ColleagueCompareStorage(appContext)
                    }
                    val viewModel: com.example.crewmate.labs.colleaguecompare.ColleagueCompareViewModel =
                        androidx.lifecycle.viewmodel.compose.viewModel(
                            factory = com.example.crewmate.labs.colleaguecompare.ColleagueCompareViewModelFactory(
                                dataSource = dataSource,
                                workScheduleDao = database.workScheduleDao(),
                                crewAssignmentDao = database.crewAssignmentDao(),
                                storage = storage,
                                isOfflineModeProvider = { isOfflineMode }
                            )
                        )
                    com.example.crewmate.labs.LabSafeContainer(
                        feature = com.example.crewmate.labs.LabFeature.COLLEAGUE_COMPARE,
                        onDisable = { navController.popBackStack() }
                    ) {
                        com.example.crewmate.labs.colleaguecompare.ColleagueCompareScreen(viewModel = viewModel)
                    }
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
                        Text("소속 승무원 현황(어제·오늘·내일) 및 필수 근무 데이터를 저장하고 있습니다...", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
                    }
                },
                confirmButton = {}
            )
        }

        if (pendingOfflineConfirmReport != null) {
            AlertDialog(
                onDismissRequest = { pendingOfflineConfirmReport = null },
                containerColor = MaterialTheme.colorScheme.surface,
                title = {
                    Text(
                        "오늘자 승무원 현황 저장 실패",
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.error
                    )
                },
                text = {
                    Text(
                        "오늘자 소속 승무원 근무 현황을 서버에서 저장하지 못했습니다.\n저장 없이 전환하면 오프라인에서 승무원 현황 및 열차 승무원 조회 결과가 표시되지 않을 수 있습니다.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            pendingOfflineConfirmReport = null
                            coroutineScope.launch {
                                isPreFetchingOfflineData = true
                                val today = java.time.LocalDate.now()
                                val dataSource = XcrewWebViewXhrDataSource(xcrewSession, XcrewWorkScheduleParser())
                                val database = com.example.crewmate.database.CrewMateDatabase.getInstance(appContext)
                                val crewStatusRepo = CrewWorkStatusRepository(dataSource, database.crewAssignmentDao()) { false }
                                val retryReport = withContext(Dispatchers.IO) {
                                    OfflineCrewPrefetcher.prefetchWindow(
                                        repo = crewStatusRepo,
                                        dao = database.crewAssignmentDao(),
                                        today = today,
                                        context = appContext,
                                        skipIfCachedToday = false
                                    )
                                }
                                isPreFetchingOfflineData = false
                                if (!retryReport.todayOk) {
                                    pendingOfflineConfirmReport = retryReport
                                } else {
                                    isOfflineMode = true
                                    prefs.edit().putBoolean("is_offline_mode", true).apply()
                                    android.widget.Toast.makeText(appContext, "오프라인 모드로 전환되었습니다. (데이터 저장 완료)", android.widget.Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    ) {
                        Text("재시도", fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    Row {
                        TextButton(
                            onClick = {
                                pendingOfflineConfirmReport = null
                                isOfflineMode = true
                                prefs.edit().putBoolean("is_offline_mode", true).apply()
                                android.widget.Toast.makeText(appContext, "오늘자 승무원 현황 저장 없이 오프라인 모드로 전환되었습니다.", android.widget.Toast.LENGTH_SHORT).show()
                            }
                        ) {
                            Text("저장 없이 전환", color = MaterialTheme.colorScheme.error)
                        }
                        TextButton(onClick = { pendingOfflineConfirmReport = null }) {
                            Text("취소")
                        }
                    }
                }
            )
        }

        // 자동 감지된 신규 업데이트 팝업
        val currentAutoUpdate = autoUpdateInfo
        if (currentAutoUpdate != null) {
            UpdateNoticeDialog(
                updateInfo = currentAutoUpdate,
                onDownloadAndInstall = {
                    autoUpdateInfo = null
                    AppUpdateManager.openInBrowser(appContext, currentAutoUpdate.downloadUrl)
                },
                onOpenInBrowser = {
                    autoUpdateInfo = null
                    AppUpdateManager.openInBrowser(appContext, currentAutoUpdate.downloadUrl)
                },
                onDismissForToday = {
                    AppUpdateManager.dismissForToday(appContext, currentAutoUpdate.latestVersion)
                    autoUpdateInfo = null
                },
                onDismiss = {
                    autoUpdateInfo = null
                }
            )
        }

        if (showInitialAppInfoDialog) {
            com.example.crewmate.util.AppInfoAndTermsDialog(
                onDismiss = {
                    prefs.edit().putBoolean("has_seen_initial_app_info", true).apply()
                    showInitialAppInfoDialog = false
                }
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
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val prefs = remember { context.getSharedPreferences("crewmate_prefs", Context.MODE_PRIVATE) }
    var autoOfflineEnabled by remember { mutableStateOf(AutoOfflineManager.isEnabled(context)) }
    var isReturnSkipped by remember { mutableStateOf(AutoOfflineManager.isReturnSkipped(context)) }
    val labsUnlocked = com.example.crewmate.labs.rememberLabsUnlocked()
    val labStates = com.example.crewmate.labs.rememberAllLabStates()
    val enabledStandaloneLabs = remember(labStates) {
        com.example.crewmate.labs.LabFeature.entries.filter {
            it.entryType == com.example.crewmate.labs.LabEntryType.STANDALONE_SCREEN &&
                it.route != null &&
                labStates[it] == true
        }
    }

    DisposableEffect(prefs) {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == AutoOfflineManager.PREF_AUTO_OFFLINE) {
                autoOfflineEnabled = AutoOfflineManager.isEnabled(context)
            }
            if (key == AutoOfflineManager.PREF_RETURN_SKIPPED) {
                isReturnSkipped = AutoOfflineManager.isReturnSkipped(context)
            }
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose {
            prefs.unregisterOnSharedPreferenceChangeListener(listener)
        }
    }

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
                            if (enabledStandaloneLabs.isNotEmpty()) {
                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, thickness = 1.dp, modifier = Modifier.padding(vertical = 4.dp))
                                enabledStandaloneLabs.forEach { labFeature ->
                                    val targetRoute = labFeature.route ?: return@forEach
                                    MenuDropdownRow(
                                        title = "🧪 ${labFeature.title}",
                                        selected = currentRoute == targetRoute,
                                        enabled = !labFeature.requiresOnline || !isOfflineMode,
                                        onClick = {
                                            menuExpanded = false
                                            onSelectMenu(targetRoute)
                                        },
                                    )
                                }
                            }
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, thickness = 1.dp, modifier = Modifier.padding(vertical = 4.dp))
                            MenuDropdownRow(
                                title = "설정",
                                selected = currentRoute == Routes.Settings,
                                onClick = {
                                    menuExpanded = false
                                    onSelectMenu(Routes.Settings)
                                },
                            )
                            if (labsUnlocked) {
                                MenuDropdownRow(
                                    title = "실험실",
                                    selected = currentRoute == Routes.Labs,
                                    onClick = {
                                        menuExpanded = false
                                        onSelectMenu(Routes.Labs)
                                    },
                                )
                            }
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

                    if (isOfflineMode && autoOfflineEnabled) {
                        Spacer(Modifier.width(6.dp))
                        if (!isReturnSkipped) {
                            OutlinedButton(
                                onClick = {
                                    AutoOfflineManager.skipOnlineReturn(context)
                                    isReturnSkipped = true
                                    Toast.makeText(context, "온라인 자동 복귀가 스킵되었습니다 (오프라인 유지).", Toast.LENGTH_SHORT).show()
                                },
                                shape = RoundedCornerShape(6.dp),
                                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                                modifier = Modifier.height(24.dp),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFD97706)),
                                border = BorderStroke(1.dp, Color(0xFFD97706))
                            ) {
                                Text("복귀 Skip", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            }
                        } else {
                            OutlinedButton(
                                onClick = {
                                    AutoOfflineManager.resetReturnSkipped(context)
                                    isReturnSkipped = false
                                    coroutineScope.launch(Dispatchers.IO) {
                                        AutoOfflineManager.syncFromDatabase(context.applicationContext)
                                    }
                                    Toast.makeText(context, "온라인 복귀가 다시 예약되었습니다.", Toast.LENGTH_SHORT).show()
                                },
                                shape = RoundedCornerShape(6.dp),
                                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                                modifier = Modifier.height(24.dp),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.primary),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary)
                            ) {
                                Text("복귀 활성", fontSize = 10.sp, fontWeight = FontWeight.Bold)
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
