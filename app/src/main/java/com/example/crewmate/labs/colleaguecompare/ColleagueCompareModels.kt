package com.example.crewmate.labs.colleaguecompare

enum class CompareEntryType {
    MY_SELF,
    REAL_COLLEAGUE,
    VIRTUAL_PERSON
}

/**
 * 특정 일자의 비교 셀 데이터 (기본은 diaNo, 옵션 활성화 시 출퇴근시각 포함).
 */
data class CompareDayCell(
    val date: String, // yyyy-MM-dd
    val diaNo: String,
    val startTime: String = "",
    val endTime: String = "",
    val isOverridden: Boolean = false
) {
    fun formattedCommuteTime(): String {
        val s = normalizeHHmm(startTime)
        val e = normalizeHHmm(endTime)
        return when {
            s.isNotEmpty() && e.isNotEmpty() -> "$s~$e"
            s.isNotEmpty() -> "${s}~"
            e.isNotEmpty() -> "~$e"
            else -> ""
        }
    }

    companion object {
        fun normalizeHHmm(raw: String): String {
            val digits = raw.filter { it.isDigit() }
            if (digits.length >= 4) {
                return "${digits.substring(0, 2)}:${digits.substring(2, 4)}"
            }
            return raw.trim()
        }
    }
}

/**
 * 비교 대상 인물(본인, Xcrew 실존 동료, 가상 인물).
 */
data class ComparePerson(
    val id: String,
    val name: String,
    val type: CompareEntryType,
    val cellsByMonth: Map<String, Map<String, CompareDayCell>> = emptyMap(), // yyyyMM -> (yyyy-MM-dd -> cell)
    val patternSequence: List<String> = emptyList(),
    val patternAnchorDate: String? = null, // yyyy-MM-dd
    val manualOverrides: Map<String, CompareDayCell> = emptyMap() // yyyy-MM-dd -> cell
) {
    fun cellForDate(yearMonthKey: String, dateIso: String): CompareDayCell? {
        manualOverrides[dateIso]?.let { return it }
        if (type == CompareEntryType.VIRTUAL_PERSON) {
            if (patternSequence.isEmpty()) return null
            val dia = PatternFileParser.resolveCyclicDia(
                targetDateIso = dateIso,
                anchorDateIso = patternAnchorDate ?: "${yearMonthKey.substring(0, 4)}-${yearMonthKey.substring(4, 6)}-01",
                sequence = patternSequence
            ) ?: return null
            return CompareDayCell(date = dateIso, diaNo = dia, isOverridden = false)
        }
        return cellsByMonth[yearMonthKey]?.get(dateIso)
    }
}

data class CompareDisplayOptions(
    val showCommuteTimes: Boolean = false,
    val transposeMatrix: Boolean = false
)
