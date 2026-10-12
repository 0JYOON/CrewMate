package com.example.crewmate.database

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "work_schedules",
    indices = [Index(value = ["date", "yearMonth"])]
)
data class WorkScheduleEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val yearMonth: String, // YYYYMM
    val date: String,      // YYYYMMDD
    val diaNo: String?,
    val trainNo: String?,
    val trainNo2: String?,
    val startTime: String?,
    val endTime: String?,
    val nonActiveType: String?,
    val isContinuation: Boolean,
    val isHoliday: Boolean,
    val isChanged: Boolean
)
