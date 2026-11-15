package com.example.crewmate.labs.colleaguecompare

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * 동료 근무 비교(실존 동료 캐시 + 가상 인물 패턴/오버라이드 + 표시 옵션) 로컬 영속 저장소.
 */
class ColleagueCompareStorage(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun loadPeople(): List<ComparePerson> {
        val raw = prefs.getString(KEY_PEOPLE_JSON, null) ?: return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            buildList(arr.length()) {
                for (i in 0 until arr.length()) {
                    val obj = arr.optJSONObject(i) ?: continue
                    val id = obj.optString("id")
                    val name = obj.optString("name")
                    val type = runCatching { CompareEntryType.valueOf(obj.optString("type")) }
                        .getOrNull() ?: continue
                    if (type == CompareEntryType.MY_SELF) continue

                    val cellsByMonth = linkedMapOf<String, Map<String, CompareDayCell>>()
                    val monthsObj = obj.optJSONObject("cellsByMonth")
                    if (monthsObj != null) {
                        monthsObj.keys().forEach { ym ->
                            val dayMap = linkedMapOf<String, CompareDayCell>()
                            val mObj = monthsObj.optJSONObject(ym)
                            if (mObj != null) {
                                mObj.keys().forEach { dt ->
                                    val cObj = mObj.optJSONObject(dt)
                                    if (cObj != null) {
                                        dayMap[dt] = CompareDayCell(
                                            date = dt,
                                            diaNo = cObj.optString("diaNo"),
                                            startTime = cObj.optString("startTime"),
                                            endTime = cObj.optString("endTime"),
                                            isOverridden = cObj.optBoolean("isOverridden", false)
                                        )
                                    }
                                }
                            }
                            cellsByMonth[ym] = dayMap
                        }
                    }

                    val seqArr = obj.optJSONArray("patternSequence")
                    val patternSequence = if (seqArr != null) {
                        (0 until seqArr.length()).mapNotNull { idx -> seqArr.optString(idx).takeIf { it.isNotBlank() } }
                    } else emptyList()

                    val overrides = linkedMapOf<String, CompareDayCell>()
                    val ovObj = obj.optJSONObject("manualOverrides")
                    if (ovObj != null) {
                        ovObj.keys().forEach { dt ->
                            val cObj = ovObj.optJSONObject(dt)
                            if (cObj != null) {
                                overrides[dt] = CompareDayCell(
                                    date = dt,
                                    diaNo = cObj.optString("diaNo"),
                                    startTime = cObj.optString("startTime"),
                                    endTime = cObj.optString("endTime"),
                                    isOverridden = true
                                )
                            }
                        }
                    }

                    add(
                        ComparePerson(
                            id = id,
                            name = name,
                            type = type,
                            cellsByMonth = cellsByMonth,
                            patternSequence = patternSequence,
                            patternAnchorDate = obj.optString("patternAnchorDate").takeIf { it.isNotBlank() },
                            manualOverrides = overrides
                        )
                    )
                }
            }
        }.getOrElse { emptyList() }
    }

    fun savePeople(people: List<ComparePerson>) {
        val arr = JSONArray()
        people.filter { it.type != CompareEntryType.MY_SELF }.forEach { person ->
            val obj = JSONObject().apply {
                put("id", person.id)
                put("name", person.name)
                put("type", person.type.name)
                put("patternAnchorDate", person.patternAnchorDate ?: "")
                put("patternSequence", JSONArray(person.patternSequence))

                val monthsObj = JSONObject()
                person.cellsByMonth.forEach { (ym, dayMap) ->
                    val mObj = JSONObject()
                    dayMap.forEach { (dt, cell) ->
                        mObj.put(dt, JSONObject().apply {
                            put("diaNo", cell.diaNo)
                            put("startTime", cell.startTime)
                            put("endTime", cell.endTime)
                            put("isOverridden", cell.isOverridden)
                        })
                    }
                    monthsObj.put(ym, mObj)
                }
                put("cellsByMonth", monthsObj)

                val ovObj = JSONObject()
                person.manualOverrides.forEach { (dt, cell) ->
                    ovObj.put(dt, JSONObject().apply {
                        put("diaNo", cell.diaNo)
                        put("startTime", cell.startTime)
                        put("endTime", cell.endTime)
                        put("isOverridden", true)
                    })
                }
                put("manualOverrides", ovObj)
            }
            arr.put(obj)
        }
        prefs.edit().putString(KEY_PEOPLE_JSON, arr.toString()).apply()
    }

    fun loadDisplayOptions(): CompareDisplayOptions {
        return CompareDisplayOptions(
            showCommuteTimes = prefs.getBoolean(KEY_SHOW_COMMUTE_TIMES, false),
            transposeMatrix = prefs.getBoolean(KEY_TRANSPOSE_MATRIX, false)
        )
    }

    fun saveDisplayOptions(options: CompareDisplayOptions) {
        prefs.edit()
            .putBoolean(KEY_SHOW_COMMUTE_TIMES, options.showCommuteTimes)
            .putBoolean(KEY_TRANSPOSE_MATRIX, options.transposeMatrix)
            .apply()
    }

    fun loadLastRefreshedAt(): String? = prefs.getString(KEY_LAST_REFRESHED_AT, null)

    fun saveLastRefreshedAt(timestamp: String) {
        prefs.edit().putString(KEY_LAST_REFRESHED_AT, timestamp).apply()
    }

    companion object {
        private const val PREFS_NAME = "crewmate_colleague_compare"
        private const val KEY_PEOPLE_JSON = "compare_people_json"
        private const val KEY_SHOW_COMMUTE_TIMES = "show_commute_times"
        private const val KEY_TRANSPOSE_MATRIX = "transpose_matrix"
        private const val KEY_LAST_REFRESHED_AT = "last_refreshed_at"
    }
}
