package com.example.crewmate.database

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "dia_details",
    indices = [Index(value = ["pjtDt", "pdiaNo"])]
)
data class DiaDetailEntity(
    @PrimaryKey
    val diaKey: String, // "$pjtDt|$pdiaNo"
    val pjtDt: String,
    val pdiaNo: String,
    val detailJson: String
)
