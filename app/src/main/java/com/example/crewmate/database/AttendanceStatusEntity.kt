package com.example.crewmate.database

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "attendance_statuses",
    indices = [Index(value = ["pjtDt"])]
)
data class AttendanceStatusEntity(
    @PrimaryKey
    val pjtDt: String, // YYYYMMDD
    val jsonString: String,
    val updatedAt: String
)
