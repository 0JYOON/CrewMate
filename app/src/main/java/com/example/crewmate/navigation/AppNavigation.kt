package com.example.crewmate.navigation

import android.annotation.SuppressLint
import android.webkit.CookieManager
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.crewmate.data.remote.xcrew.XcrewWebViewSession
import com.example.crewmate.data.remote.xcrew.XcrewWebViewXhrDataSource
import com.example.crewmate.data.remote.xcrew.XcrewWorkScheduleParser
import com.example.crewmate.login.LoginScreen
import com.example.crewmate.login.LoginViewModel
import com.example.crewmate.login.LoginViewModelFactory
import com.example.crewmate.login.SecureCredentialStore
import com.example.crewmate.schedule.ScheduleScreen
import com.example.crewmate.schedule.ScheduleViewModel
import com.example.crewmate.schedule.ScheduleViewModelFactory
import com.example.crewmate.schedule.TodayWorkRepository
import com.example.crewmate.schedule.TodayWorkScreen
import com.example.crewmate.schedule.TodayWorkViewModel
import com.example.crewmate.schedule.TodayWorkViewModelFactory
import com.example.crewmate.schedule.TrainCrewLookupScreen
import com.example.crewmate.schedule.TrainCrewLookupViewModel
import com.example.crewmate.schedule.TrainCrewLookupViewModelFactory
import com.example.crewmate.schedule.TrainCrewSearchRepository
import com.example.crewmate.schedule.WorkScheduleParser
import com.example.crewmate.schedule.WorkScheduleRepositoryImpl
import com.example.crewmate.settings.MainScreenOption
import com.example.crewmate.settings.SettingsScreen
import com.example.crewmate.ui.theme.CrewMateLogo

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
}

@Composable
fun AppNavigation() {
    val navController = rememberNavController()
    val xcrewSession = remember { XcrewWebViewSession() }
    val appContext = LocalContext.current.applicationContext
    val credentialStore = remember(appContext) { SecureCredentialStore(appContext) }
    var skipAutoLoginOnLoginScreen by remember { mutableStateOf(false) }
    val prefs = appContext.getSharedPreferences("crewmate_prefs", Context.MODE_PRIVATE)
    var isOfflineMode by remember { mutableStateOf(prefs.getBoolean("is_offline_mode", false)) }
    
    var authRootRoute by remember { 
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
                popUpTo(Routes.Login) { inclusive = true }
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
                        val prefs = appContext.getSharedPreferences("crewmate_prefs", Context.MODE_PRIVATE)
                        val mainScreenName = prefs.getString("main_screen", MainScreenOption.SCHEDULE.name)
                        val targetRoute = MainScreenOption.entries.find { it.name == mainScreenName }?.route ?: Routes.Schedule
                        navigateFromMenu(targetRoute)
                    },
                    onLogout = { handleLogout(false) },
                    onReconnect = { handleLogout(true) },
                    onToggleOfflineMode = {
                        isOfflineMode = !isOfflineMode
                        prefs.edit().putBoolean("is_offline_mode", isOfflineMode).apply()
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
                            val prefs = appContext.getSharedPreferences("crewmate_prefs", Context.MODE_PRIVATE)
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
                        onLogin = loginViewModel::login,
                        onRetryAutoLogin = loginViewModel::retryAutoLogin,
                        onSwitchToManualLogin = loginViewModel::switchToManualLogin,
                        onDeleteSavedLogin = loginViewModel::deleteSavedLogin,
                    )
                }
                composable(Routes.Schedule) {
                    val diaDetailDataSource = remember(xcrewSession) {
                        XcrewWebViewXhrDataSource(xcrewSession, XcrewWorkScheduleParser())
                    }
                    val memoRepository = remember(appContext) {
                        com.example.crewmate.database.MemoRepository(com.example.crewmate.database.CrewMateDatabase.getInstance(appContext).memoDao())
                    }
                    // 기존 레거시 ScheduleScreen 호출 코드는 주석 처리함
                    // val scheduleViewModel: ScheduleViewModel = viewModel(
                    //     factory = ScheduleViewModelFactory(xcrewSession, appContext),
                    // )
                    // ScheduleScreen(
                    //     viewModel = scheduleViewModel,
                    //     diaDetailDataSource = diaDetailDataSource,
                    // )

                    val personalViewModel: com.example.crewmate.schedule.PersonalScheduleViewModel = androidx.lifecycle.viewmodel.compose.viewModel(
                        factory = com.example.crewmate.schedule.PersonalScheduleViewModelFactory(
                            diaDetailDataSource,
                            xcrewSession,
                            memoRepository
                        )
                    )
                    com.example.crewmate.schedule.PersonalScheduleScreen(
                        viewModel = personalViewModel,
                        diaDetailDataSource = diaDetailDataSource
                    )
                }
                composable(Routes.TodayWork) {
                    val database = remember(appContext) { com.example.crewmate.database.CrewMateDatabase.getInstance(appContext) }
                    val todayWorkRepository = remember(xcrewSession, database) {
                        val scheduleDataSource = XcrewWebViewXhrDataSource(
                            xcrewSession,
                            XcrewWorkScheduleParser(),
                        )
                        val scheduleRepository = WorkScheduleRepositoryImpl(
                            scheduleDataSource,
                            WorkScheduleParser(),
                            database.workScheduleDao()
                        )
                        val crewWorkStatusRepository = com.example.crewmate.schedule.CrewWorkStatusRepository(
                            scheduleDataSource,
                            database.crewAssignmentDao()
                        )
                        TodayWorkRepository(scheduleRepository, scheduleDataSource, scheduleDataSource, crewWorkStatusRepository, xcrewSession)
                    }
                    val todayWorkViewModel: TodayWorkViewModel = viewModel(
                        factory = TodayWorkViewModelFactory(todayWorkRepository),
                    )
                    TodayWorkScreen(
                        viewModel = todayWorkViewModel,
                        onBack = { navController.popBackStack() },
                    )
                }
                composable(Routes.TrainCrewLookup) {
                    val repository = remember(xcrewSession) {
                        val dataSource = XcrewWebViewXhrDataSource(xcrewSession, XcrewWorkScheduleParser())
                        com.example.crewmate.schedule.TrainCrewSearchRepository(dataSource, dataSource)
                    }
                    val lookupViewModel: com.example.crewmate.schedule.TrainCrewLookupViewModel = viewModel(
                        factory = com.example.crewmate.schedule.TrainCrewLookupViewModelFactory(repository),
                    )
                    val diaDetailDataSource = remember(xcrewSession) {
                        XcrewWebViewXhrDataSource(xcrewSession, XcrewWorkScheduleParser())
                    }

                    com.example.crewmate.schedule.TrainCrewLookupScreen(
                        viewModel = lookupViewModel,
                        diaDetailDataSource = diaDetailDataSource,
                        onBack = { navController.popBackStack() }
                    )
                }
                composable(Routes.DiaLookup) {
                    val diaDetailDataSource = remember(xcrewSession) {
                        XcrewWebViewXhrDataSource(xcrewSession, XcrewWorkScheduleParser())
                    }
                    com.example.crewmate.schedule.DiaLookupScreen(
                        diaDetailDataSource = diaDetailDataSource
                    )
                }
                composable(Routes.CrewWorkStatus) {
                    val crewWorkStatusDataSource = remember(xcrewSession) {
                        XcrewWebViewXhrDataSource(xcrewSession, XcrewWorkScheduleParser())
                    }
                    val database = remember(appContext) { com.example.crewmate.database.CrewMateDatabase.getInstance(appContext) }
                    val crewWorkStatusRepository = remember(crewWorkStatusDataSource, database) {
                        com.example.crewmate.schedule.CrewWorkStatusRepository(crewWorkStatusDataSource, database.crewAssignmentDao())
                    }
                    com.example.crewmate.schedule.CrewWorkStatusScreen(
                        crewWorkStatusRepository = crewWorkStatusRepository,
                        diaDetailDataSource = crewWorkStatusDataSource
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
                composable(Routes.Settings) {
                    SettingsScreen()
                }
            }
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
        shadowElevation = 2.dp,
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                        ) {
                            IconButton(
                                onClick = { menuExpanded = true },
                                modifier = Modifier.size(38.dp),
                            ) {
                                Text(
                                    text = "☰",
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            }
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
                            .padding(vertical = 2.dp)
                    ) {
                        Spacer(Modifier.width(10.dp))
                        CrewMateLogo(size = 30.dp)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "CrewMate",
                            style = MaterialTheme.typography.titleLarge,
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
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                }
                
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (userInfo != null) {
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
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                    color = if (enabled) (if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    fontSize = 15.sp,
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
            WebView(context).apply {
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                settings.allowFileAccess = false
                settings.allowContentAccess = false
                CookieManager.getInstance().setAcceptThirdPartyCookies(this, false)
                webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView, url: String?) {
                        session.markPageReady()
                    }
                }
                session.attach(this)
                loadDataWithBaseURL(
                    "https://xcrew.korail.com/extrCrewMg/extrRltmCrewWrkPstt.do",
                    "<!doctype html><html><head><meta charset=\"utf-8\"></head><body></body></html>",
                    "text/html",
                    "UTF-8",
                    null,
                )
            }
        },
        update = {},
    )
}