package com.example.crewmate.alarm

import android.content.Context
import android.util.Log
import com.example.crewmate.data.remote.xcrew.AttendanceStatusDto
import com.example.crewmate.data.remote.xcrew.AttendanceStatusParser
import com.example.crewmate.database.AttendanceStatusEntity
import com.example.crewmate.database.CrewMateDatabase
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDateTime

/**
 * 출무 현황 스냅샷 중앙 저장소
 * 
 * 출무 현황 JSON 직렬화/역직렬화 및 Room DB 저장을 일원화하며,
 * 저장 시 항상 [AttendanceCheckInLatch]를 동시에 갱신하여 데이터 무결성을 보장합니다.
 */
object AttendanceSnapshotStore {
    private const val TAG = "AttendanceSnapshotStore"

    data class Snapshot(
        val pjtDt: String,
        val statuses: List<AttendanceStatusDto>,
        val updatedAt: String?
    )

    /**
     * 출무현황 목록을 Room DB에 저장하고, 출무 완료된 건을 래치에 영구 기록
     */
    suspend fun save(context: Context, pjtDt: String, statuses: List<AttendanceStatusDto>) {
        if (pjtDt.isBlank() || statuses.isEmpty()) return
        val cleanDate = pjtDt.trim().replace("-", "").filter { it.isDigit() }
        if (cleanDate.length != 8) return

        try {
            // 1. 래치 기록
            AttendanceCheckInLatch.recordCheckedIn(context, statuses)

            // 2. Room DB 엔티티 저장
            val db = CrewMateDatabase.getInstance(context)
            val jsonArr = JSONArray()
            statuses.forEach { dto ->
                val obj = JSONObject().apply {
                    put("trnNo", dto.trnNo)
                    put("trnSttNm", dto.trnSttNm)
                    put("gtwcEpctBlgNm", dto.gtwcEpctBlgNm)
                    put("gtwcEpctTm", dto.gtwcEpctTm)
                    put("gtwcGubunCd", dto.gtwcGubunCd)
                    put("pjtDt", dto.pjtDt ?: cleanDate)
                    put("gtwcTpDvCd", dto.gtwcTpDvCd)
                    put("flg", dto.flg)
                    put("tnsmTm", dto.tnsmTm)
                    put("gtwcTm", dto.gtwcTm)
                    put("actlTm", dto.actlTm)
                }
                jsonArr.put(obj)
            }

            val entity = AttendanceStatusEntity(
                pjtDt = cleanDate,
                jsonString = jsonArr.toString(),
                updatedAt = LocalDateTime.now().toString()
            )
            db.attendanceStatusDao().insertAttendanceStatus(entity)
            Log.d(TAG, "Saved ${statuses.size} attendance statuses for date $cleanDate")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to save attendance snapshot for $cleanDate", e)
        }
    }

    /**
     * Room DB에서 특정 일자의 출무 스냅샷 로드
     */
    suspend fun load(context: Context, pjtDt: String): Snapshot? {
        val cleanDate = pjtDt.trim().replace("-", "").filter { it.isDigit() }
        if (cleanDate.length != 8) return null

        return try {
            val db = CrewMateDatabase.getInstance(context)
            val cached = db.attendanceStatusDao().getAttendanceStatus(cleanDate)
            if (cached != null && cached.jsonString.isNotBlank()) {
                val parser = AttendanceStatusParser()
                val list = parser.parse(cached.jsonString).map { dto ->
                    val cleanPjt = dto.pjtDt?.trim()?.replace("-", "")?.filter { it.isDigit() }
                    val normalized = if (!cleanPjt.isNullOrEmpty() && cleanPjt.length == 8) cleanPjt else cleanDate
                    dto.copy(pjtDt = normalized)
                }
                Snapshot(cleanDate, list, cached.updatedAt)
            } else {
                null
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to load attendance snapshot for $cleanDate", e)
            null
        }
    }
}
