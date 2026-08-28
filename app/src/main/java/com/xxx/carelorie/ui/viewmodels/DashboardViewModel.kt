package com.xxx.carelorie.ui.viewmodels

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.xxx.carelorie.data.DailyMacroIntake
import com.xxx.carelorie.data.FoodRepository
import com.xxx.carelorie.data.MacroDataRepository
import com.xxx.carelorie.data.UserRepository
import com.xxx.carelorie.data.remote.RemoteFoodLog
import com.xxx.carelorie.data.WeightRecord
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth

data class DashboardUiState(
    val username: String = "",
    val weeklyIntake: List<DailyMacroIntake> = emptyList(),
    val monthlyIntake: List<DailyMacroIntake> = emptyList(),
    val todayLogs: List<RemoteFoodLog> = emptyList(),
    val weightHistory: List<WeightRecord> = emptyList(),
    val currentStreak: Int = 0,
    val trackedDates: Set<LocalDate> = emptySet(),
    val isLoading: Boolean = false,
    val error: String? = null,
    /** One-shot text for a snackbar/toast, e.g. the result of a delete. */
    val message: String? = null
) {
    val todayIntake: DailyMacroIntake?
        get() = monthlyIntake.find { it.date == LocalDate.now() } ?: weeklyIntake.find { it.date == LocalDate.now() }
}

sealed class DashboardEvent {
    data class LoadData(val userId: Int) : DashboardEvent()
    data class UpdateWeight(val userId: Int, val weight: Float, val date: LocalDate) : DashboardEvent()
    data class ChangeMonth(val userId: Int, val yearMonth: YearMonth) : DashboardEvent()
    data class DeleteLog(val userId: Int, val log: RemoteFoodLog) : DashboardEvent()
    object MessageConsumed : DashboardEvent()
}

class DashboardViewModel(
    private val userRepository: UserRepository,
    private val macroRepository: MacroDataRepository,
    private val foodRepository: FoodRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(DashboardUiState())
    val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()

    fun onEvent(event: DashboardEvent) {
        when (event) {
            is DashboardEvent.LoadData -> loadDashboardData(event.userId)
            is DashboardEvent.UpdateWeight -> updateWeight(event.userId, event.weight, event.date)
            is DashboardEvent.ChangeMonth -> loadDashboardData(event.userId, event.yearMonth)
            is DashboardEvent.DeleteLog -> deleteLog(event.userId, event.log)
            is DashboardEvent.MessageConsumed -> _uiState.update { it.copy(message = null) }
        }
    }

    private fun loadDashboardData(userId: Int, yearMonth: YearMonth = YearMonth.now()) {
        Log.d("DashboardViewModel", "loadDashboardData started for userId: $userId, month: $yearMonth")
        _uiState.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            try {
                Log.d("DashboardViewModel", "Fetching profile...")
                val profile = userRepository.getProfile(userId)
                
                val today = LocalDate.now()
                // Fetch enough logs to cover the selected month AND the last 7 days for the dashboard
                val fetchStartDate = if (yearMonth.atDay(1).isBefore(today.minusDays(7))) 
                    yearMonth.atDay(1) 
                else 
                    today.minusDays(7)

                Log.d("DashboardViewModel", "Fetching logs from $fetchStartDate...")
                val allLogs = try {
                    foodRepository.getMonthlyLogs(userId, YearMonth.from(fetchStartDate)) 
                } catch (e: Exception) {
                    Log.e("DashboardViewModel", "Error fetching logs", e)
                    emptyList()
                }
                
                // Group logs by date once to avoid repeated filtering
                val logsByDate = allLogs.groupBy { it.createdAt.take(10) }
                
                val todayLogs = logsByDate[today.toString()] ?: emptyList()
                
                // Weekly Data (last 7 days)
                val weeklyData = (0..6).map { i ->
                    val date = today.minusDays(i.toLong())
                    val logsForDay = logsByDate[date.toString()] ?: emptyList()
                    
                    DailyMacroIntake(
                        date = date,
                        protein = logsForDay.sumOf { it.protein.toDouble() }.toFloat(),
                        carbs = logsForDay.sumOf { it.carbs.toDouble() }.toFloat(),
                        fat = logsForDay.sumOf { it.fat.toDouble() }.toFloat()
                    )
                }.sortedBy { it.date }

                // Monthly Data
                val daysInMonth = yearMonth.lengthOfMonth()
                val monthlyData = (1..daysInMonth).map { day ->
                    val date = yearMonth.atDay(day)
                    val logsForDay = logsByDate[date.toString()] ?: emptyList()
                    
                    DailyMacroIntake(
                        date = date,
                        protein = logsForDay.sumOf { it.protein.toDouble() }.toFloat(),
                        carbs = logsForDay.sumOf { it.carbs.toDouble() }.toFloat(),
                        fat = logsForDay.sumOf { it.fat.toDouble() }.toFloat()
                    )
                }

                val trackedDates = allLogs.mapNotNull { 
                    try { LocalDate.parse(it.createdAt.take(10)) } catch (e: Exception) { null }
                }.toSet()

                // Calculate current streak
                var streak = 0
                var checkDate = today
                while (trackedDates.contains(checkDate)) {
                    streak++
                    checkDate = checkDate.minusDays(1)
                }
                if (streak == 0) {
                    checkDate = today.minusDays(1)
                    while (trackedDates.contains(checkDate)) {
                        streak++
                        checkDate = checkDate.minusDays(1)
                    }
                }

                Log.d("DashboardViewModel", "Fetching weight history...")
                val weightHistory = userRepository.getWeightHistory(userId)

                _uiState.update { 
                    it.copy(
                        username = profile?.name ?: "User $userId",
                        weeklyIntake = weeklyData,
                        monthlyIntake = monthlyData,
                        todayLogs = todayLogs,
                        weightHistory = weightHistory,
                        currentStreak = streak,
                        trackedDates = trackedDates,
                        isLoading = false
                    )
                }
            } catch (e: Exception) {
                Log.e("DashboardViewModel", "Error in loadDashboardData", e)
                _uiState.update { 
                    it.copy(
                        isLoading = false,
                        error = "Failed to load dashboard: ${e.localizedMessage ?: "Unknown error"}"
                    )
                }
            }
        }
    }

    private fun deleteLog(userId: Int, log: RemoteFoodLog) {
        viewModelScope.launch {
            // Row disappears immediately; the repository removes it locally first and
            // reconciles with the server after.
            _uiState.update { state ->
                state.copy(todayLogs = state.todayLogs.filterNot { it.localId == log.localId })
            }
            val removed = foodRepository.deleteLog(log)
            _uiState.update {
                it.copy(
                    message = if (removed) {
                        "Removed ${log.foodName}"
                    } else {
                        "${log.foodName} will be removed once you're back online"
                    }
                )
            }
            loadDashboardData(userId)
        }
    }

    private fun updateWeight(userId: Int, weight: Float, date: LocalDate) {
        viewModelScope.launch {
            userRepository.saveWeight(userId, weight, date.toString())
            // Reload data to update graph
            loadDashboardData(userId)
        }
    }
}
