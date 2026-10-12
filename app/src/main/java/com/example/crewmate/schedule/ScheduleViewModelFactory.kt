package com.example.crewmate.schedule

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.crewmate.data.remote.xcrew.XcrewWebViewSession
import com.example.crewmate.data.remote.xcrew.XcrewWebViewXhrDataSource
import com.example.crewmate.data.remote.xcrew.XcrewWorkScheduleParser
import com.example.crewmate.database.CrewMateDatabase
import com.example.crewmate.database.MemoRepository

class ScheduleViewModelFactory(
    private val xcrewSession: XcrewWebViewSession,
    private val context: Context,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (!modelClass.isAssignableFrom(ScheduleViewModel::class.java)) {
            throw IllegalArgumentException("Unsupported ViewModel type")
        }

        val dataSource = XcrewWebViewXhrDataSource(xcrewSession, XcrewWorkScheduleParser())
        val database = CrewMateDatabase.getInstance(context)
        val repository = WorkScheduleRepositoryImpl(dataSource, WorkScheduleParser(), database.workScheduleDao())
        val memoRepository = MemoRepository(database.memoDao())
        return ScheduleViewModel(repository, memoRepository) as T
    }
}
