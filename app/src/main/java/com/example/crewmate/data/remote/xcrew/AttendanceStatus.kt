package com.example.crewmate.data.remote.xcrew

import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import org.json.JSONTokener

data class AttendanceStatusDto(
    val trnNo: String?,          // 열차번호
    val trnSttNm: String?,       // 열차상태명
    val gtwcEpctBlgNm: String?,  // 출무예정소속명
    val gtwcEpctTm: String?,     // 예정출무시각
    val gtwcGubunCd: String?,    // 출무구분코드
    val pjtDt: String?,          // 사업일자
    val gtwcTpDvCd: String? = null, // 출무구분유형코드
    val flg: String? = null,     // 플래그
    val tnsmTm: String? = null,  // 실제출무시각 (tnsmTm)
    val gtwcTm: String? = null,  // 백업 시각
    val actlTm: String? = null   // 추가 실제시각
) {
    val actualTime: String?
        get() = listOfNotNull(tnsmTm, gtwcTm, actlTm).firstOrNull { 
            val s = it.trim()
            s.isNotBlank() && s != "null" && s != "-"
        }

    val isCheckedIn: Boolean
        get() {
            val act = actualTime?.trim()
            val hasActual = !act.isNullOrBlank() && act != "null" && act != "-"
            val status = gtwcGubunCd?.trim()
            // 3: 도중출무완료, 5: 적합성미완료 (실제 출무 시각 존재 여부와 무관하게 이미 출무 단계 진입), 6: 적합성완료
            val isCheckedStatus = status == "3" || status == "5" || status == "6"
            return hasActual || isCheckedStatus
        }
}

fun AttendanceStatusDto?.toGtwcGubunText(): String {
    if (this == null) return "-"
    return when (gtwcGubunCd?.trim()) {
        "1" -> "출무전"
        "2" -> if (gtwcTpDvCd?.trim() == "2") "도중출무" else "출무임박"
        "3" -> "도중출무완료"
        "4" -> if (gtwcTpDvCd?.trim() == "2" && flg?.trim()?.uppercase() == "Y") "도중출무" else "미출무"
        "5" -> "적합성미완료"
        "6" -> "적합성완료"
        "7" -> "지각"
        else -> "-"
    }
}

class AttendanceStatusParser {
    fun parse(json: String): List<AttendanceStatusDto> {
        val root = try {
            val element = JSONTokener(json).nextValue()
            if (element is JSONArray) {
                val wrapper = JSONObject()
                wrapper.put("data", element)
                wrapper
            } else {
                element as? JSONObject ?: throw UnexpectedXcrewResponseException()
            }
        } catch (_: JSONException) {
            throw InvalidXcrewJsonException()
        }

        var data = root.optJSONArray("data")
        if (data == null) {
            root.keys().forEach { k ->
                val arr = root.optJSONArray(k)
                if (arr != null && arr.length() > 0) {
                    data = arr
                }
            }
        }
        if (data == null) return emptyList()

        val defaultPjtDt = root.optJSONObject("extrCrewMgVO")?.optionalScalar("pjtDt")

        return buildList(data.length()) {
            for (i in 0 until data.length()) {
                val row = data.optJSONObject(i) ?: continue
                add(
                    AttendanceStatusDto(
                        trnNo = row.optionalScalar("trnNo"),
                        trnSttNm = row.optionalScalar("trnSttNm"),
                        gtwcEpctBlgNm = row.optionalScalar("gtwcEpctBlgNm"),
                        gtwcEpctTm = row.optionalScalar("gtwcEpctTm"),
                        gtwcGubunCd = row.optionalScalar("gtwcGubunCd"),
                        pjtDt = row.optionalScalar("pjtDt") ?: defaultPjtDt,
                        gtwcTpDvCd = row.optionalScalar("gtwcTpDvCd"),
                        flg = row.optionalScalar("flg"),
                        tnsmTm = row.optionalScalar("tnsmTm") ?: row.optionalScalar("tnsmDttm"),
                        gtwcTm = row.optionalScalar("gtwcTm") ?: row.optionalScalar("gtwcDttm"),
                        actlTm = row.optionalScalar("actlTm") ?: row.optionalScalar("actlGtwcTm") ?: row.optionalScalar("chckTm") ?: row.optionalScalar("realTm")
                    )
                )
            }
        }
    }

    private fun JSONObject.optionalScalar(name: String): String? {
        val value = opt(name)
        if (value == null || value == JSONObject.NULL) return null
        if (value is String) return value
        if (value is Number || value is Boolean) return value.toString()
        return null
    }
}
