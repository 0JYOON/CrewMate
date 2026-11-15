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
    const val AI_EXTRACTION_PROMPT: String = """첨부한 철도 승무근무표(개인 달력형 또는 여러 명이 나열된 엑셀/표 형태 모두 포함) 스크린샷에서 각 승무원의 이름, 연월, 1일부터 말일까지의 날짜별 다이아 번호(및 출퇴근 시각이 있다면 함께)를 아래 형식으로만 출력해 주세요. (설명 문구·마크다운 표 생략)

[이름: 홍길동 | 월: 2026-11]
01: 연차
02: 16035 | 09:53 | 21:05
03: ~(16315) | 18:51 | 10:07
04: ~
05: S

규칙:
- 여러 명이 있는 엑셀/표 형태 스크린샷이면 모든 인원마다 [이름: ... | 월: ...] 블록을 빠짐없이 반복 출력
- 사진에서 연월(YYYY-MM)을 찾을 수 없으면 월: 미상 으로 표기 (앱/웹에서 선택한 월을 자동 적용함)
- 출퇴근 시각이 없는 표라면 시각 없이 01: 16037, 03: ~, 04: S 처럼 다이아/휴무명만 출력 (있으면 | 출근 | 퇴근 추가)
- 달력형일 경우 해당 월 1일~말일까지만 포함하고 앞뒤 회색 다른 달 날짜는 제외
- 왼쪽 열에 열 순서대로 1일, 2일... 이 이어지는 가로 표라면 열 순서에 맞춰 01: 부터 순서대로 출력"""

    private val NAME_FIELD_REGEX = Regex("""이름\s*[:：]\s*([^|\]]+)""")
    private val MONTH_FIELD_REGEX = Regex("""월\s*[:：]\s*([^|\]]+)""")
    private val DAY_LINE_REGEX = Regex("""^[-*]?\s*(\d{1,2})\s*일?\s*[:：.]\s*(.+)$""")

    fun parseBatchScheduleText(rawText: String, defaultMonth: YearMonth): List<ParsedColleagueImport> {
        val trimmed = rawText.trim()
        if (trimmed.isEmpty()) return emptyList()

        if (trimmed.startsWith("[") || trimmed.startsWith("{")) {
            val jsonParsed = runCatching { parseJsonBatch(trimmed, defaultMonth) }.getOrNull()
            if (!jsonParsed.isNullOrEmpty()) return jsonParsed
        }

        val defaultYmKey = String.format("%04d%02d", defaultMonth.year, defaultMonth.monthValue)
        val result = mutableListOf<ParsedColleagueImport>()
        var curName: String? = null
        var curYmKey = defaultYmKey
        var curCells = linkedMapOf<String, CompareDayCell>()

        fun flushCurrent() {
            if (curCells.isNotEmpty()) {
                result.add(
                    ParsedColleagueImport(
                        name = curName?.ifBlank { "수동 추가" } ?: "수동 추가",
                        ymKey = curYmKey,
                        cells = curCells
                    )
                )
            }
        }

        trimmed.lines().forEach { line ->
            val cleaned = line.trim()
                .removePrefix("```")
                .trim('*', '#', ' ')
            if (cleaned.isEmpty()) return@forEach

            val nameMatch = NAME_FIELD_REGEX.find(cleaned)
            if (nameMatch != null) {
                flushCurrent()
                curName = nameMatch.groupValues[1]
                    .trim('[', ']', ' ', '*', '`')
                    .ifEmpty { "수동 추가" }
                val monthRaw = MONTH_FIELD_REGEX.find(cleaned)?.groupValues?.get(1).orEmpty()
                curYmKey = resolveYmKey(monthRaw, defaultMonth)
                curCells = linkedMapOf()
                return@forEach
            }

            val dayMatch = DAY_LINE_REGEX.matchEntire(cleaned)
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

    private fun resolveYmKey(monthRaw: String, defaultMonth: YearMonth): String {
        val defaultYmKey = String.format("%04d%02d", defaultMonth.year, defaultMonth.monthValue)
        val digits = monthRaw.filter { it.isDigit() }
        return when {
            digits.length >= 6 -> {
                val y = digits.substring(0, 4).toIntOrNull() ?: defaultMonth.year
                val m = (digits.substring(4, 6).toIntOrNull() ?: defaultMonth.monthValue).coerceIn(1, 12)
                String.format("%04d%02d", y, m)
            }
            digits.length in 1..2 -> {
                val m = digits.toIntOrNull()?.takeIf { it in 1..12 } ?: return defaultYmKey
                String.format("%04d%02d", defaultMonth.year, m)
            }
            else -> defaultYmKey
        }
    }

    private fun parseJsonBatch(raw: String, defaultMonth: YearMonth): List<ParsedColleagueImport> {
        val defaultYmKey = String.format("%04d%02d", defaultMonth.year, defaultMonth.monthValue)
        val arr = if (raw.startsWith("[")) JSONArray(raw) else JSONArray().put(JSONObject(raw))
        val out = mutableListOf<ParsedColleagueImport>()
        for (i in 0 until arr.length()) {
            val obj = arr.optJSONObject(i) ?: continue
            val name = obj.optString("name").trim().ifEmpty { "수동 추가" }
            val ymKey = resolveYmKey(obj.optString("month"), defaultMonth).ifEmpty { defaultYmKey }
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
