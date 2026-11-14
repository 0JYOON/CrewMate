package com.example.crewmate.telegram

import android.content.Context

data class SubwayNowConfig(
    val isEnabled: Boolean,
    val chatId: String,
    val isVerified: Boolean,
    val sendLeadMinutes: Int,    // -1 = 출근 시 일괄 전송, 30, 45, 60
    val trackLeadMinutes: Int,   // 10, 15, 20, 30
    val trackInterval: Int,      // 1, 2, 3
    val serverUrl: String
)

data class TrackTargetOverride(
    val rawCommand: String,
    val trackStartTimeStr: String,
    val sendTimeStr: String
)

object SubwayNowSettings {
    private const val PREF_NAME = "crewmate_prefs"
    const val KEY_ENABLED = "telegram_bot_enabled"
    const val KEY_CHAT_ID = "telegram_chat_id"
    const val KEY_VERIFIED = "telegram_verified"
    const val KEY_SEND_LEAD_MIN = "telegram_send_lead_minutes"
    const val KEY_TRACK_LEAD_MIN = "telegram_track_lead_minutes"
    const val KEY_TRACK_INTERVAL = "telegram_track_interval"
    const val KEY_SERVER_URL = "telegram_server_url"
    const val KEY_STATION_REPLACEMENTS = "telegram_station_replacements"
    private const val KEY_STOPPED_TRAINS_PREFIX = "telegram_stopped_trains_"
    private const val KEY_TARGET_OVERRIDES_PREFIX = "telegram_target_overrides_"

    const val DEFAULT_SERVER_URL = "http://129.225.156.25:8088"
    const val DEFAULT_SEND_LEAD = 30
    const val DEFAULT_TRACK_LEAD = 15
    const val DEFAULT_TRACK_INTERVAL = 3

    fun getStationReplacements(context: Context): Map<String, String> {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val jsonStr = prefs.getString(KEY_STATION_REPLACEMENTS, null) ?: return emptyMap()
        return try {
            val json = org.json.JSONObject(jsonStr)
            val map = mutableMapOf<String, String>()
            json.keys().forEach { k ->
                val v = json.optString(k, "").trim()
                if (k.isNotBlank() && v.isNotBlank()) {
                    map[k.trim()] = v
                }
            }
            map
        } catch (_: Exception) {
            emptyMap()
        }
    }

    fun setStationReplacements(context: Context, map: Map<String, String>) {
        val json = org.json.JSONObject()
        map.forEach { (k, v) ->
            if (k.isNotBlank() && v.isNotBlank()) {
                json.put(k.trim(), v.trim())
            }
        }
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE).edit()
            .putString(KEY_STATION_REPLACEMENTS, json.toString())
            .apply()
    }

    fun addStationReplacement(context: Context, fromStation: String, toStation: String) {
        val cleanFrom = fromStation.trim()
        val cleanTo = toStation.trim()
        if (cleanFrom.isBlank() || cleanTo.isBlank()) return
        val current = getStationReplacements(context).toMutableMap()
        current[cleanFrom] = cleanTo
        setStationReplacements(context, current)
    }

    fun removeStationReplacement(context: Context, fromStation: String) {
        val cleanFrom = fromStation.trim()
        val current = getStationReplacements(context).toMutableMap()
        if (current.remove(cleanFrom) != null) {
            setStationReplacements(context, current)
        }
    }

    fun resolveStationName(context: Context, rawStation: String): String {
        val clean = rawStation.trim()
        val replacements = getStationReplacements(context)
        return replacements[clean] ?: clean
    }

    fun isTrainStopped(context: Context, trainNo: String, dateKey: String): Boolean {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val set = prefs.getStringSet(KEY_STOPPED_TRAINS_PREFIX + dateKey, emptySet()) ?: emptySet()
        return set.contains(trainNo.trim().uppercase())
    }

    fun setTrainStopped(context: Context, trainNo: String, dateKey: String, stopped: Boolean) {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val key = KEY_STOPPED_TRAINS_PREFIX + dateKey
        val set = (prefs.getStringSet(key, emptySet()) ?: emptySet()).toMutableSet()
        val target = trainNo.trim().uppercase()
        if (stopped) {
            set.add(target)
        } else {
            set.remove(target)
        }
        prefs.edit().putStringSet(key, set).apply()
    }

    fun getConfig(context: Context): SubwayNowConfig {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        return SubwayNowConfig(
            isEnabled = prefs.getBoolean(KEY_ENABLED, false),
            chatId = prefs.getString(KEY_CHAT_ID, "") ?: "",
            isVerified = prefs.getBoolean(KEY_VERIFIED, false),
            sendLeadMinutes = prefs.getInt(KEY_SEND_LEAD_MIN, DEFAULT_SEND_LEAD),
            trackLeadMinutes = prefs.getInt(KEY_TRACK_LEAD_MIN, DEFAULT_TRACK_LEAD),
            trackInterval = prefs.getInt(KEY_TRACK_INTERVAL, DEFAULT_TRACK_INTERVAL),
            serverUrl = prefs.getString(KEY_SERVER_URL, DEFAULT_SERVER_URL) ?: DEFAULT_SERVER_URL
        )
    }

    fun setEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE).edit()
            .putBoolean(KEY_ENABLED, enabled).apply()
    }

    fun setChatId(context: Context, chatId: String) {
        val clean = chatId.trim()
        val current = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE).getString(KEY_CHAT_ID, "")
        val editor = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE).edit()
            .putString(KEY_CHAT_ID, clean)
        if (clean != current) {
            editor.putBoolean(KEY_VERIFIED, false)
        }
        editor.apply()
    }

    fun setVerified(context: Context, verified: Boolean) {
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE).edit()
            .putBoolean(KEY_VERIFIED, verified).apply()
    }

    fun setSendLeadMinutes(context: Context, minutes: Int) {
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE).edit()
            .putInt(KEY_SEND_LEAD_MIN, minutes).apply()
    }

    fun setTrackLeadMinutes(context: Context, minutes: Int) {
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE).edit()
            .putInt(KEY_TRACK_LEAD_MIN, minutes).apply()
    }

    fun setTrackInterval(context: Context, interval: Int) {
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE).edit()
            .putInt(KEY_TRACK_INTERVAL, interval).apply()
    }

    fun setServerUrl(context: Context, url: String) {
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE).edit()
            .putString(KEY_SERVER_URL, url.trim()).apply()
    }

    fun getTargetOverrides(context: Context, dateKey: String): Map<String, TrackTargetOverride> {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val jsonStr = prefs.getString(KEY_TARGET_OVERRIDES_PREFIX + dateKey, null) ?: return emptyMap()
        return try {
            val json = org.json.JSONObject(jsonStr)
            val map = mutableMapOf<String, TrackTargetOverride>()
            json.keys().forEach { k ->
                val obj = json.optJSONObject(k)
                if (obj != null) {
                    map[k.trim().uppercase()] = TrackTargetOverride(
                        rawCommand = obj.optString("rawCommand", ""),
                        trackStartTimeStr = obj.optString("trackStartTimeStr", ""),
                        sendTimeStr = obj.optString("sendTimeStr", "")
                    )
                }
            }
            map
        } catch (_: Exception) {
            emptyMap()
        }
    }

    fun setTargetOverride(context: Context, dateKey: String, trainNo: String, override: TrackTargetOverride) {
        val current = getTargetOverrides(context, dateKey).toMutableMap()
        current[trainNo.trim().uppercase()] = override
        val json = org.json.JSONObject()
        current.forEach { (k, v) ->
            val obj = org.json.JSONObject().apply {
                put("rawCommand", v.rawCommand)
                put("trackStartTimeStr", v.trackStartTimeStr)
                put("sendTimeStr", v.sendTimeStr)
            }
            json.put(k, obj)
        }
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE).edit()
            .putString(KEY_TARGET_OVERRIDES_PREFIX + dateKey, json.toString())
            .apply()
    }

    fun removeTargetOverride(context: Context, dateKey: String, trainNo: String) {
        val current = getTargetOverrides(context, dateKey).toMutableMap()
        if (current.remove(trainNo.trim().uppercase()) != null) {
            val json = org.json.JSONObject()
            current.forEach { (k, v) ->
                val obj = org.json.JSONObject().apply {
                    put("rawCommand", v.rawCommand)
                    put("trackStartTimeStr", v.trackStartTimeStr)
                    put("sendTimeStr", v.sendTimeStr)
                }
                json.put(k, obj)
            }
            context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE).edit()
                .putString(KEY_TARGET_OVERRIDES_PREFIX + dateKey, json.toString())
                .apply()
        }
    }
}
