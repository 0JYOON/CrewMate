package com.example.crewmate.alarm

import android.content.Context
import android.util.Log
import com.example.crewmate.database.AlarmHistoryEntity
import com.example.crewmate.database.CrewMateDatabase
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * 사이렌 알람 실행 및 알람 이력(Room DB) 기록을 일원화하는 디스패처.
 * [WorkAlarmReceiver]와 [AttendanceVerificationService](위임 발동) 양쪽에서 동일 경로로 호출됩니다.
 */
object AttendanceSirenDispatcher {
    private const val TAG = "AttSirenDispatcher"
    private const val PREFS_NAME = "crewmate_prefs"
    private const val KEY_LAST_SIREN_KEY = "last_attendance_siren_key"
    private const val KEY_LAST_SIREN_AT = "last_attendance_siren_at"
    private const val LATE_PRECHECK_SILENCE_WINDOW_MS = 60_000L

    suspend fun fire(
        context: Context,
        payload: AttendancePrecheckCoordinator.SirenPayload,
        auditNoteOverride: String? = null
    ) {
        val formattedTime = if (payload.dptTimeStr.length == 4 && payload.dptTimeStr.all(Char::isDigit)) {
            "${payload.dptTimeStr.substring(0, 2)}:${payload.dptTimeStr.substring(2, 4)}"
        } else {
            payload.dptTimeStr
        }

        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        if (payload.cat == "출무") {
            val key = AttendancePrecheckCoordinator.keyOf(payload.pjtDt, payload.trainNo, payload.dptTimeStr)
            if (!key.isNullOrBlank()) {
                prefs.edit()
                    .putString(KEY_LAST_SIREN_KEY, key)
                    .putLong(KEY_LAST_SIREN_AT, System.currentTimeMillis())
                    .apply()
            }
        }

        try {
            AlarmLoopService.start(
                context = context,
                ruleTitle = payload.ruleTitle,
                cat = payload.cat,
                trainNo = payload.trainNo,
                dptStn = payload.dptStn,
                formattedTime = formattedTime,
                soundMode = payload.soundMode
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start AlarmLoopService", e)
        }

        val firedAt = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
        val thresholdDateStr = LocalDateTime.now().minusDays(14).format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))

        try {
            val db = CrewMateDatabase.getInstance(context)
            val historyDao = db.alarmHistoryDao()

            val auditNote = when {
                !auditNoteOverride.isNullOrBlank() -> " · $auditNoteOverride"
                payload.cat == "출무" -> {
                    val precheckAudit = prefs.getString("attendance_precheck_audit_${payload.pjtDt}", null)
                    if (!precheckAudit.isNullOrBlank()) " · $precheckAudit"
                    else " · [판정: 캐시 확인]"
                }
                else -> ""
            }

            historyDao.insertHistory(
                AlarmHistoryEntity(
                    firedAt = firedAt,
                    title = payload.ruleTitle,
                    message = "[${payload.trainNo}] ${payload.dptStn} 출발 ($formattedTime)$auditNote",
                    trainNo = payload.trainNo,
                    dptStnNm = payload.dptStn
                )
            )
            historyDao.cleanupOldHistories(thresholdDateStr)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to insert alarm history", e)
        }
    }

    /**
     * 부정확 알람 등으로 인해 사이렌이 먼저 울린 직후(60초 이내) 뒤늦게 도착한 사전확인이
     * 출무 완료를 확인한 경우, 즉시 사이렌을 중지합니다.
     */
    fun stopRecentSirenIfMatches(
        context: Context,
        pjtDt: String?,
        trainNo: String?,
        dptTimeStr: String?,
        nowMs: Long = System.currentTimeMillis()
    ): Boolean {
        val targetKey = AttendancePrecheckCoordinator.keyOf(pjtDt, trainNo, dptTimeStr) ?: return false
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val lastKey = prefs.getString(KEY_LAST_SIREN_KEY, null) ?: return false
        val lastAt = prefs.getLong(KEY_LAST_SIREN_AT, 0L)
        if (lastKey == targetKey && nowMs - lastAt in 0..LATE_PRECHECK_SILENCE_WINDOW_MS) {
            runCatching {
                AlarmLoopService.stop(context)
                prefs.edit().remove(KEY_LAST_SIREN_KEY).remove(KEY_LAST_SIREN_AT).apply()
                Log.i(TAG, "Stopped active siren for $targetKey after late pre-check confirmed attendance")
            }
            return true
        }
        return false
    }
}
