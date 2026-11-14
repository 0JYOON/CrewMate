package com.example.crewmate

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.example.crewmate.navigation.AppNavigation
import com.example.crewmate.ui.theme.AppFontOption
import com.example.crewmate.ui.theme.AppThemeMode
import com.example.crewmate.ui.theme.CrewMateTheme

class MainActivity : ComponentActivity() {

    private val initialRouteState = mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        com.example.crewmate.data.remote.xcrew.XcrewNetworkLogStore.init(this)
        com.example.crewmate.login.DailyBanCheckReceiver.scheduleNextDailyCheck(this)
        initialRouteState.value = intent?.getStringExtra("target_route")
        if (intent?.getBooleanExtra("stopAlarmService", false) == true) {
            com.example.crewmate.alarm.AlarmLoopService.stop(this)
        }

        setContent {
            val context = LocalContext.current
            val prefs = remember { context.getSharedPreferences("crewmate_prefs", Context.MODE_PRIVATE) }

            var themeModeName by remember {
                mutableStateOf(prefs.getString("app_theme", AppThemeMode.SYSTEM.name) ?: AppThemeMode.SYSTEM.name)
            }
            var fontOptionName by remember {
                mutableStateOf(prefs.getString("app_font", AppFontOption.SYSTEM.name) ?: AppFontOption.SYSTEM.name)
            }

            DisposableEffect(Unit) {
                val listener = SharedPreferences.OnSharedPreferenceChangeListener { sharedPreferences, key ->
                    if (key == "app_theme") {
                        themeModeName = sharedPreferences.getString("app_theme", AppThemeMode.SYSTEM.name) ?: AppThemeMode.SYSTEM.name
                    } else if (key == "app_font") {
                        fontOptionName = sharedPreferences.getString("app_font", AppFontOption.SYSTEM.name) ?: AppFontOption.SYSTEM.name
                    }
                }
                prefs.registerOnSharedPreferenceChangeListener(listener)
                onDispose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
            }

            val appThemeMode = AppThemeMode.entries.find { it.name == themeModeName } ?: AppThemeMode.SYSTEM
            val appFontOption = AppFontOption.entries.find { it.name == fontOptionName } ?: AppFontOption.SYSTEM
            val targetRoute by initialRouteState

            CrewMateTheme(appThemeMode = appThemeMode, appFontOption = appFontOption) {
                AppNavigation(initialTargetRoute = targetRoute)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.getBooleanExtra("stopAlarmService", false)) {
            com.example.crewmate.alarm.AlarmLoopService.stop(this)
        }
        val route = intent.getStringExtra("target_route")
        if (!route.isNullOrBlank()) {
            initialRouteState.value = route
        }
    }
}
