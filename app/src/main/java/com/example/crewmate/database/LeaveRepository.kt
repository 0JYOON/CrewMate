package com.example.crewmate.database

import kotlinx.coroutines.flow.Flow

class LeaveRepository(private val leaveDao: LeaveDao) {
    fun getAnnualLeave(year: Int): Flow<AnnualLeaveEntity?> = leaveDao.getAnnualLeave(year)
    
    suspend fun insertAnnualLeave(year: Int, totalDays: Int) {
        leaveDao.insertAnnualLeave(AnnualLeaveEntity(year, totalDays))
    }

    fun getCarriedOverLeaves(): Flow<List<CarriedOverLeaveEntity>> = leaveDao.getCarriedOverLeaves()

    suspend fun insertCarriedOverLeave(days: Float, endDate: String) {
        leaveDao.insertCarriedOverLeave(CarriedOverLeaveEntity(days = days, endDate = endDate))
    }

    suspend fun updateCarriedOverLeave(leave: CarriedOverLeaveEntity, days: Float, endDate: String) {
        leaveDao.insertCarriedOverLeave(leave.copy(days = days, endDate = endDate))
    }

    suspend fun deleteCarriedOverLeave(leave: CarriedOverLeaveEntity) {
        leaveDao.deleteCarriedOverLeave(leave)
    }

    suspend fun cleanupExpired(currentYear: Int, currentDateStr: String) {
        leaveDao.deleteExpiredAnnualLeaves(currentYear)
        leaveDao.deleteExpiredCarriedOverLeaves(currentDateStr)
    }
}
