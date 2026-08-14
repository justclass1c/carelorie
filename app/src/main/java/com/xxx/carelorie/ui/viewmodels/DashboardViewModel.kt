package com.xxx.carelorie.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.xxx.carelorie.data.DailyMacroIntake
import com.xxx.carelorie.data.MacroDataRepository
import com.xxx.carelorie.data.UserRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate

data class DashboardUiState(
    val username: String = "",
    val weeklyIntake: List<DailyMacroIntake> = emptyList(),
    val isLoading: Boolean = false
) {
    val todayIntake: DailyMacroIntake?
        get() = weeklyIntake.find { it.date == LocalDate.now() }
}

sealed class DashboardEvent {
    data class LoadData(val userId: Int) : DashboardEvent()
}

class DashboardViewModel(
    private val userRepository: UserRepository,
    private val macroRepository: MacroDataRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(DashboardUiState())
    val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()

    fun onEvent(event: DashboardEvent) {
        when (event) {
            is DashboardEvent.LoadData -> loadDashboardData(event.userId)
        }
    }

    private fun loadDashboardData(userId: Int) {
        _uiState.update { it.copy(isLoading = true) }
        viewModelScope.launch {
            val profile = userRepository.getProfile(userId)
            val macroData = macroRepository.fetchWeeklyMacroIntake(userId)
            
            _uiState.update { 
                it.copy(
                    username = profile?.name ?: "User $userId",
                    weeklyIntake = macroData,
                    isLoading = false
                )
            }
        }
    }
}
