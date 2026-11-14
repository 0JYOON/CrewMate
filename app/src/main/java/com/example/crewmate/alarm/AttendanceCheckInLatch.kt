package com.example.crewmate.alarm

import android.content.Context
import android.util.Log
import com.example.crewmate.data.remote.xcrew.AttendanceStatusDto
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * 출무 완료 래치 (Attendance Check-In Latch)
 * 
 * 한번 서버에서 출무 완료(실제 출무 시각 존재, 또는 상태코드 3, 5, 6)로 판정된 건을
 * 영구 기록하여, 이후 앱 재실행이나 백그라운드 동기화 시 구형 캐시나 네트워크 실패로 인해
 * 다시 '출무전'으로 덮어써지더라도 출무 전 알람이 되살아나거나 오발동하지 않도록 방어합니다.
 */
object AttendanceCheckInLatch {
    private const val TAG = "AttendanceCheckInLatch"
    private const val PREF_NAME = "crewmate_attendance_latch"
    private const val KEY_RECORDED_KEYS = "latched_keys"

    /**
     * 출무 식별 고유 키 생성
     * 포맷: "YYYYMMDD|CLEAN_TRAIN|HHMM"
     */
    fun keyOf(pjtDt: String?, trnNo: String?, epctTm: String?): String? {
        val cleanDate = pjtDt?.trim()?.replace("-", "")?.filter { it.isDigit() }.orEmpty()
        val cleanTrain = trnNo?.trim()?.replace("-", "").orEmpty()
        val cleanTime = epctTm?.trim()?.replace(":", "")?.filter { it.isDigit() }.orEmpty()

        if (cleanDate.length != 8 || cleanTime.length < 4) return null
        val time4 = cleanTime.take(4)
        return "$cleanDate|$cleanTrain|$time4"
    }

    /**
     * DTO에서 키 추출
     */
    fun keyOf(dto: AttendanceStatusDto): String? {
        return keyOf(dto.pjtDt, dto.trnNo, dto.gtwcEpctTm)
    }

    /**
     * 서버/캐시로부터 수신된 출무현황 목록 중 출무가 완료된(isCheckedIn) 항목들을 래치에 영구 기록
     */
    fun recordCheckedIn(context: Context, statuses: List<AttendanceStatusDto>) {
        if (statuses.isEmpty()) return
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val existing = prefs.getStringSet(KEY_RECORDED_KEYS, emptySet())?.toMutableSet() ?: mutableSetOf()
        var addedCount = 0

        statuses.forEach { dto ->
            if (dto.isCheckedIn) {
                val key = keyOf(dto)
                if (key != null && existing.add(key)) {
                    addedCount++
                }
            }
        }

        if (addedCount > 0) {
            cleanOldKeys(existing)
            prefs.edit().putStringSet(KEY_RECORDED_KEYS, existing).apply()
            Log.i(TAG, "Recorded $addedCount new check-in latch keys. Total active: ${existing.size}")
        }
    }

    /**
     * 특정 날짜/열차/시각의 출무가 이미 완료되었는지 래치 확인
     */
    fun isCheckedIn(context: Context, pjtDt: String?, trnNo: String?, epctTm: String?): Boolean {
        val cleanDate = pjtDt?.trim()?.replace("-", "")?.filter { it.isDigit() }.orEmpty()
        val cleanTrain = trnNo?.trim()?.replace("-", "").orEmpty()
        val cleanTime = epctTm?.trim()?.replace(":", "")?.filter { it.isDigit() }.orEmpty().take(4)

        if (cleanDate.length != 8) return false

        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val keys = prefs.getStringSet(KEY_RECORDED_KEYS, emptySet()).orEmpty()
        if (keys.isEmpty()) return false

        // 1) 정확한 일치
        if (cleanTime.isNotEmpty()) {
            val exactKey = "$cleanDate|$cleanTrain|$cleanTime"
            if (keys.contains(exactKey)) return true
        }

        // 2) 열차번호 및 시각 유연한 매칭 (열차번호 접미사 일치 등)
        for (k in keys) {
            val parts = k.split("|")
            if (parts.size == 3) {
                val kDate = parts[0]
                val kTrain = parts[1]
                val kTime = parts[2]

                if (kDate == cleanDate) {
                    val matchesTrain = cleanTrain.isNotEmpty() && (kTrain == cleanTrain || kTrain.endsWith(cleanTrain) || cleanTrain.endsWith(kTrain))
                    val matchesTime = cleanTime.isNotEmpty() && kTime == cleanTime

                    if (matchesTrain && matchesTime) return true
                    if (matchesTrain && cleanTime.isEmpty()) return true
                    if (matchesTime && cleanTrain.isEmpty()) return true
                }
            }
        }

        return false
    }

    /**
     * 현재 래치된 모든 키 반환 (RuleEvaluator 등에서 필터링용)
     */
    fun allKeys(context: Context): Set<String> {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        return prefs.getStringSet(KEY_RECORDED_KEYS, emptySet())?.toSet() ?: emptySet()
    }

    /**
     * 3일 이상 지난 구형 키 자동 정리
     */
    private fun cleanOldKeys(keys: MutableSet<String>) {
        val thresholdDate = try {
            LocalDate.now().minusDays(3).format(DateTimeFormatter.BASIC_ISO_DATE)
        } catch (_: Exception) {
            return
        }

        keys.retainAll { key ->
            val datePart = key.substringBefore("|")
            datePart >= thresholdDate
        }
    }
}
