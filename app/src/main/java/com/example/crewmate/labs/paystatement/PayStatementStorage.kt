package com.example.crewmate.labs.paystatement

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class CachedPayStatementMonth(
    val rows: List<PayStatementRow>,
    val updatedAt: String?
)

/**
 * 개인근무명세서 월별 로컬 캐시 저장소 (오프라인 모드 조회 지원).
 */
class PayStatementStorage(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun loadMonth(ymKey: String): CachedPayStatementMonth? {
        val raw = prefs.getString("${KEY_MONTH_PREFIX}$ymKey", null) ?: return null
        return runCatching {
            val obj = JSONObject(raw)
            val updatedAt = obj.optString("updatedAt").takeIf { it.isNotBlank() }
            val arr = obj.optJSONArray("rows") ?: JSONArray()
            val rows = buildList(arr.length()) {
                for (i in 0 until arr.length()) {
                    val item = arr.optJSONObject(i) ?: continue
                    add(
                        PayStatementRow(
                            num = item.optInt("num", i + 1),
                            pjtDt = item.optString("pjtDt"),
                            pdiaNo = item.optString("pdiaNo"),
                            offdDdFlg = item.optString("offdDdFlg", "N"),
                            alcSpecDvCd = item.optString("alcSpecDvCd"),
                            pdiaDvCd = item.optString("pdiaDvCd"),
                            totPjtTnum = item.optInt("totPjtTnum", 0),
                            totPjtDst = item.optDouble("totPjtDst", 0.0),
                            hldyTnum = item.optInt("hldyTnum", 0),
                            extnTnum = item.optInt("extnTnum", 0),
                            nihtTnum = item.optInt("nihtTnum", 0)
                        )
                    )
                }
            }
            CachedPayStatementMonth(rows = rows, updatedAt = updatedAt)
        }.getOrNull()
    }

    fun saveMonth(ymKey: String, rows: List<PayStatementRow>, updatedAt: String) {
        val arr = JSONArray()
        rows.forEach { r ->
            arr.put(
                JSONObject().apply {
                    put("num", r.num)
                    put("pjtDt", r.pjtDt)
                    put("pdiaNo", r.pdiaNo)
                    put("offdDdFlg", r.offdDdFlg)
                    put("alcSpecDvCd", r.alcSpecDvCd)
                    put("pdiaDvCd", r.pdiaDvCd)
                    put("totPjtTnum", r.totPjtTnum)
                    put("totPjtDst", r.totPjtDst)
                    put("hldyTnum", r.hldyTnum)
                    put("extnTnum", r.extnTnum)
                    put("nihtTnum", r.nihtTnum)
                }
            )
        }
        val payload = JSONObject().apply {
            put("updatedAt", updatedAt)
            put("rows", arr)
        }
        prefs.edit().putString("${KEY_MONTH_PREFIX}$ymKey", payload.toString()).apply()
    }

    fun loadAutoRefresh(): Boolean =
        prefs.getBoolean(KEY_AUTO_REFRESH, false)

    fun saveAutoRefresh(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_AUTO_REFRESH, enabled).apply()
    }

    companion object {
        private const val PREFS_NAME = "crewmate_pay_statement_cache"
        private const val KEY_MONTH_PREFIX = "month_"
        private const val KEY_AUTO_REFRESH = "auto_refresh"
    }
}
