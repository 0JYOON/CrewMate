package com.example.crewmate.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface DiaCensusDao {
    @Query("SELECT * FROM dia_census ORDER BY pdiaNo ASC")
    fun getAllCensusFlow(): Flow<List<DiaCensusEntity>>

    @Query("SELECT * FROM dia_census ORDER BY pdiaNo ASC")
    suspend fun getAllCensus(): List<DiaCensusEntity>

    @Query("SELECT COUNT(*) FROM dia_census")
    suspend fun getCensusCount(): Int

    @Query("SELECT MAX(updatedAt) FROM dia_census")
    suspend fun getLatestUpdateDate(): String?

    @Query("SELECT * FROM dia_census WHERE pdiaNo = :pdiaNo LIMIT 1")
    suspend fun getCensusByPdiaNo(pdiaNo: String): DiaCensusEntity?

    @Query("SELECT * FROM dia_census WHERE trainNumbers LIKE '%' || :trainNo || '%'")
    suspend fun searchCensusByTrainNumber(trainNo: String): List<DiaCensusEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCensus(entity: DiaCensusEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCensusList(entities: List<DiaCensusEntity>)

    @Query("DELETE FROM dia_census")
    suspend fun clearAllCensus()
}
