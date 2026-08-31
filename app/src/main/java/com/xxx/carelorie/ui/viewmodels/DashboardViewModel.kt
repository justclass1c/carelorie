package com.xxx.carelorie.ui.viewmodels

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.xxx.carelorie.data.DailyMacroIntake
import com.xxx.carelorie.data.FoodRepository
import com.xxx.carelorie.data.MacroDataRepository
import com.xxx.carelorie.data.NutritionTargets
import com.xxx.carelorie.data.UserRepository
import com.xxx.carelorie.data.remote.DeepSeekService
import com.xxx.carelorie.data.remote.HealthContext
import com.xxx.carelorie.data.remote.GoalInsightContext
import com.xxx.carelorie.data.remote.RemoteFoodLog
import com.xxx.carelorie.data.WeightRecord
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter

data class DashboardUiState(
    val username: String = "",
    val weeklyIntake: List<DailyMacroIntake> = emptyList(),
    val monthlyIntake: List<DailyMacroIntake> = emptyList(),
    val todayLogs: List<RemoteFoodLog> = emptyList(),
    val weightHistory: List<WeightRecord> = emptyList(),
    val currentStreak: Int = 0,
    val trackedDates: Set<LocalDate> = emptySet(),
    val targets: NutritionTargets = NutritionTargets.DEFAULT,
    val weightAdvice: String? = null,
    val goalInsight: String? = null,
    val isGoalInsightLoading: Boolean = false,
    val isLoading: Boolean = true,
    val error: String? = null,
    /** One-shot text for a snackbar/toast, e.g. the result of a delete. */
    val message: String? = null
) {
    val todayIntake: DailyMacroIntake
        get() {
            val protein = todayLogs.sumOf { it.protein.toDouble() }.toFloat()
            val carbs = todayLogs.sumOf { it.carbs.toDouble() }.toFloat()
            val fat = todayLogs.sumOf { it.fat.toDouble() }.toFloat()
            return DailyMacroIntake(
                date = LocalDate.now(),
                protein = protein,
                carbs = carbs,
                fat = fat
            )
        }
}

sealed class DashboardEvent {
    data class LoadData(val userId: String) : DashboardEvent()
    data class UpdateWeight(val userId: String, val weight: Float, val date: LocalDate) : DashboardEvent()
    data class ChangeMonth(val userId: String, val yearMonth: YearMonth) : DashboardEvent()
    data class DeleteLog(val userId: String, val log: RemoteFoodLog) : DashboardEvent()
    data class RequestGoalInsight(val userId: String) : DashboardEvent()
    object MessageConsumed : DashboardEvent()
}

class DashboardViewModel(
    private val userRepository: UserRepository,
    private val macroRepository: MacroDataRepository,
    private val foodRepository: FoodRepository,
    private val deepSeekService: DeepSeekService
) : ViewModel() {

    private val _uiState = MutableStateFlow(DashboardUiState())
    val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()

    private var todayLogsJob: Job? = null

    fun onEvent(event: DashboardEvent) {
        when (event) {
            is DashboardEvent.LoadData -> loadDashboardData(event.userId)
            is DashboardEvent.UpdateWeight -> updateWeight(event.userId, event.weight, event.date)
            is DashboardEvent.ChangeMonth -> loadDashboardData(event.userId, event.yearMonth)
            is DashboardEvent.DeleteLog -> deleteLog(event.userId, event.log)
            is DashboardEvent.RequestGoalInsight -> requestGoalInsight(event.userId)
            is DashboardEvent.MessageConsumed -> _uiState.update { it.copy(message = null) }
        }
    }

    private fun loadDashboardData(userId: String, yearMonth: YearMonth = YearMonth.now()) {
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
                // Refresh the whole range to ensure consistency
                try {
                    foodRepository.refreshRange(userId, fetchStartDate, today)
                } catch (e: Exception) {
                    Log.e("DashboardViewModel", "Error refreshing logs", e)
                }

                val allLogs = try {
                    foodRepository.getMonthlyLogs(userId, YearMonth.from(fetchStartDate)) 
                } catch (e: Exception) {
                    Log.e("DashboardViewModel", "Error fetching logs from cache", e)
                    emptyList()
                }
                
                // Group logs by date once to avoid repeated filtering
                val logsByDate = allLogs.groupBy { it.createdAt.take(10) }
                
                val todayLogs = logsByDate[today.toString()] ?: emptyList()
                
                // Observe today's logs in real time so deletions from the Food Log screen or
                // elsewhere are reflected immediately without waiting for the next refresh.
                observeTodayLogs(userId)
                
                // Weekly Data — the current calendar week, Sunday to Saturday,
                // regardless of which day "today" falls on.
                val weekStart = today.minusDays((today.dayOfWeek.value % 7).toLong())
                val weeklyData = (0..6).map { i ->
                    val date = weekStart.plusDays(i.toLong())
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
                        username = profile?.name ?: userId,
                        weeklyIntake = weeklyData,
                        monthlyIntake = monthlyData,
                        todayLogs = todayLogs,
                        weightHistory = weightHistory,
                        currentStreak = streak,
                        trackedDates = trackedDates,
                        targets = profile?.toNutritionTargets() ?: NutritionTargets.DEFAULT,
                        weightAdvice = profile?.weightAdvice,
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

    private fun observeTodayLogs(userId: String) {
        todayLogsJob?.cancel()
        todayLogsJob = viewModelScope.launch {
            foodRepository.observeLogsForDate(userId, LocalDate.now()).collect { logs ->
                _uiState.update { it.copy(todayLogs = logs) }
            }
        }
    }

    private fun deleteLog(userId: String, log: RemoteFoodLog) {
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

    private fun updateWeight(userId: String, weight: Float, date: LocalDate) {
        viewModelScope.launch {
            userRepository.saveWeight(userId, weight, date.toString())
            
            // Reload weight history to get the latest data including the just-saved one
            val weightHistory = userRepository.getWeightHistory(userId).sortedBy { it.date }
            val profile = userRepository.getProfile(userId)
            
            if (profile != null) {
                // Calculate 7-day trend
                val sevenDaysAgo = LocalDate.now().minusDays(7).toString()
                val weight7DaysAgo = weightHistory.lastOrNull { it.date <= sevenDaysAgo }?.weight 
                    ?: weightHistory.firstOrNull()?.weight ?: weight
                
                val change7Days = weight - weight7DaysAgo

                val healthContext = HealthContext(
                    name = profile.name,
                    gender = profile.gender,
                    currentWeight = weight,
                    heightCm = profile.height.toFloatOrNull() ?: 0f,
                    weightChange7Days = change7Days,
                    experience = profile.liftingExperience
                )

                val advice = deepSeekService.getWeightAdvice(healthContext)
                if (advice != null) {
                    userRepository.saveProfile(profile.copy(weightAdvice = advice))
                }
            }

            // Reload data to update graph and advice
            loadDashboardData(userId)

            // Auto-request goal insight after weight update
            requestGoalInsight(userId)
        }
    }

    private fun requestGoalInsight(userId: String) {
        viewModelScope.launch {
            Log.d("DashboardViewModel", "requestGoalInsight started for userId: $userId")
            _uiState.update { it.copy(isGoalInsightLoading = true) }

            try {
                val profile = userRepository.getProfile(userId)
                val weightHistory = userRepository.getWeightHistory(userId).sortedBy { it.date }

                Log.d("DashboardViewModel", "Goal insight - profile: ${profile != null}, weightRecords: ${weightHistory.size}")

                if (weightHistory.isEmpty()) {
                    Log.w("DashboardViewModel", "Goal insight skipped - no weight history")
                    _uiState.update { it.copy(isGoalInsightLoading = false) }
                    return@launch
                }

                val today = LocalDate.now()
                val sevenDaysAgo = today.minusDays(7)
                val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")

                val last7Days = weightHistory
                    .filter { LocalDate.parse(it.date, formatter).isAfter(sevenDaysAgo) }
                    .map { it.date to it.weight }

                val currentWeight = weightHistory.lastOrNull()?.weight ?: 0f

                Log.d("DashboardViewModel", "Goal insight context - weight: $currentWeight, last7Days: $last7Days")

                val context = GoalInsightContext(
                    name = profile?.name ?: "User",
                    gender = profile?.gender ?: "unknown",
                    birthday = profile?.birthday ?: "",
                    currentWeight = currentWeight,
                    heightCm = profile?.height?.toFloatOrNull() ?: 0f,
                    experience = profile?.liftingExperience ?: "beginner",
                    weightHistoryLast7Days = last7Days
                )

                val insight = deepSeekService.getGoalInsight(context)
                Log.d("DashboardViewModel", "Goal insight result: ${insight?.take(50) ?: "NULL"}")
                _uiState.update { it.copy(goalInsight = insight, isGoalInsightLoading = false) }
            } catch (e: Exception) {
                Log.e("DashboardViewModel", "Error requesting goal insight", e)
                _uiState.update { it.copy(isGoalInsightLoading = false) }
            }
        }
    }
}
