package com.example.crewmate.schedule

import com.example.crewmate.data.remote.xcrew.CrewAssignment
import com.example.crewmate.data.remote.xcrew.CrewWorkStatusDataSource
import com.example.crewmate.database.CrewAssignmentDao
import com.example.crewmate.database.CrewAssignmentEntity

class CrewWorkStatusRepository(
    private val dataSource: CrewWorkStatusDataSource,
    private val crewAssignmentDao: CrewAssignmentDao,
    private val isOfflineModeProvider: () -> Boolean = { false }
) {
    suspend fun getCrewWorkStatus(date: String): List<CrewAssignment> {
        if (isOfflineModeProvider()) {
            val cachedEntities = crewAssignmentDao.getAssignmentsForDate(date)
            return cachedEntities.map { entity ->
                CrewAssignment(
                    pdiaNo = entity.pdiaNo,
                    repTrn1No = entity.repTrn1No,
                    repTrn2No = entity.repTrn2No,
                    gwkTm = entity.gwkTm,
                    loiwTm = entity.loiwTm,
                    crewMembers = entity.crewMembers
                )
            }
        }

        return try {
            val assignments = dataSource.fetchCrewWorkStatus(date)
            
            // Cache to local DB
            val entities = assignments.map {
                CrewAssignmentEntity(
                    date = date,
                    pdiaNo = it.pdiaNo,
                    repTrn1No = it.repTrn1No,
                    repTrn2No = it.repTrn2No,
                    gwkTm = it.gwkTm,
                    loiwTm = it.loiwTm,
                    crewMembers = it.crewMembers
                )
            }
            crewAssignmentDao.deleteAssignmentsForDate(date)
            crewAssignmentDao.insertAssignments(entities)
            
            assignments
        } catch (e: Exception) {
            // Fallback to local DB
            val cachedEntities = crewAssignmentDao.getAssignmentsForDate(date)
            cachedEntities.map { entity ->
                CrewAssignment(
                    pdiaNo = entity.pdiaNo,
                    repTrn1No = entity.repTrn1No,
                    repTrn2No = entity.repTrn2No,
                    gwkTm = entity.gwkTm,
                    loiwTm = entity.loiwTm,
                    crewMembers = entity.crewMembers
                )
            }
        }
    }
}
