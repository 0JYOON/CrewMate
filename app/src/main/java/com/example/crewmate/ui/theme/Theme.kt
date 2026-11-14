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

enum class AppFontOption(val title: String) {
    SYSTEM("System"),
    PRETENDARD("Pretendard"),
    SUIT("SUIT"),
    SPOQA("Spoqa Han Sans Neo")
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
    errorContainer = Color(0xFFFFEBEE),
    onErrorContainer = Color(0xFFC62828),
    tertiary = Color(0xFF0F766E), // Dark Teal for specific highlights
    tertiaryContainer = Color(0xFFCCFBF1),
    onTertiaryContainer = Color(0xFF115E59)
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
    errorContainer = Color(0xFF451A1A), // Deep dark red for error backgrounds
    onErrorContainer = Color(0xFFFCA5A5), // Soft red text for error containers
    tertiary = Color(0xFF2DD4BF),
    tertiaryContainer = Color(0xFF134E4A),
    onTertiaryContainer = Color(0xFF99F6E4)
)

@Composable
fun CrewMateTheme(
    appThemeMode: AppThemeMode = AppThemeMode.SYSTEM,
    appFontOption: AppFontOption = AppFontOption.SYSTEM,
    content: @Composable () -> Unit
) {
    val darkTheme = when (appThemeMode) {
        AppThemeMode.SYSTEM -> isSystemInDarkTheme()
        AppThemeMode.LIGHT -> false
        AppThemeMode.DARK -> true
    }

    val colorScheme = if (darkTheme) DarkCrewMateColors else LightCrewMateColors
    val typography = when (appFontOption) {
        AppFontOption.SYSTEM -> Typography
        AppFontOption.PRETENDARD -> createTypography(PretendardFontFamily)
        AppFontOption.SUIT -> createTypography(SuitFontFamily)
        AppFontOption.SPOQA -> createTypography(SpoqaHanSansNeoFontFamily)
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = typography,
        shapes = Shapes,
        content = content,
    )
}

fun android.content.Context.getAppFontOption(): AppFontOption {
    val prefs = getSharedPreferences("crewmate_prefs", android.content.Context.MODE_PRIVATE)
    val fontName = prefs.getString("app_font", AppFontOption.SYSTEM.name) ?: AppFontOption.SYSTEM.name
    return AppFontOption.entries.find { it.name == fontName } ?: AppFontOption.SYSTEM
}

fun android.content.Context.getCanvasTypeface(baseWeight: Int, weightAdjustment: Int = 0): android.graphics.Typeface {
    val fontOption = getAppFontOption()
    val targetWeight = (baseWeight + weightAdjustment).coerceIn(1, 1000)

    val customFontRes = when (fontOption) {
        AppFontOption.PRETENDARD -> when {
            targetWeight >= 800 -> com.example.crewmate.R.font.pretendard_extrabold
            targetWeight >= 650 -> com.example.crewmate.R.font.pretendard_bold
            targetWeight >= 500 -> com.example.crewmate.R.font.pretendard_medium
            else -> com.example.crewmate.R.font.pretendard_regular
        }
        AppFontOption.SUIT -> when {
            targetWeight >= 800 -> com.example.crewmate.R.font.suit_extrabold
            targetWeight >= 650 -> com.example.crewmate.R.font.suit_bold
            targetWeight >= 500 -> com.example.crewmate.R.font.suit_medium
            else -> com.example.crewmate.R.font.suit_regular
        }
        AppFontOption.SPOQA -> when {
            targetWeight >= 650 -> com.example.crewmate.R.font.spoqa_han_sans_neo_bold
            targetWeight >= 500 -> com.example.crewmate.R.font.spoqa_han_sans_neo_medium
            else -> com.example.crewmate.R.font.spoqa_han_sans_neo_regular
        }
        AppFontOption.SYSTEM -> null
    }
    if (customFontRes != null) {
        val tf = runCatching { androidx.core.content.res.ResourcesCompat.getFont(this, customFontRes) }.getOrNull()
        if (tf != null) return tf
    }

    val baseTypeface = runCatching {
        android.widget.TextView(this).typeface
    }.getOrNull() ?: android.graphics.Typeface.defaultFromStyle(android.graphics.Typeface.NORMAL)

    return if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
        android.graphics.Typeface.create(baseTypeface, targetWeight, false)
    } else {
        android.graphics.Typeface.create(
            baseTypeface,
            if (targetWeight >= 600) android.graphics.Typeface.BOLD else android.graphics.Typeface.NORMAL
        )
    }
}

fun android.content.Context.getAppThemeMode(): AppThemeMode {
    val prefs = getSharedPreferences("crewmate_prefs", android.content.Context.MODE_PRIVATE)
    val themeModeName = prefs.getString("app_theme", AppThemeMode.SYSTEM.name) ?: AppThemeMode.SYSTEM.name
    return AppThemeMode.entries.find { it.name == themeModeName } ?: AppThemeMode.SYSTEM
}

fun android.content.Context.isDarkThemeActive(appThemeMode: AppThemeMode = getAppThemeMode()): Boolean {
    return when (appThemeMode) {
        AppThemeMode.SYSTEM -> {
            val nightFlags = resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK
            nightFlags == android.content.res.Configuration.UI_MODE_NIGHT_YES
        }
        AppThemeMode.LIGHT -> false
        AppThemeMode.DARK -> true
    }
}
