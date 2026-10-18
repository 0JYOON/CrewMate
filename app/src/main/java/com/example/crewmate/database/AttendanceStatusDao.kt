package com.example.crewmate.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface AttendanceStatusDao {
    @Query("SELECT * FROM attendance_statuses WHERE pjtDt = :pjtDt LIMIT 1")
    suspend fun getAttendanceStatus(pjtDt: String): AttendanceStatusEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAttendanceStatus(entity: AttendanceStatusEntity)

    @Query("DELETE FROM attendance_statuses")
    suspend fun clearAll()
}
