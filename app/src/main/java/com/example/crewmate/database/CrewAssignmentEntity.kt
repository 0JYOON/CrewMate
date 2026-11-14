package com.example.crewmate.database

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "crew_assignments",
    indices = [Index(value = ["date"])]
)
data class CrewAssignmentEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val date: String, // YYYYMMDD
    val pdiaNo: String,
    val repTrn1No: String?,
    val repTrn2No: String?,
    val gwkTm: String?,
    val loiwTm: String?,
    val crewMembers: List<String>
)
