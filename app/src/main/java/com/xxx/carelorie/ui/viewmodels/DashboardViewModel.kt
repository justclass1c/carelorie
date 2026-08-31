package com.xxx.carelorie.ui.viewmodels

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.xxx.carelorie.data.DailyMacroIntake
import com.xxx.carelorie.data.FoodRepository
import com.xxx.carelorie.data.MealPresetRepository
import com.xxx.carelorie.data.NutritionTargets
import com.xxx.carelorie.data.UserRepository
import com.xxx.carelorie.data.remote.CoachContext
import com.xxx.carelorie.data.remote.DeepSeekService
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
    /**
     * The coach's read on the user's weight trend.
     *
     * Seeded from the profile on load so returning to the Goal tab shows the last insight
     * instantly instead of billing a fresh API call every visit.
     */
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
    /** Saves everything currently logged to [mealType] as a reusable meal. */
    data class SaveMealAsPreset(
        val userId: String,
        val mealType: String,
        val name: String
    ) : DashboardEvent()
    /** [force] bypasses the "already have one" guard — used by the refresh button. */
    data class RequestGoalInsight(val userId: String, val force: Boolean = false) : DashboardEvent()
    object MessageConsumed : DashboardEvent()
}

class DashboardViewModel(
    private val userRepository: UserRepository,
    private val foodRepository: FoodRepository,
    private val mealPresetRepository: MealPresetRepository,
    private val deepSeekService: DeepSeekService
) : ViewModel() {

    private val _uiState = MutableStateFlow(DashboardUiState())
    val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()

    private var todayLogsJob: Job? = null
    private var goalInsightJob: Job? = null

    /** Which account the state on screen belongs to. Null until the first load. */
    private var loadedForUser: String? = null

    fun onEvent(event: DashboardEvent) {
        when (event) {
            is DashboardEvent.LoadData -> loadDashboardData(event.userId)
            is DashboardEvent.UpdateWeight -> updateWeight(event.userId, event.weight, event.date)
            is DashboardEvent.ChangeMonth -> loadDashboardData(event.userId, event.yearMonth)
            is DashboardEvent.DeleteLog -> deleteLog(event.userId, event.log)
            is DashboardEvent.SaveMealAsPreset ->
                saveMealAsPreset(event.userId, event.mealType, event.name)
            is DashboardEvent.RequestGoalInsight -> requestGoalInsight(event.userId, event.force)
            is DashboardEvent.MessageConsumed -> _uiState.update { it.copy(message = null) }
        }
    }

    private fun loadDashboardData(userId: String, yearMonth: YearMonth = YearMonth.now()) {
        // A different account than the one on screen: throw the old data away instead of showing
        // the previous user's name, streak, targets and meals until the first refresh lands. This
        // ViewModel is owned by the Activity, so signing out does not dispose it.
        if (loadedForUser != null && loadedForUser != userId) {
            todayLogsJob?.cancel()
            goalInsightJob?.cancel()
            _uiState.value = DashboardUiState()
        }
        loadedForUser = userId

        _uiState.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            try {
                val profile = userRepository.getProfile(userId)
                
                val today = LocalDate.now()
                // Fetch enough logs to cover the selected month AND the last 7 days for the dashboard
                val fetchStartDate = if (yearMonth.atDay(1).isBefore(today.minusDays(7))) 
                    yearMonth.atDay(1) 
                else 
                    today.minusDays(7)

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

                // Dates for the calendar come from the fetched window; the streak needs the
                // whole history, or it silently caps at the day of the month and changes
                // every time the user pages to a different month.
                val trackedDates = allLogs.mapNotNull {
                    try { LocalDate.parse(it.createdAt.take(10)) } catch (e: Exception) { null }
                }.toSet()

                val allLoggedDates = try {
                    foodRepository.getAllLoggedDates(userId)
                } catch (e: Exception) {
                    Log.e("DashboardViewModel", "Could not read logged dates", e)
                    trackedDates
                }

                val streak = currentStreak(allLoggedDates, today)

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
                        // Keep an insight already on screen; otherwise fall back to the stored one.
                        goalInsight = it.goalInsight ?: profile?.weightAdvice,
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

    /**
     * Saves the foods currently logged to [mealType] under [name].
     *
     * Acts on what is on screen rather than on a food-library selection, because that is what the
     * user means by "save this meal" — quantities included.
     */
    private fun saveMealAsPreset(userId: String, mealType: String, name: String) {
        val logs = _uiState.value.todayLogs
            .filter { it.mealType.equals(mealType, ignoreCase = true) }

        if (logs.isEmpty()) {
            _uiState.update { it.copy(message = "Nothing logged to $mealType yet") }
            return
        }

        viewModelScope.launch {
            try {
                if (mealPresetRepository.nameIsTaken(userId, name)) {
                    _uiState.update { it.copy(message = "You already have a meal called ${name.trim()}") }
                    return@launch
                }
                mealPresetRepository.saveFromLogs(userId, name, mealType, logs)
                _uiState.update { it.copy(message = "Saved ${name.trim()}") }
            } catch (e: Exception) {
                Log.e("DashboardViewModel", "Could not save meal preset", e)
                _uiState.update { it.copy(message = "Could not save that meal") }
            }
        }
    }

    private fun updateWeight(userId: String, weight: Float, date: LocalDate) {
        viewModelScope.launch {
            userRepository.saveWeight(userId, weight, date.toString())

            // Redraw straight away. The coach call used to be awaited here, which left the graph
            // showing the old weight until the network answered — with no spinner, so the app
            // simply looked frozen on a slow connection.
            loadDashboardData(userId)

            // Now that the history has changed, the standing insight is stale.
            requestGoalInsight(userId, force = true)
        }
    }

    /**
     * Asks the coach for a read on the user's weight trend.
     *
     * Skips the call when an insight is already on screen unless [force] is set, because the Goal
     * screen asks on every resume and each call is billed. The result is written to the profile so
     * it survives process death and seeds [DashboardUiState.goalInsight] on the next load.
     */
    private fun requestGoalInsight(userId: String, force: Boolean = false) {
        if (!force && (_uiState.value.goalInsight != null || _uiState.value.isGoalInsightLoading)) return

        goalInsightJob?.cancel()
        goalInsightJob = viewModelScope.launch {
            _uiState.update { it.copy(isGoalInsightLoading = true) }
            try {
                val profile = userRepository.getProfile(userId)
                if (profile == null) {
                    _uiState.update { it.copy(isGoalInsightLoading = false) }
                    return@launch
                }

                val cutoff = LocalDate.now().minusDays(7)
                val recentWeights = userRepository.getWeightHistory(userId)
                    .sortedBy { it.date }
                    .filter { record ->
                        runCatching { LocalDate.parse(record.date) }.getOrNull()
                            ?.isAfter(cutoff) == true
                    }
                    .map { it.date to it.weight }

                val insight = deepSeekService.getCoachInsight(
                    CoachContext(profile = profile, weightHistoryLast7Days = recentWeights)
                )

                if (insight != null) {
                    // Persist alongside the profile so reopening the tab costs nothing.
                    userRepository.saveProfile(profile.copy(weightAdvice = insight))
                }
                _uiState.update {
                    it.copy(goalInsight = insight ?: it.goalInsight, isGoalInsightLoading = false)
                }
            } catch (e: Exception) {
                Log.e("DashboardViewModel", "Could not fetch coach insight", e)
                _uiState.update { it.copy(isGoalInsightLoading = false) }
            }
        }
    }

    /**
     * Consecutive days ending today (or yesterday) with at least one entry.
     *
     * Yesterday counts as the anchor when today has nothing logged yet, so the number does not
     * drop to zero every morning before breakfast.
     */
    private fun currentStreak(loggedDates: Set<LocalDate>, today: LocalDate): Int {
        val anchor = when {
            loggedDates.contains(today) -> today
            loggedDates.contains(today.minusDays(1)) -> today.minusDays(1)
            else -> return 0
        }
        var streak = 0
        var cursor = anchor
        while (loggedDates.contains(cursor)) {
            streak++
            cursor = cursor.minusDays(1)
        }
        return streak
    }
}
