package com.example.crewmate.database

import kotlinx.coroutines.flow.Flow

class MemoRepository(private val memoDao: MemoDao) {
    fun getMemosForMonth(yearMonth: String): Flow<List<MemoEntity>> {
        return memoDao.getMemosForMonth(yearMonth)
    }

    fun getMemosForDate(date: String): Flow<List<MemoEntity>> {
        return memoDao.getMemosForDate(date)
    }

    suspend fun insertMemo(date: String, content: String) {
        if (content.isBlank()) return
        memoDao.insertMemo(MemoEntity(date = date, content = content.trim()))
    }

    suspend fun updateMemo(memo: MemoEntity, newContent: String) {
        if (newContent.isBlank()) return
        memoDao.updateMemo(memo.copy(content = newContent.trim()))
    }

    suspend fun deleteMemo(memo: MemoEntity) {
        memoDao.deleteMemo(memo)
    }
}
