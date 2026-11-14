package com.example.crewmate.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction

@Dao
interface WorkScheduleDao {
    @Query("SELECT * FROM work_schedules WHERE yearMonth = :yearMonth ORDER BY date ASC")
    suspend fun getSchedulesForMonth(yearMonth: String): List<WorkScheduleEntity>

    @Query("SELECT * FROM work_schedules WHERE yearMonth LIKE :yearPrefix ORDER BY date ASC")
    suspend fun getSchedulesForYear(yearPrefix: String): List<WorkScheduleEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSchedules(schedules: List<WorkScheduleEntity>)

    @Query("DELETE FROM work_schedules WHERE yearMonth = :yearMonth")
    suspend fun deleteSchedulesForMonth(yearMonth: String)

    @Transaction
    suspend fun replaceSchedulesForMonth(yearMonth: String, schedules: List<WorkScheduleEntity>) {
        deleteSchedulesForMonth(yearMonth)
        insertSchedules(schedules)
    }

    @Query("DELETE FROM work_schedules WHERE yearMonth < :thresholdMonth")
    suspend fun cleanupOldSchedules(thresholdMonth: String)
}
