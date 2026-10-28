package com.example.crewmate.database

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface MemoDao {
    @Query("SELECT * FROM memos WHERE date LIKE :yearMonth || '%' ORDER BY id ASC")
    fun getMemosForMonth(yearMonth: String): Flow<List<MemoEntity>>

    @Query("SELECT * FROM memos WHERE date = :date ORDER BY id ASC")
    fun getMemosForDate(date: String): Flow<List<MemoEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMemo(memo: MemoEntity)

    @androidx.room.Update
    suspend fun updateMemo(memo: MemoEntity)

    @Delete
    suspend fun deleteMemo(memo: MemoEntity)

    @Query("SELECT * FROM memos ORDER BY date ASC, id ASC")
    suspend fun getAllMemos(): List<MemoEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMemos(memos: List<MemoEntity>)
}
