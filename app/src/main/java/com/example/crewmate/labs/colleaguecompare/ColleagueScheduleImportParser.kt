package com.example.crewmate.labs.colleaguecompare

import org.json.JSONArray
import org.json.JSONObject
import java.time.YearMonth

data class ParsedColleagueImport(
    val name: String,
    val ymKey: String, // yyyyMM
    val cells: Map<String, CompareDayCell> // yyyy-MM-dd -> CompareDayCell
)

/**
 * Gemini / ChatGPT 등 외부 AI 도구에 스크린샷과 함께 복붙할 프롬프트 및
 * 다중 인원 일괄 출력 파서.
 */
object ColleagueScheduleImportParser {
    const val AI_EXTRACTION_PROMPT: String = """첨부한 철도 승무근무표 스크린샷에서 각 승무원의 이름, 연월, 날짜별 다이아 번호와 출퇴근 시각을 아래 형식으로만 정확히 출력해 주세요. (여러 명이면 블록을 반복, 설명 문구 생략)

[이름: 홍길동 | 월: 2026-11]
01: 연차
02: 16035 | 09:53 | 21:05
03: 16315 | 18:51 | 10:07
04: ~(16315) | 18:51 | 10:07
11: S

규칙:
- 해당 월의 1일부터 말일까지만 포함하고, 달력 앞뒤 회색 다른 달 날짜는 제외
- 출퇴근 시각이 없으면 다이아/휴무명만 적기 (예: 11: S, 01: 연차)
- 승무원 이름이 안 보이면 홍길동 대신 동료1, 동료2로 표기"""

    private val HEADER_REGEX = Regex("""^\[?\s*이름\s*[:：]\s*([^|\]]+?)(?:\s*\|\s*월\s*[:：]\s*(\d{4}[-.]?\d{2}))?\s*\]?$""")
    private val DAY_LINE_REGEX = Regex("""^(\d{1,2})일?\s*[:：]\s*(.+)$""")

    fun parseBatchScheduleText(rawText: String, defaultMonth: YearMonth): List<ParsedColleagueImport> {
        val trimmed = rawText.trim()
        if (trimmed.isEmpty()) return emptyList()

        // JSON 형식 대응 (array 또는 object)
        if (trimmed.startsWith("[") || trimmed.startsWith("{")) {
            val jsonParsed = runCatching { parseJsonBatch(trimmed, defaultMonth) }.getOrNull()
            if (!jsonParsed.isNullOrEmpty()) return jsonParsed
        }

        val defaultYmDash = String.format("%04d-%02d", defaultMonth.year, defaultMonth.monthValue)
        val result = mutableListOf<ParsedColleagueImport>()
        var curName = "수동 추가"
        var curYmKey = String.format("%04d%02d", defaultMonth.year, defaultMonth.monthValue)
        var curCells = linkedMapOf<String, CompareDayCell>()

        fun flushCurrent() {
            if (curCells.isNotEmpty()) {
                result.add(ParsedColleagueImport(name = curName, ymKey = curYmKey, cells = curCells))
            }
        }

        trimmed.lines().forEach { line ->
            val t = line.trim()
            if (t.isEmpty() || t.startsWith("```")) return@forEach

            val hdr = HEADER_REGEX.matchEntire(t)
            if (hdr != null) {
                flushCurrent()
                curName = hdr.groupValues[1].trim().ifEmpty { "수동 추가" }
                val rawYm = hdr.groupValues[2].replace(".", "").replace("-", "").trim()
                curYmKey = if (rawYm.length == 6 && rawYm.all { it.isDigit() }) {
                    rawYm
                } else {
                    defaultYmDash.replace("-", "")
                }
                curCells = linkedMapOf()
                return@forEach
            }

            val dayMatch = DAY_LINE_REGEX.matchEntire(t)
            if (dayMatch != null) {
                val day = dayMatch.groupValues[1].toIntOrNull() ?: return@forEach
                if (day !in 1..31) return@forEach
                val parts = dayMatch.groupValues[2].split("|").map { it.trim() }
                val dia = parts.getOrNull(0).orEmpty().ifEmpty { "-" }
                val start = parts.getOrNull(1).orEmpty()
                val end = parts.getOrNull(2).orEmpty()
                val iso = "${curYmKey.substring(0, 4)}-${curYmKey.substring(4, 6)}-${String.format("%02d", day)}"
                curCells[iso] = CompareDayCell(
                    date = iso,
                    diaNo = dia,
                    startTime = start,
                    endTime = end,
                    isOverridden = false
                )
            }
        }
        flushCurrent()
        return result
    }

    private fun parseJsonBatch(raw: String, defaultMonth: YearMonth): List<ParsedColleagueImport> {
        val defaultYmKey = String.format("%04d%02d", defaultMonth.year, defaultMonth.monthValue)
        val arr = if (raw.startsWith("[")) JSONArray(raw) else JSONArray().put(JSONObject(raw))
        val out = mutableListOf<ParsedColleagueImport>()
        for (i in 0 until arr.length()) {
            val obj = arr.optJSONObject(i) ?: continue
            val name = obj.optString("name").trim().ifEmpty { "수동 추가" }
            val ymDigits = obj.optString("month").filter { it.isDigit() }
            val ymKey = if (ymDigits.length == 6) ymDigits else defaultYmKey
            val daysObj = obj.optJSONObject("days") ?: obj.optJSONObject("schedule") ?: continue
            val cells = linkedMapOf<String, CompareDayCell>()
            val keys = daysObj.keys()
            while (keys.hasNext()) {
                val k = keys.next()
                val day = k.filter { it.isDigit() }.toIntOrNull() ?: continue
                if (day !in 1..31) continue
                val iso = "${ymKey.substring(0, 4)}-${ymKey.substring(4, 6)}-${String.format("%02d", day)}"
                val v = daysObj.opt(k)
                if (v is JSONObject) {
                    cells[iso] = CompareDayCell(
                        date = iso,
                        diaNo = v.optString("dia").ifEmpty { v.optString("diaNo", "-") },
                        startTime = v.optString("start").ifEmpty { v.optString("startTime", "") },
                        endTime = v.optString("end").ifEmpty { v.optString("endTime", "") }
                    )
                } else {
                    val str = v?.toString()?.trim().orEmpty()
                    if (str.isNotEmpty()) {
                        cells[iso] = CompareDayCell(date = iso, diaNo = str)
                    }
                }
            }
            if (cells.isNotEmpty()) {
                out.add(ParsedColleagueImport(name = name, ymKey = ymKey, cells = cells))
            }
        }
        return out
    }
}
