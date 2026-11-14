package com.example.crewmate.alarm

import java.time.LocalDateTime

/**
 * 출무 알람 사전확인(T-15초)과 본 알람(T) 간의 발동 조율기.
 *
 * 사전확인 서비스([AttendanceVerificationService])가 사이렌 시각(T)에도 아직 서버 조회 중이면,
 * [WorkAlarmReceiver]가 사이렌을 즉시 울리지 않고 본 조율기에 발동 정보를 위임(defer)합니다.
 * 서비스가 조회를 마치는 즉시(또는 최대 지연 시간 [MAX_DEFER_SECONDS] 도달 시):
 * - 출무 완료로 확인되면 사이렌을 울리지 않고 취소
 * - 미출무 또는 시간 초과이면 위임받은 사이렌을 즉시 발동합니다.
 */
object AttendancePrecheckCoordinator {
    const val PRECHECK_LEAD_SECONDS = 15L
    const val MAX_DEFER_SECONDS = 10L
    const val MAX_IN_FLIGHT_WINDOW_MILLIS = (PRECHECK_LEAD_SECONDS + MAX_DEFER_SECONDS) * 1_000L

    data class SirenPayload(
        val ruleTitle: String,
        val cat: String,
        val trainNo: String,
        val dptStn: String,
        val dptTimeStr: String,
        val pjtDt: String?,
        val soundMode: AlarmSoundMode
    )

    private data class InFlightEntry(
        val startedAtMs: Long,
        var deferredPayload: SirenPayload? = null
    )

    private val lock = Any()
    private val inFlight = mutableMapOf<String, InFlightEntry>()

    fun precheckTimeFor(trigger: LocalDateTime): LocalDateTime =
        trigger.minusSeconds(PRECHECK_LEAD_SECONDS)

    fun keyOf(pjtDt: String?, trainNo: String?, time: String?): String? =
        AttendanceCheckInLatch.keyOf(pjtDt, trainNo, time)

    fun markInFlight(key: String, nowMs: Long = System.currentTimeMillis()) {
        if (key.isBlank()) return
        synchronized(lock) {
            val existing = inFlight[key]
            inFlight[key] = InFlightEntry(
                startedAtMs = nowMs,
                deferredPayload = existing?.deferredPayload
            )
        }
    }

    fun isInFlight(key: String, nowMs: Long = System.currentTimeMillis()): Boolean {
        if (key.isBlank()) return false
        synchronized(lock) {
            val entry = inFlight[key] ?: return false
            if (nowMs - entry.startedAtMs > MAX_IN_FLIGHT_WINDOW_MILLIS) {
                inFlight.remove(key)
                return false
            }
            return true
        }
    }

    fun tryDefer(
        key: String,
        payload: SirenPayload,
        nowMs: Long = System.currentTimeMillis()
    ): Boolean {
        if (key.isBlank()) return false
        synchronized(lock) {
            val entry = inFlight[key] ?: return false
            if (nowMs - entry.startedAtMs > MAX_IN_FLIGHT_WINDOW_MILLIS) {
                inFlight.remove(key)
                return false
            }
            entry.deferredPayload = payload
            return true
        }
    }

    fun complete(key: String): SirenPayload? {
        if (key.isBlank()) return null
        synchronized(lock) {
            val removed = inFlight.remove(key)
            return removed?.deferredPayload
        }
    }

    fun clearForTesting() {
        synchronized(lock) {
            inFlight.clear()
        }
    }
}
