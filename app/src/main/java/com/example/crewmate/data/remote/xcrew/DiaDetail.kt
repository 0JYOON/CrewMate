package com.example.crewmate.data.remote.xcrew

import android.util.Log
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import org.json.JSONTokener

private val DIA_RUN_DT_DV_MAP = linkedMapOf(
    "diaRunDtDvCd00" to "전일",
    "diaRunDtDvCd01" to "월",
    "diaRunDtDvCd02" to "화",
    "diaRunDtDvCd03" to "수",
    "diaRunDtDvCd04" to "목",
    "diaRunDtDvCd05" to "금",
    "diaRunDtDvCd06" to "토",
    "diaRunDtDvCd07" to "일",
    "diaRunDtDvCd10" to "평휴",
    "diaRunDtDvCd11" to "휴휴",
    "diaRunDtDvCd12" to "휴평",
    "diaRunDtDvCd14" to "토휴",
    "diaRunDtDvCd15" to "휴토",
    "diaRunDtDvCd16" to "평평",
    "diaRunDtDvCd17" to "평토"
)

data class DiaDetailResponse(
    val effectiveTerm: DiaEffectiveTerm?,
    /** Preserved as raw JSON because the supplied Xcrew schema does not define these fields. */
    val runDtDv: Any?,
    val segments: List<DiaDetailSegment>,
    val summaries: List<DiaDetailSummary>,
    val crewMeta: DiaCrewMeta?,
    val rawCrewMeta: JSONObject? = null,
    val rawEfcTrm: JSONObject? = null,
    val rawRootJson: JSONObject? = null
) {
    fun resolveSecondTrainNo(primaryTrainNo: String?): String? {
        val normalizedPrimary = primaryTrainNo?.trim()?.takeIf(String::isNotEmpty)
        crewMeta?.repTrn2No?.trim()?.takeIf { it.isNotEmpty() && it != normalizedPrimary }?.let {
            return it
        }

        val drivingTrains = segments
            .filter { it.pjtHrDvNm?.trim() == "운전" || it.pjtHrDvNm?.trim() == "승무" }
            .mapNotNull { it.trnNo?.trim()?.takeIf(String::isNotEmpty) }
        if (drivingTrains.size >= 2) {
            if (normalizedPrimary != null) {
                val primaryIndex = drivingTrains.indexOf(normalizedPrimary)
                if (primaryIndex >= 0 && primaryIndex + 1 < drivingTrains.size) {
                    return drivingTrains[primaryIndex + 1]
                }
                drivingTrains.firstOrNull { it != normalizedPrimary }?.let { return it }
            }
            return drivingTrains[1]
        }

        val allTrains = segments.mapNotNull { it.trnNo?.trim()?.takeIf(String::isNotEmpty) }
        if (allTrains.size >= 2) {
            if (normalizedPrimary != null) {
                val primaryIndex = allTrains.indexOf(normalizedPrimary)
                if (primaryIndex >= 0 && primaryIndex + 1 < allTrains.size) {
                    return allTrains[primaryIndex + 1]
                }
                allTrains.firstOrNull { it != normalizedPrimary }?.let { return it }
            }
            return allTrains[1]
        }
        return null
    }

    fun extractRunDays(): String {
        val matchedDays = mutableSetOf<String>()

        val dayLabels = listOf(
            "전일", "월", "화", "수", "목", "금", "토", "일",
            "평휴", "휴휴", "휴평", "토휴", "휴토", "평평", "평토"
        )
        val knownDaySet = dayLabels.toSet()

        fun isValueTrue(v: Any?): Boolean {
            val s = v?.toString()?.trim()?.uppercase() ?: return false
            return s == "Y" || s == "1" || s == "TRUE" || s == "ON"
        }

        fun scanObject(obj: JSONObject) {
            // 1. Official Xcrew column keys (diaRunDtDvCd00 .. diaRunDtDvCd17)
            var hasOfficialKeys = false
            DIA_RUN_DT_DV_MAP.forEach { (key, title) ->
                if (obj.has(key)) {
                    hasOfficialKeys = true
                    val v = obj.opt(key)
                    val strVal = v?.toString()?.trim() ?: ""
                    if (v == 1 || v == "1" || strVal == "1") {
                        matchedDays.add(title)
                    }
                }
            }

            // Xcrew 공식 키를 감지한 경우 부작용이 있는 숫자기반 추정 로직은 수행하지 않고 종료
            if (hasOfficialKeys) return

            // 2. Numbered keys (e.g. runWkdy1..15, wkdy1..15)
            obj.keys().forEach { key ->
                val value = obj.opt(key)
                if (isValueTrue(value)) {
                    val digits = key.takeLastWhile { it.isDigit() }.toIntOrNull()
                    if (digits != null) {
                        if (digits in 1..15) {
                            matchedDays.add(dayLabels[digits - 1])
                        } else if (digits in 0..14) {
                            matchedDays.add(dayLabels[digits])
                        }
                    }
                    if (key in knownDaySet) {
                        matchedDays.add(key)
                    }
                }

                // If value is a string containing day names (e.g. "평휴, 평평, 평토")
                val strVal = value?.toString()?.trim()
                if (strVal != null && strVal.length in 1..50 && !strVal.startsWith("{") && !strVal.startsWith("[")) {
                    strVal.split(",", "/", " ").map { it.trim() }.forEach { token ->
                        if (token in knownDaySet) {
                            matchedDays.add(token)
                        }
                    }
                }
            }

            // 3. Explicit name property (e.g., {"runWkdyNm": "평휴, 토휴, 휴휴"})
            val explicitName = listOf("runWkdyNm", "wkdyNm", "cdNm", "codeNm", "name", "nm")
                .mapNotNull { obj.optString(it).takeIf(String::isNotBlank) }
                .firstOrNull()

            if (explicitName != null) {
                val isUnchecked = obj.keys().asSequence().any { k ->
                    val v = obj.optString(k).trim().uppercase()
                    v == "N" || v == "0" || v == "FALSE"
                }
                if (!isUnchecked) {
                    explicitName.split(",", "/", " ").map { it.trim() }.filter { it.isNotEmpty() }.forEach {
                        if (it in knownDaySet || it.length in 1..4) matchedDays.add(it)
                    }
                }
            }
        }

        fun scanElement(elem: Any?) {
            when (elem) {
                is JSONArray -> {
                    for (i in 0 until elem.length()) scanElement(elem.opt(i))
                }
                is JSONObject -> {
                    scanObject(elem)
                    elem.keys().forEach { k ->
                        val sub = elem.opt(k)
                        if (sub is JSONObject || sub is JSONArray) scanElement(sub)
                    }
                }
                is String -> {
                    elem.split(",", "/", " ").map { it.trim() }.forEach {
                        if (it in knownDaySet) matchedDays.add(it)
                    }
                }
            }
        }

        scanElement(runDtDv)
        scanElement(rawEfcTrm)
        scanElement(rawCrewMeta)
        if (matchedDays.isEmpty()) {
            scanElement(rawRootJson)
        }

        return if (matchedDays.isNotEmpty()) {
            dayLabels.filter { it in matchedDays }.joinToString(", ")
        } else "-"
    }

    fun extractEffectiveTermStr(): String {
        val stDt = effectiveTerm?.pdiaEfcStDt?.trim().orEmpty()
        val clsDt = effectiveTerm?.pdiaEfcClsDt?.trim().orEmpty()
        fun format(dt: String): String = if (dt.length == 8) "${dt.substring(0,4)}.${dt.substring(4,6)}.${dt.substring(6,8)}" else dt
        val start = format(stDt)
        val end = format(clsDt)
        return if (start.isNotEmpty() || end.isNotEmpty()) "$start ~ $end" else "-"
    }
}

data class DiaEffectiveTerm(
    val pdiaEfcStDt: String?,
    val pdiaEfcClsDt: String?,
    val pdiaDvCd: String?,
    val pdiaNo: String?,
)

data class DiaDetailSegment(
    val num: Int?,
    val pdiaEfcStDt: String?,
    val pdiaEfcClsDt: String?,
    val trnNo: String?,
    val dptStnNm: String?,
    val arvStnNm: String?,
    val dptTm: String?,
    val arvTm: String?,
    val pjtHrDvNm: String?,
    val pjtTnum: Int?,
    val pjtDst: String?,
)

data class DiaDetailSummary(
    val repPjtHrDvCd: String?,
    val pjtHrDvNm: String?,
    val pjtTnum: Int?,
    val pjtDst: Double?,
    val totWrkTnum: Int?,
    val totPjtDst: Double?,
)

data class DiaCrewMeta(
    val pjtDt: String?,
    val pdiaNo: String?,
    val pdiaEfcStDt: String?,
    val pdiaDvCd: String?,
    val repTrn1No: String? = null,
    val repTrn2No: String? = null,
)

fun interface DiaDetailDataSource {
    suspend fun fetchDiaDetail(pjtDt: String, pdiaNo: String): DiaDetailResponse
}

class DiaDetailParser {
    fun parse(json: String): DiaDetailResponse {
        val root = try {
            JSONTokener(json).nextValue() as? JSONObject ?: throw UnexpectedXcrewResponseException()
        } catch (_: JSONException) {
            throw InvalidXcrewJsonException()
        }
        val segments = root.optJSONArray("data").requiredObjects().map { it.toSegment() }
        val summaries = root.optJSONArray("dtlData").requiredObjects().map { it.toSummary() }
        Log.i(TAG, "topLevelKeys=${root.keys().asSequence().toList().sorted()}")
        Log.i(TAG, "segmentCount=${segments.size} summaryCount=${summaries.size}")
        val crewMetaJson = root.optJSONObject("extrCrewMgVO")
        val efcTrmJson = root.optJSONObject("efcTrm")
        return DiaDetailResponse(
            effectiveTerm = efcTrmJson?.toEffectiveTerm(),
            runDtDv = root.opt("runDtDv"),
            segments = segments,
            summaries = summaries,
            crewMeta = crewMetaJson?.toCrewMeta(),
            rawCrewMeta = crewMetaJson,
            rawEfcTrm = efcTrmJson,
            rawRootJson = root
        )
    }

    private fun JSONArray?.requiredObjects(): List<JSONObject> {
        val array = this ?: throw UnexpectedXcrewResponseException()
        return buildList(array.length()) {
            for (index in 0 until array.length()) {
                add(array.optJSONObject(index) ?: throw UnexpectedXcrewResponseException())
            }
        }
    }

    private fun JSONObject.toEffectiveTerm() = DiaEffectiveTerm(
        pdiaEfcStDt = optionalText("pdiaEfcStDt"),
        pdiaEfcClsDt = optionalText("pdiaEfcClsDt"),
        pdiaDvCd = optionalText("pdiaDvCd"),
        pdiaNo = optionalText("pdiaNo"),
    )

    private fun JSONObject.toSegment() = DiaDetailSegment(
        num = optIntOrNull("num"),
        pdiaEfcStDt = optionalText("pdiaEfcStDt"),
        pdiaEfcClsDt = optionalText("pdiaEfcClsDt"),
        trnNo = optionalText("trnNo"),
        dptStnNm = optionalText("dptStnNm"),
        arvStnNm = optionalText("arvStnNm"),
        dptTm = optionalText("dptTm"),
        arvTm = optionalText("arvTm"),
        pjtHrDvNm = optionalText("pjtHrDvNm"),
        pjtTnum = optIntOrNull("pjtTnum"),
        pjtDst = optionalText("pjtDst"),
    )

    private fun JSONObject.toSummary() = DiaDetailSummary(
        repPjtHrDvCd = optionalText("repPjtHrDvCd"),
        pjtHrDvNm = optionalText("pjtHrDvNm"),
        pjtTnum = optIntOrNull("pjtTnum"),
        pjtDst = optDoubleOrNull("pjtDst"),
        totWrkTnum = optIntOrNull("totWrkTnum"),
        totPjtDst = optDoubleOrNull("totPjtDst"),
    )

    private fun JSONObject.toCrewMeta() = DiaCrewMeta(
        pjtDt = optionalText("pjtDt"),
        pdiaNo = optionalText("pdiaNo"),
        pdiaEfcStDt = optionalText("pdiaEfcStDt"),
        pdiaDvCd = optionalText("pdiaDvCd"),
        repTrn1No = optionalText("repTrn1No"),
        repTrn2No = optionalText("repTrn2No"),
    )

    private fun JSONObject.optionalText(name: String): String? = when (val value = opt(name)) {
        null, JSONObject.NULL -> null
        is String -> value
        is Number, is Boolean -> value.toString()
        else -> null
    }

    private fun JSONObject.optIntOrNull(name: String): Int? = when (val value = opt(name)) {
        is Number -> value.toInt()
        is String -> value.toIntOrNull()
        else -> null
    }

    private fun JSONObject.optDoubleOrNull(name: String): Double? = when (val value = opt(name)) {
        is Number -> value.toDouble()
        is String -> value.toDoubleOrNull()
        else -> null
    }

    private companion object {
        const val TAG = "XcrewDiaJson"
    }
}