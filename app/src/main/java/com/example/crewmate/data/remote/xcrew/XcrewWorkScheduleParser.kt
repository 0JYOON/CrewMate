package com.example.crewmate.data.remote.xcrew

import android.util.Log
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import org.json.JSONTokener

private const val TAG = "XcrewJson"

class XcrewWorkScheduleParser {
    fun parse(json: String): XcrewWorkScheduleResponse {
        val root = try {
            val element = JSONTokener(json).nextValue()
            if (element is JSONArray) {
                // If the root itself is an array
                val wrapper = JSONObject()
                wrapper.put("data", element)
                wrapper
            } else {
                element as? JSONObject ?: throw UnexpectedXcrewResponseException("JSON 루트가 Object나 Array가 아닙니다. 타입: ${element.javaClass.simpleName}")
            }
        } catch (_: JSONException) {
            throw InvalidXcrewJsonException()
        }

        val topLevelKeys = root.keys().asSequence().toList().sorted()
        Log.i(TAG, "topLevelKeys=$topLevelKeys")
        
        var data = root.optJSONArray("data")
        if (data == null || data.length() == 0) {
            // Fallback: search for any non-empty array
            root.keys().forEach { k ->
                val arr = root.optJSONArray(k)
                if (arr != null && arr.length() > 0) {
                    data = arr
                }
            }
        }
        if (data == null) throw UnexpectedXcrewResponseException("배열을 찾을 수 없습니다. (키: ${topLevelKeys.joinToString()})")

        val items = buildList(data.length()) {
            for (index in 0 until data.length()) {
                val row = data.opt(index) as? JSONObject ?: continue
                add(row.toScheduleItem())
            }
        }
        Log.i(TAG, "dataItemCount=${items.size}")

        if (items.isEmpty()) {
            val debugKeys = if (data.length() > 0) data.optJSONObject(0)?.keys()?.asSequence()?.joinToString() else topLevelKeys.joinToString()
            throw UnexpectedXcrewResponseException("배열은 찾았으나 내용이 비어있습니다. (키: $debugKeys)")
        }

        return XcrewWorkScheduleResponse(
            data = items,
            extrCrewMgVO = root.opt("extrCrewMgVO") as? JSONObject,
        )
    }

    private fun JSONObject.toScheduleItem(): XcrewWorkScheduleItem {
        val rawPdia = optionalScalar("pdiaNo") ?: optionalScalar("crwPlnDvCd")
        val alcCode = optionalScalar("alcSpecDvCd")
        val finalPdia = if (!rawPdia.isNullOrBlank()) rawPdia else if (alcCode == "02") "S" else null

        return XcrewWorkScheduleItem(
            pjtDt = optionalScalar("pjtDt"),
            pdiaNo = finalPdia,
            repTrn1No = optionalScalar("repTrn1No"),
            gwkTm = optionalScalar("gwkTm"),
            loiwTm = optionalScalar("loiwTm"),
            alcSpecDvCd = alcCode,
            nonAtulClsfCdNm = optionalScalar("nonAtulClsfCdNm") ?: optionalScalar("nonOpNm"),
            totWrkTnum = optionalScalar("totWrkTnum"),
            repTrn2No = optionalScalar("repTrn2No"),
            hldyYn = optionalScalar("hldyYn"),
            wkdyDvCd = optionalScalar("wkdyDvCd"),
            hldyAt = optionalScalar("hldyAt"),
            pjtChgPrsFlg = optionalScalar("pjtChgPrsFlg")
        )
    }

    /** Missing, null, and non-scalar optional values do not invalidate the full response. */
    private fun JSONObject.optionalScalar(name: String): String? {
        val value = opt(name)
        if (value == null || value == JSONObject.NULL) return null
        if (value is String) return value
        if (value is Number || value is Boolean) return value.toString()
        return null
    }
}


sealed class XcrewResponseException(message: String) : Exception(message)
class InvalidXcrewJsonException : XcrewResponseException("Xcrew 응답 JSON을 파싱할 수 없습니다.")
class UnexpectedXcrewResponseException(msg: String = "예상하지 못한 Xcrew 응답 구조입니다.") : XcrewResponseException(msg)