package com.xxx.carelorie.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.xxx.carelorie.data.FoodRepository
import com.xxx.carelorie.data.NutritionTargets
import com.xxx.carelorie.data.SyncResult
import com.xxx.carelorie.data.UserRepository
import com.xxx.carelorie.data.remote.RemoteFoodLog
import com.xxx.carelorie.ui.components.dashboard.MEAL_TYPES
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.format.DateTimeFormatter

/** One meal bucket with its entries and totals, ready to render. */
data class MealGroup(
    val mealType: String,
    val entries: List<RemoteFoodLog>
) {
    val calories: Int get() = entries.sumOf { it.calories }
    val protein: Float get() = entries.sumOf { it.protein.toDouble() }.toFloat()
    val carbs: Float get() = entries.sumOf { it.carbs.toDouble() }.toFloat()
    val fat: Float get() = entries.sumOf { it.fat.toDouble() }.toFloat()
    val isEmpty: Boolean get() = entries.isEmpty()
}

data class FoodLogUiState(
    val selectedDate: LocalDate = LocalDate.now(),
    val logs: List<RemoteFoodLog> = emptyList(),
    val loggedDates: Set<LocalDate> = emptySet(),
    val calendarMonth: YearMonth = YearMonth.now(),
    val isCalendarVisible: Boolean = false,
    /**
     * Starts true: until Room has answered we do not know whether the day is empty, and
     * rendering the empty state on that first frame flashes "nothing logged" over a day
     * that actually has entries.
     */
    val isLoading: Boolean = true,
    val isOffline: Boolean = false,
    val message: String? = null,
    val targets: NutritionTargets = NutritionTargets.DEFAULT
) {
    val totalProtein: Float get() = logs.sumOf { it.protein.toDouble() }.toFloat()
    val totalCarbs: Float get() = logs.sumOf { it.carbs.toDouble() }.toFloat()
    val totalFat: Float get() = logs.sumOf { it.fat.toDouble() }.toFloat()
    val totalCalories: Int get() = logs.sumOf { it.calories }

    val isToday: Boolean get() = selectedDate == LocalDate.now()
    val isFuture: Boolean get() = selectedDate.isAfter(LocalDate.now())

    /** Entries split into the four meal buckets, in dashboard order. */
    val mealGroups: List<MealGroup>
        get() = MEAL_TYPES.map { meal ->
            MealGroup(
                mealType = meal,
                entries = logs.filter { it.mealType.equals(meal, ignoreCase = true) }
            )
        }

    /** Anything logged under an unrecognised meal type still needs somewhere to go. */
    val otherEntries: List<RemoteFoodLog>
        get() = logs.filterNot { log -> MEAL_TYPES.any { it.equals(log.mealType, ignoreCase = true) } }
}

sealed class FoodLogEvent {
    data class Start(val userId: String) : FoodLogEvent()
    data class LoadLogs(val userId: String, val date: LocalDate) : FoodLogEvent()
    data class ChangeDate(val userId: String, val newDate: LocalDate) : FoodLogEvent()
    data class ChangeMonth(val yearMonth: YearMonth) : FoodLogEvent()
    data class DeleteLog(val userId: String, val log: RemoteFoodLog) : FoodLogEvent()
    data class UpdateLog(
        val userId: String,
        val localId: String,
        val quantity: Float,
        val mealType: String
    ) : FoodLogEvent()
    data class Refresh(val userId: String) : FoodLogEvent()
    object ToggleCalendar : FoodLogEvent()
    object MessageConsumed : FoodLogEvent()
}

class FoodLogViewModel(
    private val foodRepository: FoodRepository,
    private val userRepository: UserRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(FoodLogUiState())
    val uiState: StateFlow<FoodLogUiState> = _uiState.asStateFlow()

    private var logsJob: Job? = null
    private var datesJob: Job? = null
    private var startedForUser: String? = null

    fun onEvent(event: FoodLogEvent) {
        when (event) {
            is FoodLogEvent.Start -> start(event.userId)
            is FoodLogEvent.LoadLogs -> {
                _uiState.update { it.copy(selectedDate = event.date) }
                observeLogs(event.userId, event.date)
                syncFrom(event.userId, event.date)
            }
            is FoodLogEvent.ChangeDate -> changeDate(event.userId, event.newDate)
            is FoodLogEvent.ChangeMonth -> _uiState.update { it.copy(calendarMonth = event.yearMonth) }
            is FoodLogEvent.DeleteLog -> deleteLog(event.userId, event.log)
            is FoodLogEvent.UpdateLog ->
                updateLog(event.userId, event.localId, event.quantity, event.mealType)
            is FoodLogEvent.Refresh -> syncFrom(event.userId, _uiState.value.selectedDate)
            is FoodLogEvent.ToggleCalendar ->
                _uiState.update { it.copy(isCalendarVisible = !it.isCalendarVisible) }
            is FoodLogEvent.MessageConsumed -> _uiState.update { it.copy(message = null) }
        }
    }

    /** Idempotent — safe to call on every recomposition of the screen. */
    private fun start(userId: String) {
        if (startedForUser == userId) {
            syncFrom(userId, _uiState.value.selectedDate)
            return
        }
        startedForUser = userId
        observeLogs(userId, _uiState.value.selectedDate)
        observeLoggedDates(userId)
        syncFrom(userId, _uiState.value.selectedDate)
        loadTargets(userId)
    }

    /** Pulls the user's daily macro limits so the summary ring reflects their settings. */
    private fun loadTargets(userId: String) {
        viewModelScope.launch {
            val profile = userRepository.getProfile(userId)
            if (profile != null) {
                _uiState.update { it.copy(targets = profile.toNutritionTargets()) }
            }
        }
    }

    private fun changeDate(userId: String, newDate: LocalDate) {
        _uiState.update {
            it.copy(selectedDate = newDate, calendarMonth = YearMonth.from(newDate))
        }
        observeLogs(userId, newDate)
        syncFrom(userId, newDate)
    }

    /**
     * Reads from Room, so entries appear with no connection and update the moment
     * anything is added or removed anywhere in the app.
     */
    private fun observeLogs(userId: String, date: LocalDate) {
        logsJob?.cancel()
        logsJob = viewModelScope.launch {
            foodRepository.observeLogsForDate(userId, date).collect { logs ->
                _uiState.update { it.copy(logs = logs, isLoading = false) }
            }
        }
    }

    private fun observeLoggedDates(userId: String) {
        datesJob?.cancel()
        datesJob = viewModelScope.launch {
            foodRepository.observeLoggedDates(userId).collect { dates ->
                _uiState.update { it.copy(loggedDates = dates) }
            }
        }
    }

    private fun syncFrom(userId: String, date: LocalDate) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            // Pull the whole surrounding month so calendar markers stay accurate.
            val from = YearMonth.from(date).atDay(1)
            val result = foodRepository.refresh(userId, from)
            _uiState.update {
                it.copy(isLoading = false, isOffline = result == SyncResult.OFFLINE)  // FAILED is a
                    // server-side problem, not a missing connection, so it must not show
                    // the "showing saved history" banner.
            }
        }
    }

    /**
     * Changes an entry's servings or meal.
     *
     * The list redraws from Room on its own, so nothing here touches [logs] — only the
     * confirmation message and the push are this function's job.
     */
    private fun updateLog(userId: String, localId: String, quantity: Float, mealType: String) {
        viewModelScope.launch {
            val result = foodRepository.updateLog(localId, quantity, mealType)
            _uiState.update {
                it.copy(
                    message = if (result.isSuccess) {
                        "Entry updated"
                    } else {
                        result.exceptionOrNull()?.message ?: "Could not update that entry"
                    }
                )
            }
            syncFrom(userId, _uiState.value.selectedDate)
        }
    }

    private fun deleteLog(userId: String, log: RemoteFoodLog) {
        viewModelScope.launch {
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
            // Attempt to push the pending delete to the server immediately so the log does not
            // resurrect on the next refresh.
            syncFrom(userId, _uiState.value.selectedDate)
        }
    }

    companion object {
        private val TIME_FORMAT = DateTimeFormatter.ofPattern("h:mm a")

        /** Renders an entry's ISO timestamp as a short clock time, or null if unparseable. */
        fun formatLoggedTime(createdAt: String): String? = runCatching {
            LocalDateTime.parse(createdAt).format(TIME_FORMAT)
        }.getOrNull()
    }
}
