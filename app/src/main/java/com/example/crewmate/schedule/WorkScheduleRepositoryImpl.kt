package com.example.crewmate.schedule

import com.example.crewmate.data.remote.xcrew.XcrewDataSource

import com.example.crewmate.database.WorkScheduleDao
import com.example.crewmate.database.WorkScheduleEntity

class WorkScheduleRepositoryImpl(
    private val dataSource: XcrewDataSource,
    private val parser: WorkScheduleParser,
    private val workScheduleDao: WorkScheduleDao
) : WorkScheduleRepository {
    override suspend fun getSchedules(month: String): WorkScheduleMonth {
        return try {
            val response = dataSource.getWorkSchedules(month)
            val domainSchedules = parser.toDomain(response.data)
            
            // Cache to local DB
            val entities = domainSchedules.map { schedule ->
                WorkScheduleEntity(
                    yearMonth = month,
                    date = schedule.workDate ?: "",
                    diaNo = schedule.diaNo,
                    trainNo = schedule.trainNo,
                    trainNo2 = schedule.trainNo2,
                    startTime = schedule.startTime,
                    endTime = schedule.endTime,
                    nonActiveType = schedule.nonActiveType,
                    isContinuation = schedule.isContinuation,
                    isHoliday = schedule.isHoliday,
                    isChanged = schedule.isChanged
                )
            }
            workScheduleDao.deleteSchedulesForMonth(month) // Clean old records for this month
            workScheduleDao.insertSchedules(entities)

            WorkScheduleMonth(
                month = month,
                schedules = domainSchedules,
            )
        } catch (e: Exception) {
            // Fallback to local DB
            val cachedEntities = workScheduleDao.getSchedulesForMonth(month)
            val cachedSchedules = cachedEntities.map { entity ->
                WorkSchedule(
                    workDate = entity.date,
                    diaNo = entity.diaNo,
                    trainNo = entity.trainNo,
                    trainNo2 = entity.trainNo2,
                    startTime = entity.startTime,
                    endTime = entity.endTime,
                    nonActiveType = entity.nonActiveType,
                    isContinuation = entity.isContinuation,
                    isHoliday = entity.isHoliday,
                    isChanged = entity.isChanged
                )
            }
            WorkScheduleMonth(
                month = month,
                schedules = cachedSchedules,
            )
        }
    }
}
