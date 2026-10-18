package com.example.crewmate.database

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "alarm_histories",
    indices = [Index(value = ["firedAt"])]
)
data class AlarmHistoryEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val firedAt: String, // YYYY-MM-DD HH:mm:ss
    val title: String,
    val message: String,
    val trainNo: String,
    val dptStnNm: String
)
