package com.example.crewmate.data.remote.xcrew

import org.json.JSONException
import org.json.JSONObject
import org.json.JSONTokener

data class NonOpData(
    val startDate: String,
    val endDate: String,
    val name: String,
    val days: Float,
    val reason: String
)

class NonOpStatusParser {
    fun parse(json: String): List<NonOpData> {
        val root = try {
            JSONTokener(json).nextValue()
        } catch (_: JSONException) {
            throw RuntimeException("JSON 파싱 에러")
        }

        val arraysToSearch = mutableListOf<org.json.JSONArray>()
        if (root is org.json.JSONArray) {
            arraysToSearch.add(root)
        } else if (root is JSONObject) {
            root.keys().forEach { k ->
                val arr = root.optJSONArray(k)
                if (arr != null) arraysToSearch.add(arr)
            }
        }

        if (arraysToSearch.isEmpty()) {
            val keys = if (root is JSONObject) root.keys().asSequence().joinToString() else root.javaClass.simpleName
            throw RuntimeException("데이터 배열 없음 (키: $keys)")
        }

        val results = mutableListOf<NonOpData>()
        var maxKeys = ""

        for (arr in arraysToSearch) {
            for (i in 0 until arr.length()) {
                val row = arr.optJSONObject(i) ?: continue

                fun JSONObject.anyString(vararg keys: String): String {
                    for (k in keys) {
                        val v = optString(k).trim()
                        if (v.isNotEmpty() && v != "null") return v
                    }
                    return ""
                }

                val stDt = row.anyString("nonAtulStDt", "stDt", "bgDt", "strtDt", "frDt", "pjtDt", "nonActvStDt")
                val endDt = row.anyString("nonAtulClsDt", "edDt", "endDt", "toDt", "clsDt", "nonActvEdDt")
                val name = row.anyString("nonAtulClsfCdNm", "nonAtulNm", "nonActvNm", "nonOpNm", "nonActvKndNm", "cdNm", "psttNm", "name")
                
                val daysStr = row.anyString("nonAtulDno", "dys", "dy", "days", "nonActvDys", "totDys")
                val days = daysStr.toFloatOrNull() ?: 1.0f
                
                val reason = row.anyString("nonAtulRsnCont", "rsn", "rmk", "resn", "memo", "note")

                if (stDt.isNotBlank() || name.isNotBlank()) {
                    results.add(NonOpData(stDt, endDt, name.ifBlank { "기타(명칭없음)" }, days, reason))
                }

                if (i == 0) {
                    maxKeys = row.keys().asSequence().joinToString(", ")
                }
            }
        }

        if (results.isEmpty() && arraysToSearch.any { it.length() > 0 }) {
            throw RuntimeException("배열은 있으나 파싱 실패. 발견된 키: $maxKeys")
        }

        return results
    }
}
