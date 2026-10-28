package com.example.crewmate.database

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "bot_command_histories",
    indices = [
        Index(value = ["sentAt"]),
        Index(value = ["trainNo"])
    ]
)
data class BotCommandHistoryEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val sentAt: String,          // YYYY-MM-DD HH:mm:ss
    val trainNo: String,         // e.g. "K507"
    val targetStation: String,   // e.g. "수원"
    val interval: Int,           // e.g. 3
    val startTime: String,       // e.g. "14:15"
    val rawCommand: String,      // e.g. "K507 수원 3 14:15"
    val triggerType: String,     // "AUTO" or "MANUAL"
    val status: String,          // "SUCCESS", "FAILED", "CANCELLED"
    val responseMessage: String? = null
)
