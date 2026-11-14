package com.example.crewmate.data.remote.xcrew

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

data class XcrewNetworkLogEntry(
    val timestampMillis: Long,
    val actionName: String,
    val endpoint: String,
    val success: Boolean,
    val summary: String
)

/**
 * Xcrew 서버와의 네트워크 통신 이력(시각, 기능명, 엔드포인트, 성공 여부)을 저장하는 로컬 저장소.
 * - 보안 원칙: 사번, 비밀번호, 쿠키, JSESSIONID, 인증 토큰, 요청 파라미터는 절대 저장하지 않습니다.
 * - 보존 기간: 당일을 포함한 앞뒤 1일(어제 ~ 오늘 ~ 내일) 이력만 유지하고 범위 밖 데이터는 자동 삭제합니다.
 */
object XcrewNetworkLogStore {

    private const val PREFS_NAME = "crewmate_xcrew_net_logs"
    private const val KEY_LOGS_JSON = "logs_json"
    private const val MAX_ENTRIES = 300

    @Volatile
    private var appContext: Context? = null

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    fun resolveActionName(path: String): String = when {
        path.contains("searchExtrIndCrewWrk") -> "월간/개인 근무표 조회"
        path.contains("searchExtrCrewDia") -> "다이아 상세 조회"
        path.contains("searchExtrCrewWrkPstt") -> "소속 승무원 근무 현황"
        path.contains("searchExtrTrnQryItmtGtwc") -> "출무 현황 조회"
        path.contains("searchExtrNonActvPstt") -> "비실동 현황 조회"
        path.contains("login", ignoreCase = true) -> "Xcrew 로그인"
        else -> "Xcrew 서버 요청"
    }

    /**
     * 기준일(referenceDate) 기준 앞뒤 1일(어제 ~ 오늘 ~ 내일) 범위의 이력만 남기고 정리합니다.
     */
    fun pruneEntries(
        entries: List<XcrewNetworkLogEntry>,
        referenceDate: LocalDate = LocalDate.now(),
        zoneId: ZoneId = ZoneId.systemDefault()
    ): List<XcrewNetworkLogEntry> {
        val minDate = referenceDate.minusDays(1)
        val maxDate = referenceDate.plusDays(1)
        return entries
            .filter { entry ->
                val d = Instant.ofEpochMilli(entry.timestampMillis).atZone(zoneId).toLocalDate()
                !d.isBefore(minDate) && !d.isAfter(maxDate)
            }
            .sortedByDescending { it.timestampMillis }
            .take(MAX_ENTRIES)
    }

    fun serialize(entries: List<XcrewNetworkLogEntry>): String {
        val arr = JSONArray()
        entries.forEach { e ->
            arr.put(
                JSONObject().apply {
                    put("ts", e.timestampMillis)
                    put("action", e.actionName)
                    put("endpoint", e.endpoint)
                    put("ok", e.success)
                    put("summary", e.summary)
                }
            )
        }
        return arr.toString()
    }

    fun deserialize(raw: String): List<XcrewNetworkLogEntry> {
        if (raw.isBlank()) return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            buildList {
                for (i in 0 until arr.length()) {
                    val obj = arr.optJSONObject(i) ?: continue
                    add(
                        XcrewNetworkLogEntry(
                            timestampMillis = obj.optLong("ts", 0L),
                            actionName = obj.optString("action", "Xcrew 요청"),
                            endpoint = obj.optString("endpoint", ""),
                            success = obj.optBoolean("ok", false),
                            summary = obj.optString("summary", "")
                        )
                    )
                }
            }
        }.getOrDefault(emptyList())
    }

    @Synchronized
    fun record(
        context: Context,
        actionName: String,
        endpoint: String,
        success: Boolean,
        summary: String,
        timestampMillis: Long = System.currentTimeMillis()
    ) {
        val appCtx = context.applicationContext
        appContext = appCtx
        val prefs = appCtx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val current = deserialize(prefs.getString(KEY_LOGS_JSON, "").orEmpty())
        val safeEndpoint = endpoint.substringBefore("?").take(120)
        val newEntry = XcrewNetworkLogEntry(
            timestampMillis = timestampMillis,
            actionName = actionName,
            endpoint = safeEndpoint,
            success = success,
            summary = summary.take(80)
        )
        val pruned = pruneEntries(listOf(newEntry) + current)
        prefs.edit().putString(KEY_LOGS_JSON, serialize(pruned)).apply()
    }

    fun recordIfInitialized(
        actionName: String,
        endpoint: String,
        success: Boolean,
        summary: String,
        timestampMillis: Long = System.currentTimeMillis()
    ) {
        val ctx = appContext ?: return
        record(ctx, actionName, endpoint, success, summary, timestampMillis)
    }

    @Synchronized
    fun getLogs(context: Context): List<XcrewNetworkLogEntry> {
        val appCtx = context.applicationContext
        appContext = appCtx
        val prefs = appCtx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val raw = prefs.getString(KEY_LOGS_JSON, "").orEmpty()
        val parsed = deserialize(raw)
        val pruned = pruneEntries(parsed)
        if (pruned.size != parsed.size) {
            prefs.edit().putString(KEY_LOGS_JSON, serialize(pruned)).apply()
        }
        return pruned
    }

    @Synchronized
    fun clearLogs(context: Context) {
        context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .remove(KEY_LOGS_JSON)
            .apply()
    }
}
