package com.example.crewmate.schedule

data class WorkSchedule(
    val workDate: String? = null,
    val diaNo: String? = null,
    val trainNo: String? = null,
    val trainNo2: String? = null,
    val startTime: String? = null,
    val endTime: String? = null,
    val nonActiveType: String? = null,
    val isContinuation: Boolean = false,
    val isHoliday: Boolean = false,
    val isChanged: Boolean = false,
)