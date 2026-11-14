package com.example.crewmate.schedule

interface WorkScheduleRepository {
    suspend fun getSchedules(month: String, forceRefresh: Boolean = false): WorkScheduleMonth
    suspend fun getCachedSchedulesOnly(month: String): WorkScheduleMonth = getSchedules(month, forceRefresh = false)
}

data class WorkScheduleMonth(
    val month: String,
    val schedules: List<WorkSchedule>,
)
