package com.example.crewmate.labs.diarating

enum class DiaRatingGrade(
    val label: String,
    val shortBadge: String,
    val colorHex: Long
) {
    LIGHT("경사업", "경", 0xFF059669),
    NORMAL("보통", "중", 0xFF2563EB),
    HEAVY("중사업", "강", 0xFFDC2626),
    CUSTOM("선호", "★", 0xFF7C3AED),
    AVOID("기피", "⚠️", 0xFF991B1B)
}

data class DiaRatingRules(
    val lightMaxDriveMinutes: Int = 240,
    val heavyMinDriveMinutes: Int = 360,
    val heavyMinTotalMinutes: Int = 600,
    val preferredSleepStations: Set<String> = emptySet(),
    val heavySleepStations: Set<String> = emptySet(),
    val customOverrides: Map<String, DiaRatingGrade> = emptyMap(),
    val showCellTint: Boolean = true
)

data class DiaRatingResult(
    val grade: DiaRatingGrade,
    val reasons: List<String>,
    val sleepStation: String? = null,
    val driveMinutes: Int = 0,
    val totalMinutes: Int = 0,
    val isManualOverride: Boolean = false
)
