package com.example.crewmate.schedule

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.example.crewmate.data.remote.xcrew.DiaDetailDataSource

@Composable
fun PersonalScheduleScreen(
    viewModel: PersonalScheduleViewModel,
    diaDetailDataSource: DiaDetailDataSource? = null,
) {
    val uiState by viewModel.uiState.collectAsState()
    val memosByDate by viewModel.memos.collectAsState()

    ScheduleContent(
        uiState = uiState,
        memosByDate = memosByDate,
        diaDetailDataSource = diaDetailDataSource,
        onPreviousMonth = viewModel::previousMonth,
        onNextMonth = viewModel::nextMonth,
        onRetry = viewModel::retrySelectedMonth,
        onAddMemo = viewModel::addMemo,
        onUpdateMemo = viewModel::updateMemo,
        onDeleteMemo = viewModel::deleteMemo,
        onMonthSelected = viewModel::setMonth
    )
}
