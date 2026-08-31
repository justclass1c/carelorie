package com.xxx.carelorie.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.xxx.carelorie.data.FoodRepository
import com.xxx.carelorie.data.MealPresetRepository
import com.xxx.carelorie.data.MealPresetWithItems
import com.xxx.carelorie.data.remote.RemoteFoodLog
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate

data class SavedMealsUiState(
    val userId: String = "",
    val meals: List<MealPresetWithItems> = emptyList(),
    val isLoading: Boolean = true,
    val message: String? = null
)

sealed class SavedMealsEvent {
    data class Start(val userId: String) : SavedMealsEvent()
    data class LogMeal(
        val meal: MealPresetWithItems,
        val mealType: String,
        val date: LocalDate = LocalDate.now()
    ) : SavedMealsEvent()
    data class Rename(val meal: MealPresetWithItems, val name: String) : SavedMealsEvent()
    data class Delete(val meal: MealPresetWithItems) : SavedMealsEvent()
    object MessageConsumed : SavedMealsEvent()
}

/**
 * The saved-meals library.
 *
 * Reads through a Flow so a meal saved from the dashboard shows up here without a refresh.
 */
class SavedMealsViewModel(
    private val mealPresetRepository: MealPresetRepository,
    private val foodRepository: FoodRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(SavedMealsUiState())
    val uiState: StateFlow<SavedMealsUiState> = _uiState.asStateFlow()

    private var mealsJob: Job? = null

    fun onEvent(event: SavedMealsEvent) {
        when (event) {
            is SavedMealsEvent.Start -> start(event.userId)
            is SavedMealsEvent.LogMeal -> logMeal(event.meal, event.mealType, event.date)
            is SavedMealsEvent.Rename -> rename(event.meal, event.name)
            is SavedMealsEvent.Delete -> delete(event.meal)
            SavedMealsEvent.MessageConsumed -> _uiState.update { it.copy(message = null) }
        }
    }

    private fun start(userId: String) {
        if (_uiState.value.userId == userId && mealsJob != null) return
        _uiState.update { it.copy(userId = userId) }
        mealsJob?.cancel()
        mealsJob = viewModelScope.launch {
            mealPresetRepository.observeMeals(userId).collect { meals ->
                _uiState.update { it.copy(meals = meals, isLoading = false) }
            }
        }
    }

    private fun logMeal(meal: MealPresetWithItems, mealType: String, date: LocalDate) {
        val userId = _uiState.value.userId
        if (userId.isEmpty()) return
        viewModelScope.launch {
            try {
                val count = mealPresetRepository.logMeal(userId, meal, mealType, date)
                _uiState.update {
                    it.copy(
                        message = if (count == 1) {
                            "Added 1 item to $mealType"
                        } else {
                            "Added $count items to $mealType"
                        }
                    )
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(message = "Could not log ${meal.meal.name}") }
            }
        }
    }

    private fun rename(meal: MealPresetWithItems, name: String) {
        if (name.isBlank()) return
        viewModelScope.launch {
            mealPresetRepository.rename(meal, name)
            _uiState.update { it.copy(message = "Renamed to ${name.trim()}") }
        }
    }

    private fun delete(meal: MealPresetWithItems) {
        viewModelScope.launch {
            mealPresetRepository.delete(meal.meal.localId)
            _uiState.update { it.copy(message = "Deleted ${meal.meal.name}") }
        }
    }
}
