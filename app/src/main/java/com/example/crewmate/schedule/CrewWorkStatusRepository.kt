package com.example.crewmate.schedule

import android.util.Log
import com.example.crewmate.data.remote.xcrew.CrewAssignment
import com.example.crewmate.data.remote.xcrew.CrewWorkStatusDataSource
import com.example.crewmate.database.CrewAssignmentDao
import com.example.crewmate.database.CrewAssignmentEntity

sealed interface CrewFetchOutcome {
    data class Saved(val count: Int) : CrewFetchOutcome
    data class KeptCache(val count: Int, val reason: String) : CrewFetchOutcome
    data class Failed(val reason: String) : CrewFetchOutcome

    val isUsable: Boolean
        get() = this is Saved || (this is KeptCache && count > 0)
}

class CrewWorkStatusRepository(
    private val dataSource: CrewWorkStatusDataSource,
    private val crewAssignmentDao: CrewAssignmentDao,
    private val isOfflineModeProvider: () -> Boolean = { false }
) : CrewWorkStatusDataSource {

    override suspend fun fetchCrewWorkStatus(pjtDt: String): List<CrewAssignment> = getCrewWorkStatus(pjtDt)

    suspend fun getCachedOnly(date: String): List<CrewAssignment> {
        return crewAssignmentDao.getAssignmentsForDate(date).map { it.toDomain() }
    }

    suspend fun hasCache(date: String): Boolean {
        return crewAssignmentDao.countForDate(date) > 0
    }

    suspend fun refreshAndStore(date: String): CrewFetchOutcome {
        if (isOfflineModeProvider()) {
            val cachedCount = crewAssignmentDao.countForDate(date)
            return if (cachedCount > 0) {
                CrewFetchOutcome.KeptCache(cachedCount, "offline")
            } else {
                CrewFetchOutcome.Failed("offline_no_cache")
            }
        }

        return try {
            val assignments = dataSource.fetchCrewWorkStatus(date)
            if (assignments.isEmpty()) {
                val existingCount = crewAssignmentDao.countForDate(date)
                if (existingCount > 0) {
                    runCatching {
                        Log.w(TAG, "Empty server response for date=$date; keeping existing cache ($existingCount rows)")
                    }
                    return CrewFetchOutcome.KeptCache(existingCount, "empty_response")
                }
                return CrewFetchOutcome.Failed("empty_response")
            }

            val entities = assignments.map { it.toEntity(date) }
            crewAssignmentDao.replaceAssignmentsForDate(date, entities)
            CrewFetchOutcome.Saved(entities.size)
        } catch (e: Exception) {
            val reason = e::class.simpleName ?: "Exception"
            val existingCount = runCatching { crewAssignmentDao.countForDate(date) }.getOrDefault(0)
            if (existingCount > 0) {
                runCatching {
                    Log.w(TAG, "Fetch failed ($reason) for date=$date; keeping existing cache ($existingCount rows)")
                }
                CrewFetchOutcome.KeptCache(existingCount, reason)
            } else {
                runCatching {
                    Log.w(TAG, "Fetch failed ($reason) for date=$date with no cache")
                }
                CrewFetchOutcome.Failed(reason)
            }
        }
    }

    suspend fun getCrewWorkStatus(date: String): List<CrewAssignment> {
        if (isOfflineModeProvider()) {
            return getCachedOnly(date)
        }

        return try {
            val assignments = dataSource.fetchCrewWorkStatus(date)
            if (assignments.isNotEmpty()) {
                val entities = assignments.map { it.toEntity(date) }
                crewAssignmentDao.replaceAssignmentsForDate(date, entities)
                assignments
            } else {
                val cached = getCachedOnly(date)
                if (cached.isNotEmpty()) {
                    runCatching {
                        Log.w(TAG, "Empty server response for date=$date; returning cached ${cached.size} rows")
                    }
                    cached
                } else {
                    emptyList()
                }
            }
        } catch (e: Exception) {
            getCachedOnly(date)
        }
    }

    private fun CrewAssignment.toEntity(date: String) = CrewAssignmentEntity(
        date = date,
        pdiaNo = pdiaNo,
        repTrn1No = repTrn1No,
        repTrn2No = repTrn2No,
        gwkTm = gwkTm,
        loiwTm = loiwTm,
        crewMembers = crewMembers
    )

    private fun CrewAssignmentEntity.toDomain() = CrewAssignment(
        pdiaNo = pdiaNo,
        repTrn1No = repTrn1No,
        repTrn2No = repTrn2No,
        gwkTm = gwkTm,
        loiwTm = loiwTm,
        crewMembers = crewMembers
    )

    companion object {
        private const val TAG = "CrewWorkStatusRepo"
    }
}
