package com.example.crewmate.data.remote.xcrew

import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import org.json.JSONTokener

data class PersonalWorkScheduleDto(
    val pjtDt: String?,
    val pdiaNo: String?,
    val repTrn1No: String?,
    val repTrn2No: String?,
    val gwkTm: String?,
    val loiwTm: String?,
    val hldyYn: String?,
    val nonAtulClsfCdNm: String? // "연차" 등 비활성 사유
)

class PersonalWorkScheduleParser {
    fun parse(json: String): List<PersonalWorkScheduleDto> {
        val root = try {
            val element = JSONTokener(json).nextValue()
            if (element is JSONArray) {
                val wrapper = JSONObject()
                wrapper.put("data", element)
                wrapper
            } else {
                element as? JSONObject ?: throw UnexpectedXcrewResponseException("PersonalSchedule JSON root is not Object/Array")
            }
        } catch (_: JSONException) {
            throw InvalidXcrewJsonException()
        }

        var data = root.optJSONArray("data")
        if (data == null || data.length() == 0) {
            root.keys().forEach { k ->
                val arr = root.optJSONArray(k)
                if (arr != null && arr.length() > 0) {
                    data = arr
                }
            }
        }
        
        if (data == null) throw UnexpectedXcrewResponseException("PersonalSchedule data array not found")

        val items = buildList(data.length()) {
            for (index in 0 until data.length()) {
                val row = data.opt(index) as? JSONObject ?: continue
                add(row.toPersonalScheduleItem())
            }
        }

        if (items.isEmpty()) {
            throw UnexpectedXcrewResponseException("데이터가 없습니다.")
        }

        return items
    }

    private fun JSONObject.toPersonalScheduleItem() = PersonalWorkScheduleDto(
        pjtDt = optionalScalar("pjtDt"),
        pdiaNo = optionalScalar("pdiaNo") ?: optionalScalar("crwPlnDvCd") ?: optionalScalar("alcSpecDvCd"),
        repTrn1No = optionalScalar("repTrn1No"),
        repTrn2No = optionalScalar("repTrn2No"),
        gwkTm = optionalScalar("gwkTm"),
        loiwTm = optionalScalar("loiwTm"),
        hldyYn = optionalScalar("hldyYn"),
        nonAtulClsfCdNm = optionalScalar("nonAtulClsfCdNm") ?: optionalScalar("nonOpNm") ?: optionalScalar("cdNm")
    )

    private fun JSONObject.optionalScalar(name: String): String? {
        val value = opt(name)
        if (value == null || value == JSONObject.NULL) return null
        if (value is String) return value
        if (value is Number || value is Boolean) return value.toString()
        return null
    }
}
