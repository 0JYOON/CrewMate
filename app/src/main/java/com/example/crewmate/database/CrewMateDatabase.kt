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
        CrewAssignmentEntity::class
    ],
    version = 3,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class CrewMateDatabase : RoomDatabase() {
    abstract fun memoDao(): MemoDao
    abstract fun leaveDao(): LeaveDao
    abstract fun workScheduleDao(): WorkScheduleDao
    abstract fun crewAssignmentDao(): CrewAssignmentDao

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
