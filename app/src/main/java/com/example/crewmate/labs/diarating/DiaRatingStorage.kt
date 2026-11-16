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
            val importedMap = mutableMapOf<String, ImportedDiaRating>()
            obj.optJSONObject("importedRatings")?.let { impObj ->
                impObj.keys().forEach { key ->
                    val item = impObj.optJSONObject(key) ?: return@forEach
                    val grade = runCatching { DiaRatingGrade.valueOf(item.optString("grade")) }.getOrNull() ?: return@forEach
                    importedMap[key] = ImportedDiaRating(
                        grade = grade,
                        customLabel = item.optString("customLabel", "").trim(),
                        pattern = item.optString("pattern", "").trim(),
                        driveRatioPct = item.optDouble("driveRatioPct", 0.0),
                        note = item.optString("note", "").trim()
                    )
                }
            }
            DiaRatingRules(
                lightMaxDriveMinutes = obj.optInt("lightMaxDriveMinutes", 240).coerceIn(60, 600),
                heavyMinDriveMinutes = obj.optInt("heavyMinDriveMinutes", 360).coerceIn(120, 720),
                heavyMinTotalMinutes = obj.optInt("heavyMinTotalMinutes", 600).coerceIn(240, 1440),
                preferredSleepStations = preferred,
                heavySleepStations = heavyStations,
                customOverrides = overrides,
                showCellTint = obj.optBoolean("showCellTint", true),
                importedRatings = importedMap
            )
        }.getOrDefault(DiaRatingRules())
    }

    fun importPackJson(context: Context, rawJson: String): Result<Int> = runCatching {
        val root = JSONObject(rawJson.trim())
        val itemsObj = root.optJSONObject("items") ?: error("유효한 다이아 평가 자료(items)가 없습니다.")
        val parsed = mutableMapOf<String, ImportedDiaRating>()
        itemsObj.keys().forEach { key ->
            val cleanDia = key.trim().removePrefix("~")
            val item = itemsObj.optJSONObject(key) ?: return@forEach
            val gradeStr = item.optString("grade", "NORMAL").trim().uppercase()
            val grade = runCatching { DiaRatingGrade.valueOf(gradeStr) }.getOrDefault(DiaRatingGrade.NORMAL)
            if (cleanDia.isNotEmpty()) {
                parsed[cleanDia] = ImportedDiaRating(
                    grade = grade,
                    customLabel = item.optString("customLabel", "").trim(),
                    pattern = item.optString("pattern", "").trim(),
                    driveRatioPct = item.optDouble("driveRatioPct", 0.0),
                    note = item.optString("note", "").trim()
                )
            }
        }
        require(parsed.isNotEmpty()) { "등록할 다이아 평가 항목이 0건입니다." }
        val current = getRules(context)
        saveRules(context, current.copy(importedRatings = parsed))
        parsed.size
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
            put("importedRatings", JSONObject().apply {
                rules.importedRatings.forEach { (k, v) ->
                    put(k, JSONObject().apply {
                        put("grade", v.grade.name)
                        put("customLabel", v.customLabel)
                        put("pattern", v.pattern)
                        put("driveRatioPct", v.driveRatioPct)
                        put("note", v.note)
                    })
                }
            })
        }.toString()
    }
}
