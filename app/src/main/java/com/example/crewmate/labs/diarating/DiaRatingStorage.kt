package com.example.crewmate.labs.diarating

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

/**
 * 다이아 평가 기준 및 수동 등급 오버라이드 로컬 저장소.
 */
object DiaRatingStorage {
    private const val PREF_NAME = "crewmate_dia_rating"
    private const val KEY_RULES_JSON = "rules_json"

    private val _rulesFlow = MutableStateFlow(DiaRatingRules())
    val rulesFlow: StateFlow<DiaRatingRules> = _rulesFlow.asStateFlow()

    @Volatile
    private var loaded = false

    fun getRules(context: Context): DiaRatingRules {
        if (!loaded) {
            synchronized(this) {
                if (!loaded) {
                    _rulesFlow.value = readFromPrefs(context.applicationContext)
                    loaded = true
                }
            }
        }
        return _rulesFlow.value
    }

    fun saveRules(context: Context, rules: DiaRatingRules) {
        val appCtx = context.applicationContext
        val json = toJson(rules)
        appCtx.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_RULES_JSON, json)
            .apply()
        loaded = true
        _rulesFlow.value = rules
    }

    fun setCustomOverride(context: Context, diaNo: String, grade: DiaRatingGrade?) {
        val clean = diaNo.trim().removePrefix("~")
        if (clean.isEmpty()) return
        val current = getRules(context)
        val nextOverrides = current.customOverrides.toMutableMap()
        if (grade == null) {
            nextOverrides.remove(clean)
        } else {
            nextOverrides[clean] = grade
        }
        saveRules(context, current.copy(customOverrides = nextOverrides))
    }

    private fun readFromPrefs(context: Context): DiaRatingRules {
        val raw = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .getString(KEY_RULES_JSON, null)
            ?: return DiaRatingRules()

        return runCatching {
            val obj = JSONObject(raw)
            val preferred = mutableSetOf<String>()
            obj.optJSONArray("preferredSleepStations")?.let { arr ->
                for (i in 0 until arr.length()) {
                    arr.optString(i).trim().takeIf { it.isNotEmpty() }?.let(preferred::add)
                }
            }
            val heavyStations = mutableSetOf<String>()
            obj.optJSONArray("heavySleepStations")?.let { arr ->
                for (i in 0 until arr.length()) {
                    arr.optString(i).trim().takeIf { it.isNotEmpty() }?.let(heavyStations::add)
                }
            }
            val overrides = mutableMapOf<String, DiaRatingGrade>()
            obj.optJSONObject("customOverrides")?.let { mapObj ->
                mapObj.keys().forEach { key ->
                    val grade = runCatching { DiaRatingGrade.valueOf(mapObj.optString(key)) }.getOrNull()
                    if (grade != null) overrides[key] = grade
                }
            }
            DiaRatingRules(
                lightMaxDriveMinutes = obj.optInt("lightMaxDriveMinutes", 240).coerceIn(60, 600),
                heavyMinDriveMinutes = obj.optInt("heavyMinDriveMinutes", 360).coerceIn(120, 720),
                heavyMinTotalMinutes = obj.optInt("heavyMinTotalMinutes", 600).coerceIn(240, 1440),
                preferredSleepStations = preferred,
                heavySleepStations = heavyStations,
                customOverrides = overrides,
                showCellTint = obj.optBoolean("showCellTint", true)
            )
        }.getOrDefault(DiaRatingRules())
    }

    private fun toJson(rules: DiaRatingRules): String {
        return JSONObject().apply {
            put("lightMaxDriveMinutes", rules.lightMaxDriveMinutes)
            put("heavyMinDriveMinutes", rules.heavyMinDriveMinutes)
            put("heavyMinTotalMinutes", rules.heavyMinTotalMinutes)
            put("preferredSleepStations", JSONArray(rules.preferredSleepStations.toList()))
            put("heavySleepStations", JSONArray(rules.heavySleepStations.toList()))
            put("customOverrides", JSONObject().apply {
                rules.customOverrides.forEach { (k, v) -> put(k, v.name) }
            })
            put("showCellTint", rules.showCellTint)
        }.toString()
    }
}
