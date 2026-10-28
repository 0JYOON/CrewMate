package com.example.crewmate.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface DiaDetailDao {
    @Query("SELECT * FROM dia_details WHERE diaKey = :diaKey")
    suspend fun getDiaDetail(diaKey: String): DiaDetailEntity?

    @Query("SELECT * FROM dia_details WHERE pjtDt = :pjtDt")
    suspend fun getDiaDetailsForDate(pjtDt: String): List<DiaDetailEntity>

    @Query("SELECT * FROM dia_details WHERE pdiaNo = :pdiaNo ORDER BY pjtDt DESC LIMIT 1")
    suspend fun getLatestDiaDetailByPdiaNo(pdiaNo: String): DiaDetailEntity?

    @Query("SELECT * FROM dia_details")
    suspend fun getAllDiaDetails(): List<DiaDetailEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDiaDetail(entity: DiaDetailEntity)

    @Query("DELETE FROM dia_details WHERE diaKey = :diaKey")
    suspend fun deleteDiaDetail(diaKey: String)

    @Query("DELETE FROM dia_details WHERE pjtDt < :thresholdDate")
    suspend fun cleanupOldDiaDetails(thresholdDate: String)
}
