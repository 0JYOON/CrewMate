package com.example.crewmate

import android.content.Context
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
import com.example.crewmate.ui.theme.AppThemeMode
import com.example.crewmate.ui.theme.CrewMateTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val context = LocalContext.current
            val prefs = remember { context.getSharedPreferences("crewmate_prefs", Context.MODE_PRIVATE) }

            var themeModeName by remember {
                mutableStateOf(prefs.getString("app_theme", AppThemeMode.SYSTEM.name) ?: AppThemeMode.SYSTEM.name)
            }

            DisposableEffect(Unit) {
                val listener = SharedPreferences.OnSharedPreferenceChangeListener { sharedPreferences, key ->
                    if (key == "app_theme") {
                        themeModeName = sharedPreferences.getString("app_theme", AppThemeMode.SYSTEM.name) ?: AppThemeMode.SYSTEM.name
                    }
                }
                prefs.registerOnSharedPreferenceChangeListener(listener)
                onDispose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
            }

            val appThemeMode = AppThemeMode.entries.find { it.name == themeModeName } ?: AppThemeMode.SYSTEM

            CrewMateTheme(appThemeMode = appThemeMode) {
                AppNavigation()
            }
        }
    }
}
