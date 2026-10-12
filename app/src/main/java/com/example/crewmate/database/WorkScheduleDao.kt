package com.example.crewmate.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface WorkScheduleDao {
    @Query("SELECT * FROM work_schedules WHERE yearMonth = :yearMonth ORDER BY date ASC")
    suspend fun getSchedulesForMonth(yearMonth: String): List<WorkScheduleEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSchedules(schedules: List<WorkScheduleEntity>)

    @Query("DELETE FROM work_schedules WHERE yearMonth = :yearMonth")
    suspend fun deleteSchedulesForMonth(yearMonth: String)

    @Query("DELETE FROM work_schedules WHERE yearMonth < :thresholdMonth")
    suspend fun cleanupOldSchedules(thresholdMonth: String)
}
