package com.example.crewmate.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface AlarmHistoryDao {
    @Query("SELECT * FROM alarm_histories ORDER BY firedAt DESC")
    fun getAllHistories(): Flow<List<AlarmHistoryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHistory(history: AlarmHistoryEntity)

    @Query("DELETE FROM alarm_histories")
    suspend fun clearAllHistories()

    @Query("DELETE FROM alarm_histories WHERE firedAt < :thresholdDateStr")
    suspend fun cleanupOldHistories(thresholdDateStr: String)
}
