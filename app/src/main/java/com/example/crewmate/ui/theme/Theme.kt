package com.example.crewmate.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

enum class AppThemeMode(val title: String) {
    SYSTEM("시스템 설정에 맞춤"),
    LIGHT("라이트 모드"),
    DARK("다크 모드")
}

private val LightCrewMateColors = lightColorScheme(
    primary = Navy,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDCE8F8),
    onPrimaryContainer = Color(0xFF0A1E3F),
    secondary = RoyalBlue,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE8EEF6),
    onSecondaryContainer = DarkText,
    background = SoftBackground,
    onBackground = DarkText,
    surface = CardSurface,
    onSurface = DarkText,
    surfaceVariant = Color(0xFFE9EEF5),
    onSurfaceVariant = MutedText,
    outline = Color(0xFFCBD5E1),
    outlineVariant = Color(0xFFE2E8F0),
    error = Color(0xFFD32F2F),
)

private val DarkCrewMateColors = darkColorScheme(
    primary = Color(0xFF38BDF8),
    onPrimary = Color(0xFF0F172A),
    primaryContainer = Color(0xFF1E3A8A),
    onPrimaryContainer = Color(0xFFE0F2FE),
    secondary = Color(0xFF60A5FA),
    onSecondary = Color(0xFF0F172A),
    secondaryContainer = Color(0xFF1E293B),
    onSecondaryContainer = Color(0xFFF1F5F9),
    background = Color(0xFF0B0F17),
    onBackground = Color(0xFFF8FAFC),
    surface = Color(0xFF161E2E),
    onSurface = Color(0xFFF8FAFC),
    surfaceVariant = Color(0xFF232D3F),
    onSurfaceVariant = Color(0xFF94A3B8),
    outline = Color(0xFF334155),
    outlineVariant = Color(0xFF263345),
    error = Color(0xFFF87171),
)

@Composable
fun CrewMateTheme(
    appThemeMode: AppThemeMode = AppThemeMode.SYSTEM,
    content: @Composable () -> Unit
) {
    val darkTheme = when (appThemeMode) {
        AppThemeMode.SYSTEM -> isSystemInDarkTheme()
        AppThemeMode.LIGHT -> false
        AppThemeMode.DARK -> true
    }

    val colorScheme = if (darkTheme) DarkCrewMateColors else LightCrewMateColors

    MaterialTheme(
        colorScheme = colorScheme,
        content = content,
    )
}
