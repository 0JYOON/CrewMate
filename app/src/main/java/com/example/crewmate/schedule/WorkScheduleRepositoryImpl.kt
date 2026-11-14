package com.example.crewmate.schedule

import com.example.crewmate.data.remote.xcrew.XcrewDataSource
import com.example.crewmate.database.WorkScheduleDao
import com.example.crewmate.database.WorkScheduleEntity
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class WorkScheduleRepositoryImpl(
    private val dataSource: XcrewDataSource,
    private val parser: WorkScheduleParser,
    private val workScheduleDao: WorkScheduleDao,
    private val isOfflineModeProvider: () -> Boolean = { false }
) : WorkScheduleRepository {

    private val refreshMutex = Mutex()

    private fun List<WorkScheduleEntity>.toDeduplicatedDomainSchedules(): List<WorkSchedule> {
        return map { entity ->
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
                alcSpecDvCd = entity.alcSpecDvCd,
                hldyYn = entity.hldyYn,
                isChanged = entity.isChanged
            )
        }.distinctBy {
            "${it.workDate}_${it.diaNo}_${it.startTime}_${it.endTime}_${it.trainNo}_${it.trainNo2}_${it.alcSpecDvCd}_${it.hldyYn}_${it.nonActiveType}"
        }
    }

    override suspend fun getCachedSchedulesOnly(month: String): WorkScheduleMonth {
        val cachedEntities = workScheduleDao.getSchedulesForMonth(month)
        val cachedSchedules = cachedEntities.toDeduplicatedDomainSchedules()
        return WorkScheduleMonth(
            month = month,
            schedules = cachedSchedules,
        )
    }

    override suspend fun getSchedules(month: String, forceRefresh: Boolean): WorkScheduleMonth {
        val cachedEntities = workScheduleDao.getSchedulesForMonth(month)
        val cachedSchedules = cachedEntities.toDeduplicatedDomainSchedules()

        // 1. 오프라인 모드이거나, 강제 갱신이 아니면서 이미 로컬 DB에 해당 월 근무표 캐시가 존재하면 즉시 반환 (Cache-First)
        if (isOfflineModeProvider() || (!forceRefresh && cachedSchedules.isNotEmpty())) {
            return WorkScheduleMonth(
                month = month,
                schedules = cachedSchedules,
            )
        }

        return refreshMutex.withLock {
            // Re-check cache inside lock in case another coroutine just completed refresh
            if (!forceRefresh) {
                val updatedCached = workScheduleDao.getSchedulesForMonth(month).toDeduplicatedDomainSchedules()
                if (updatedCached.isNotEmpty()) {
                    return@withLock WorkScheduleMonth(month = month, schedules = updatedCached)
                }
            }

            try {
                val response = dataSource.getWorkSchedules(month)
                val domainSchedules = parser.toDomain(response.data).distinctBy {
                    "${it.workDate}_${it.diaNo}_${it.startTime}_${it.endTime}_${it.trainNo}_${it.trainNo2}_${it.alcSpecDvCd}_${it.hldyYn}_${it.nonActiveType}"
                }
                
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
                        alcSpecDvCd = schedule.alcSpecDvCd,
                        hldyYn = schedule.hldyYn,
                        isChanged = schedule.isChanged
                    )
                }
                workScheduleDao.replaceSchedulesForMonth(month, entities) // Clean old records and atomic insert

                WorkScheduleMonth(
                    month = month,
                    schedules = domainSchedules,
                )
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException && e !is kotlinx.coroutines.TimeoutCancellationException) throw e
                
                // Fallback to local DB on network/server/session/timeout errors
                val fallbackEntities = workScheduleDao.getSchedulesForMonth(month)
                val fallbackSchedules = fallbackEntities.toDeduplicatedDomainSchedules()
                WorkScheduleMonth(
                    month = month,
                    schedules = fallbackSchedules,
                )
            }
        }
    }
}
