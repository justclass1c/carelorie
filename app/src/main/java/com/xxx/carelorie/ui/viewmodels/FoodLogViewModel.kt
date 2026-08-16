package com.xxx.carelorie.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.xxx.carelorie.data.FoodRepository
import com.xxx.carelorie.data.remote.RemoteFoodLog
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate

data class FoodLogUiState(
    val selectedDate: LocalDate = LocalDate.now(),
    val logs: List<RemoteFoodLog> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    // Targets - these could later come from a GoalRepository
    val proteinTarget: Float = 120f,
    val carbsTarget: Float = 200f,
    val fatTarget: Float = 80f,
    val caloriesTarget: Int = 2000
) {
    val totalProtein: Float get() = logs.sumOf { it.protein.toDouble() }.toFloat()
    val totalCarbs: Float get() = logs.sumOf { it.carbs.toDouble() }.toFloat()
    val totalFat: Float get() = logs.sumOf { it.fat.toDouble() }.toFloat()
    val totalCalories: Int get() = logs.sumOf { it.calories }
}

sealed class FoodLogEvent {
    data class LoadLogs(val userId: Int, val date: LocalDate) : FoodLogEvent()
    data class ChangeDate(val userId: Int, val newDate: LocalDate) : FoodLogEvent()
}

class FoodLogViewModel(private val foodRepository: FoodRepository) : ViewModel() {

    private val _uiState = MutableStateFlow(FoodLogUiState())
    val uiState: StateFlow<FoodLogUiState> = _uiState.asStateFlow()

    fun onEvent(event: FoodLogEvent) {
        when (event) {
            is FoodLogEvent.LoadLogs -> {
                fetchLogs(event.userId, event.date)
            }
            is FoodLogEvent.ChangeDate -> {
                _uiState.update { it.copy(selectedDate = event.newDate) }
                fetchLogs(event.userId, event.newDate)
            }
        }
    }

    private fun fetchLogs(userId: Int, date: LocalDate) {
        _uiState.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            try {
                val logs = foodRepository.getDailyLogs(userId, date.toString())
                _uiState.update { it.copy(logs = logs, isLoading = false) }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = e.localizedMessage) }
            }
        }
    }
}
