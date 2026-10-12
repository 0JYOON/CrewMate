package com.example.crewmate.schedule

interface WorkScheduleRepository {
    suspend fun getSchedules(month: String): WorkScheduleMonth
}

data class WorkScheduleMonth(
    val month: String,
    val schedules: List<WorkSchedule>,
)
