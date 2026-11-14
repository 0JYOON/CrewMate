package com.example.crewmate.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction

@Dao
interface CrewAssignmentDao {
    @Query("SELECT * FROM crew_assignments WHERE date = :date ORDER BY id ASC")
    suspend fun getAssignmentsForDate(date: String): List<CrewAssignmentEntity>

    @Query("SELECT COUNT(*) FROM crew_assignments WHERE date = :date")
    suspend fun countForDate(date: String): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAssignments(assignments: List<CrewAssignmentEntity>)

    @Query("DELETE FROM crew_assignments WHERE date = :date")
    suspend fun deleteAssignmentsForDate(date: String)

    @Transaction
    suspend fun replaceAssignmentsForDate(date: String, assignments: List<CrewAssignmentEntity>) {
        deleteAssignmentsForDate(date)
        insertAssignments(assignments)
    }

    @Query("DELETE FROM crew_assignments WHERE date < :thresholdDate")
    suspend fun cleanupOldAssignments(thresholdDate: String)
}
