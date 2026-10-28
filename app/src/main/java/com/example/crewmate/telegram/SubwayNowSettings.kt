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

object SubwayNowSettings {
    private const val PREF_NAME = "crewmate_prefs"
    const val KEY_ENABLED = "telegram_bot_enabled"
    const val KEY_CHAT_ID = "telegram_chat_id"
    const val KEY_VERIFIED = "telegram_verified"
    const val KEY_SEND_LEAD_MIN = "telegram_send_lead_minutes"
    const val KEY_TRACK_LEAD_MIN = "telegram_track_lead_minutes"
    const val KEY_TRACK_INTERVAL = "telegram_track_interval"
    const val KEY_SERVER_URL = "telegram_server_url"

    const val DEFAULT_SERVER_URL = "http://129.225.156.25:8088"
    const val DEFAULT_SEND_LEAD = 30
    const val DEFAULT_TRACK_LEAD = 15
    const val DEFAULT_TRACK_INTERVAL = 3

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
}
