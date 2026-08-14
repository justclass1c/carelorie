package com.xxx.carelorie.ui.viewmodels

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.xxx.carelorie.data.DailyMacroIntake
import com.xxx.carelorie.data.FoodRepository
import com.xxx.carelorie.data.MacroDataRepository
import com.xxx.carelorie.data.UserRepository
import com.xxx.carelorie.data.remote.RemoteFoodLog
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate

data class DashboardUiState(
    val username: String = "",
    val weeklyIntake: List<DailyMacroIntake> = emptyList(),
    val todayLogs: List<RemoteFoodLog> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null
) {
    val todayIntake: DailyMacroIntake?
        get() = weeklyIntake.find { it.date == LocalDate.now() }
}

sealed class DashboardEvent {
    data class LoadData(val userId: Int) : DashboardEvent()
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
        }
    }

    private fun loadDashboardData(userId: Int) {
        Log.d("DashboardViewModel", "loadDashboardData started for userId: $userId")
        _uiState.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            try {
                Log.d("DashboardViewModel", "Fetching profile...")
                val profile = userRepository.getProfile(userId)
                Log.d("DashboardViewModel", "Profile fetched: ${profile?.name}")
                
                Log.d("DashboardViewModel", "Fetching weekly logs...")
                val allLogs = try {
                    foodRepository.getWeeklyLogs(userId)
                } catch (e: Exception) {
                    Log.e("DashboardViewModel", "Error fetching weekly logs", e)
                    emptyList()
                }
                Log.d("DashboardViewModel", "Fetched ${allLogs.size} logs from Supabase")
                
                val today = LocalDate.now()
                val todayLogs = allLogs.filter { it.createdAt.startsWith(today.toString()) }
                
                val weeklyData = (0..6).map { i ->
                    val date = today.minusDays(i.toLong())
                    val logsForDay = allLogs.filter { it.createdAt.startsWith(date.toString()) }
                    
                    DailyMacroIntake(
                        date = date,
                        protein = logsForDay.sumOf { it.protein.toDouble() }.toFloat(),
                        carbs = logsForDay.sumOf { it.carbs.toDouble() }.toFloat(),
                        fat = logsForDay.sumOf { it.fat.toDouble() }.toFloat()
                    )
                }.sortedBy { it.date }

                Log.d("DashboardViewModel", "Weekly data processed successfully")

                _uiState.update { 
                    it.copy(
                        username = profile?.name ?: "User $userId",
                        weeklyIntake = weeklyData,
                        todayLogs = todayLogs,
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
}
