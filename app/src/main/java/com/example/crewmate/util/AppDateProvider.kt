package com.example.crewmate.util

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.LocalDate

object AppDateProvider {
    private val _overrideDate = MutableStateFlow<LocalDate?>(null)
    val overrideDate: StateFlow<LocalDate?> = _overrideDate.asStateFlow()

    val today: LocalDate
        get() = _overrideDate.value ?: LocalDate.now()

    fun setCustomDate(date: LocalDate) {
        _overrideDate.value = date
    }

    fun resetToRealToday() {
        _overrideDate.value = null
    }

    fun setOverrideDate(date: LocalDate?) {
        _overrideDate.value = date
    }
}
