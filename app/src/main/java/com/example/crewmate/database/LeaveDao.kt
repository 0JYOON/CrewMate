package com.example.crewmate.database

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface LeaveDao {
    @Query("SELECT * FROM annual_leaves WHERE year = :year")
    fun getAnnualLeave(year: Int): Flow<AnnualLeaveEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAnnualLeave(leave: AnnualLeaveEntity)

    @Query("SELECT * FROM annual_leaves ORDER BY year ASC")
    suspend fun getAllAnnualLeaves(): List<AnnualLeaveEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAnnualLeaves(leaves: List<AnnualLeaveEntity>)

    @Query("SELECT * FROM carried_over_leaves ORDER BY endDate ASC, id ASC")
    fun getCarriedOverLeaves(): Flow<List<CarriedOverLeaveEntity>>

    @Query("SELECT * FROM carried_over_leaves ORDER BY endDate ASC, id ASC")
    suspend fun getAllCarriedOverLeaves(): List<CarriedOverLeaveEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCarriedOverLeave(leave: CarriedOverLeaveEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCarriedOverLeaves(leaves: List<CarriedOverLeaveEntity>)

    @Delete
    suspend fun deleteCarriedOverLeave(leave: CarriedOverLeaveEntity)

    // Cleanup Queries
    @Query("DELETE FROM annual_leaves WHERE year < :currentYear")
    suspend fun deleteExpiredAnnualLeaves(currentYear: Int)

    @Query("DELETE FROM carried_over_leaves WHERE endDate < :currentDateStr")
    suspend fun deleteExpiredCarriedOverLeaves(currentDateStr: String)
}
