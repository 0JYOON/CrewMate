package com.example.crewmate.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface BotCommandDao {
    @Query("SELECT * FROM bot_command_histories ORDER BY id DESC")
    fun getAllHistories(): Flow<List<BotCommandHistoryEntity>>

    @Query("SELECT * FROM bot_command_histories ORDER BY id DESC LIMIT :limit")
    suspend fun getRecentHistories(limit: Int): List<BotCommandHistoryEntity>

    @Query("SELECT * FROM bot_command_histories WHERE trainNo = :trainNo ORDER BY id DESC LIMIT 1")
    suspend fun getLatestByTrainNo(trainNo: String): BotCommandHistoryEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHistory(history: BotCommandHistoryEntity): Long

    @Query("UPDATE bot_command_histories SET status = :status, responseMessage = :responseMessage WHERE id = :id")
    suspend fun updateStatus(id: Long, status: String, responseMessage: String?)

    @Query("UPDATE bot_command_histories SET status = 'CANCELLED' WHERE trainNo = :trainNo AND status = 'SUCCESS'")
    suspend fun markCancelledByTrainNo(trainNo: String)

    @Query("DELETE FROM bot_command_histories")
    suspend fun clearAllHistories()
}
