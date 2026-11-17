package com.example.crewmate.labs

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext

/**
 * 실험실(Labs) 기능의 활성화 상태를 영속화하고 실시간 관찰을 제공하는 매니저.
 *
 * `crewmate_prefs`에 `lab_enabled_<id>` 키로 저장되므로 [com.example.crewmate.data.backup.DataBackupManager]의
 * 백업/복원 대상에 자동 포함됩니다.
 */
object LabsManager {
    private const val TAG = "LabsManager"
    const val PREFS_NAME = "crewmate_prefs"
    const val PREF_KEY_PREFIX = "lab_enabled_"
    const val PREF_KEY_LABS_UNLOCKED = "labs_menu_unlocked"
    const val PREF_KEY_DIA_LOOKUP_DEFAULT_VALUE = "lab_dia_lookup_default_value"

    fun prefKey(feature: LabFeature): String = "$PREF_KEY_PREFIX${feature.id}"

    private fun getPrefs(context: Context): SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun isLabsUnlocked(context: Context): Boolean =
        isLabsUnlocked(getPrefs(context))

    fun isLabsUnlocked(prefs: SharedPreferences): Boolean =
        prefs.getBoolean(PREF_KEY_LABS_UNLOCKED, false)

    fun setLabsUnlocked(context: Context, unlocked: Boolean) {
        setLabsUnlocked(getPrefs(context), unlocked)
    }

    fun setLabsUnlocked(prefs: SharedPreferences, unlocked: Boolean) {
        prefs.edit().putBoolean(PREF_KEY_LABS_UNLOCKED, unlocked).apply()
    }

    fun isEnabled(context: Context, feature: LabFeature): Boolean =
        isEnabled(getPrefs(context), feature)

    fun isEnabled(prefs: SharedPreferences, feature: LabFeature): Boolean {
        if (!isLabsUnlocked(prefs)) return false
        if (!feature.isImplemented) return false
        return prefs.getBoolean(prefKey(feature), feature.defaultEnabled)
    }

    fun setEnabled(context: Context, feature: LabFeature, enabled: Boolean) {
        setEnabled(getPrefs(context), feature, enabled)
    }

    fun setEnabled(prefs: SharedPreferences, feature: LabFeature, enabled: Boolean) {
        if (!feature.isImplemented && enabled) return
        prefs.edit().putBoolean(prefKey(feature), enabled).apply()
    }

    fun getAllStates(context: Context): Map<LabFeature, Boolean> =
        getAllStates(getPrefs(context))

    fun getAllStates(prefs: SharedPreferences): Map<LabFeature, Boolean> =
        LabFeature.entries.associateWith { isEnabled(prefs, it) }

    fun getDiaLookupDefaultValue(context: Context): String =
        getDiaLookupDefaultValue(getPrefs(context))

    fun getDiaLookupDefaultValue(prefs: SharedPreferences): String =
        prefs.getString(PREF_KEY_DIA_LOOKUP_DEFAULT_VALUE, "")?.trim().orEmpty()

    fun setDiaLookupDefaultValue(context: Context, value: String) {
        setDiaLookupDefaultValue(getPrefs(context), value)
    }

    fun setDiaLookupDefaultValue(prefs: SharedPreferences, value: String) {
        val cleaned = value.trim().filter { it.isDigit() }
        prefs.edit().putString(PREF_KEY_DIA_LOOKUP_DEFAULT_VALUE, cleaned).apply()
    }

    fun resetAll(context: Context) {
        resetAll(getPrefs(context))
    }

    fun resetAll(prefs: SharedPreferences) {
        val editor = prefs.edit()
        LabFeature.entries.forEach { feature ->
            editor.remove(prefKey(feature))
        }
        editor.remove(PREF_KEY_DIA_LOOKUP_DEFAULT_VALUE)
        editor.apply()
    }

    /**
     * 실험실 기능 실행 중 예외가 발생해도 앱 전체가 중단되지 않도록 격리 실행합니다.
     */
    fun <T> runLabSafely(feature: LabFeature, block: () -> T): Result<T> {
        return runCatching(block).onFailure { error ->
            Log.w(TAG, "Lab feature '${feature.id}' failed safely: ${error.javaClass.simpleName}")
        }
    }
}

@Composable
fun rememberLabEnabled(feature: LabFeature): Boolean {
    val context = LocalContext.current
    val prefs = remember(context) {
        context.applicationContext.getSharedPreferences(LabsManager.PREFS_NAME, Context.MODE_PRIVATE)
    }
    var enabled by remember(prefs, feature) {
        mutableStateOf(LabsManager.isEnabled(prefs, feature))
    }

    DisposableEffect(prefs, feature) {
        val targetKey = LabsManager.prefKey(feature)
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { sharedPreferences, key ->
            if (key == targetKey || key == LabsManager.PREF_KEY_LABS_UNLOCKED || key == null) {
                enabled = LabsManager.isEnabled(sharedPreferences, feature)
            }
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose {
            prefs.unregisterOnSharedPreferenceChangeListener(listener)
        }
    }
    return enabled
}

@Composable
fun rememberAllLabStates(): Map<LabFeature, Boolean> {
    val context = LocalContext.current
    val prefs = remember(context) {
        context.applicationContext.getSharedPreferences(LabsManager.PREFS_NAME, Context.MODE_PRIVATE)
    }
    var states by remember(prefs) {
        mutableStateOf(LabsManager.getAllStates(prefs))
    }

    DisposableEffect(prefs) {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { sharedPreferences, key ->
            if (key == null || key.startsWith(LabsManager.PREF_KEY_PREFIX) || key == LabsManager.PREF_KEY_LABS_UNLOCKED) {
                states = LabsManager.getAllStates(sharedPreferences)
            }
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose {
            prefs.unregisterOnSharedPreferenceChangeListener(listener)
        }
    }
    return states
}

@Composable
fun rememberLabsUnlocked(): Boolean {
    val context = LocalContext.current
    val prefs = remember(context) {
        context.applicationContext.getSharedPreferences(LabsManager.PREFS_NAME, Context.MODE_PRIVATE)
    }
    var unlocked by remember(prefs) {
        mutableStateOf(LabsManager.isLabsUnlocked(prefs))
    }

    DisposableEffect(prefs) {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { sharedPreferences, key ->
            if (key == null || key == LabsManager.PREF_KEY_LABS_UNLOCKED) {
                unlocked = LabsManager.isLabsUnlocked(sharedPreferences)
            }
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose {
            prefs.unregisterOnSharedPreferenceChangeListener(listener)
        }
    }
    return unlocked
}

@Composable
fun rememberDiaLookupDefaultValue(): String {
    val context = LocalContext.current
    val prefs = remember(context) {
        context.applicationContext.getSharedPreferences(LabsManager.PREFS_NAME, Context.MODE_PRIVATE)
    }
    var defaultVal by remember(prefs) {
        mutableStateOf(LabsManager.getDiaLookupDefaultValue(prefs))
    }

    DisposableEffect(prefs) {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { sharedPreferences, key ->
            if (key == null || key == LabsManager.PREF_KEY_DIA_LOOKUP_DEFAULT_VALUE) {
                defaultVal = LabsManager.getDiaLookupDefaultValue(sharedPreferences)
            }
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose {
            prefs.unregisterOnSharedPreferenceChangeListener(listener)
        }
    }
    return defaultVal
}
