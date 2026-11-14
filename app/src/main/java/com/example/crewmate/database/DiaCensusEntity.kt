package com.example.crewmate.database

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "dia_census",
    indices = [Index(value = ["pdiaNo"])]
)
data class DiaCensusEntity(
    @PrimaryKey
    val pdiaNo: String,       // e.g. "16001" (Sole Primary Key)
    val trainNumbers: String, // Comma-separated train numbers e.g. "K4501,K4512"
    val detailJson: String,   // Full DiaDetailResponse JSON
    val updatedAt: String     // YYYY-MM-DD HH:mm:ss
)
