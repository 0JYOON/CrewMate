package com.example.crewmate.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

import androidx.room.TypeConverters

@Database(
    entities = [
        MemoEntity::class, 
        AnnualLeaveEntity::class, 
        CarriedOverLeaveEntity::class,
        WorkScheduleEntity::class,
        CrewAssignmentEntity::class,
        DiaDetailEntity::class,
        AlarmHistoryEntity::class,
        DiaCensusEntity::class,
        AttendanceStatusEntity::class
    ],
    version = 7,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class CrewMateDatabase : RoomDatabase() {
    abstract fun memoDao(): MemoDao
    abstract fun leaveDao(): LeaveDao
    abstract fun workScheduleDao(): WorkScheduleDao
    abstract fun crewAssignmentDao(): CrewAssignmentDao
    abstract fun diaDetailDao(): DiaDetailDao
    abstract fun alarmHistoryDao(): AlarmHistoryDao
    abstract fun diaCensusDao(): DiaCensusDao
    abstract fun attendanceStatusDao(): AttendanceStatusDao

    companion object {
        @Volatile
        private var INSTANCE: CrewMateDatabase? = null

        fun getInstance(context: Context): CrewMateDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    CrewMateDatabase::class.java,
                    "crewmate_database"
                ).fallbackToDestructiveMigration().build()
                INSTANCE = instance
                instance
            }
        }
    }
}
