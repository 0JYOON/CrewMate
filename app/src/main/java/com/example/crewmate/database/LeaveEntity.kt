package com.example.crewmate.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "annual_leaves")
data class AnnualLeaveEntity(
    @PrimaryKey
    val year: Int,
    val totalDays: Int
)

@Entity(tableName = "carried_over_leaves")
data class CarriedOverLeaveEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val days: Float,
    val endDate: String // YYYY-MM-DD
)
