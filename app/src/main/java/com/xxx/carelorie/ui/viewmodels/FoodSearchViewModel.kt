package com.xxx.carelorie.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.xxx.carelorie.data.FoodRepository
import com.xxx.carelorie.data.remote.RemoteFoodPreset
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class FoodSearchUiState(
    val query: String = "",
    val presets: List<RemoteFoodPreset> = emptyList(),
    val filteredPresets: List<RemoteFoodPreset> = emptyList(),
    val isLoading: Boolean = false,
    val isSuccess: Boolean = false
)

sealed class FoodSearchEvent {
    data class LoadPresets(val userId: Int) : FoodSearchEvent()
    data class SearchQueryChanged(val query: String) : FoodSearchEvent()
    data class LogFood(val userId: Int, val mealType: String, val food: RemoteFoodPreset) : FoodSearchEvent()
    object ResetSuccess : FoodSearchEvent()
}

class FoodSearchViewModel(private val foodRepository: FoodRepository) : ViewModel() {

    private val _uiState = MutableStateFlow(FoodSearchUiState())
    val uiState: StateFlow<FoodSearchUiState> = _uiState.asStateFlow()

    fun onEvent(event: FoodSearchEvent) {
        when (event) {
            is FoodSearchEvent.LoadPresets -> loadPresets(event.userId)
            is FoodSearchEvent.SearchQueryChanged -> updateSearchQuery(event.query)
            is FoodSearchEvent.LogFood -> logFood(event.userId, event.mealType, event.food)
            is FoodSearchEvent.ResetSuccess -> _uiState.update { it.copy(isSuccess = false) }
        }
    }

    private fun loadPresets(userId: Int) {
        _uiState.update { it.copy(isLoading = true) }
        viewModelScope.launch {
            try {
                val presets = foodRepository.getFoodPresets(userId)
                _uiState.update { 
                    it.copy(
                        presets = presets,
                        filteredPresets = presets,
                        isLoading = false
                    )
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false) }
                e.printStackTrace()
            }
        }
    }

    private fun updateSearchQuery(query: String) {
        _uiState.update { state ->
            val filtered = if (query.isBlank()) {
                state.presets
            } else {
                state.presets.filter { it.name.contains(query, ignoreCase = true) }
            }
            state.copy(query = query, filteredPresets = filtered)
        }
    }

    private fun logFood(userId: Int, mealType: String, food: RemoteFoodPreset) {
        viewModelScope.launch {
            try {
                foodRepository.logFood(userId, mealType, food)
                _uiState.update { it.copy(isSuccess = true) }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}
