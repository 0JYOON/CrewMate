package com.example.crewmate.data.remote.xcrew

import android.util.Log
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import org.json.JSONTokener

data class CrewAssignment(
    val pdiaNo: String,
    val repTrn1No: String?,
    val repTrn2No: String?,
    val gwkTm: String?,
    val loiwTm: String?,
    val crewMembers: List<String>,
)

fun interface CrewWorkStatusDataSource {
    suspend fun fetchCrewWorkStatus(pjtDt: String): List<CrewAssignment>
}

class XcrewCrewWorkStatusParser {
    fun parse(json: String): List<CrewAssignment> {
        val root = try {
            JSONTokener(json).nextValue() as? JSONObject ?: throw UnexpectedXcrewResponseException()
        } catch (_: JSONException) {
            throw InvalidXcrewJsonException()
        }
        val data = root.optJSONArray("data") ?: throw UnexpectedXcrewResponseException()
        val assignments = buildList(data.length()) {
            for (index in 0 until data.length()) {
                val row = data.optJSONObject(index) ?: throw UnexpectedXcrewResponseException()
                val pdiaNo = row.optionalText("pdiaNo")?.trim().orEmpty()
                if (pdiaNo.isEmpty()) continue
                add(
                    CrewAssignment(
                        pdiaNo = pdiaNo,
                        repTrn1No = row.optionalText("repTrn1No")?.trim()?.ifEmpty { null },
                        repTrn2No = row.optionalText("repTrn2No")?.trim()?.ifEmpty { null },
                        gwkTm = row.optionalText("gwkTm")?.trim()?.ifEmpty { null },
                        loiwTm = row.optionalText("loiwTm")?.trim()?.ifEmpty { null },
                        crewMembers = normalizeCrewMembers(
                            row.optionalText("empNm1"),
                            row.optionalText("empNm2"),
                            row.optionalText("empNm3"),
                            row.optionalText("empNm4"),
                        ),
                    ),
                )
            }
        }
        Log.i(TAG, "assignmentCount=${assignments.size}")
        return assignments
    }

    private fun JSONObject.optionalText(key: String): String? = when (val value = opt(key)) {
        null, JSONObject.NULL -> null
        is String -> value
        is Number, is Boolean -> value.toString()
        else -> null
    }

    private companion object {
        const val TAG = "XcrewCrewStatusJson"
    }
}

/** Trims and filters Xcrew's four masked name fields without altering their masking. */
fun normalizeCrewMembers(vararg names: String?): List<String> = names
    .mapNotNull { it?.trim()?.takeIf(String::isNotEmpty) }
