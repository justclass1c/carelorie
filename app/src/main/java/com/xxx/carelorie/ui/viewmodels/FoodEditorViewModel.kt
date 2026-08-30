package com.xxx.carelorie.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.xxx.carelorie.data.FoodRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class FoodEditorUiState(
    val userId: String = "",
    /** null when creating; set when editing one of the user's own foods. */
    val localId: String? = null,
    val name: String = "",
    val brand: String = "",
    val servingDescription: String = "",
    val calories: String = "",
    val protein: String = "",
    val carbs: String = "",
    val fat: String = "",
    val isLoading: Boolean = false,
    val isSaved: Boolean = false,
    val errorMessage: String? = null,
    /** True when the form was seeded from a built-in, so the UI can say it will make a copy. */
    val isCopyOfBuiltIn: Boolean = false
) {
    val isEditingExisting: Boolean get() = localId != null

    /**
     * Calories implied by the macros, for the "doesn't add up" hint.
     * Protein and carbs are 4 kcal/g, fat 9.
     */
    val caloriesFromMacros: Int?
        get() {
            val p = protein.toFloatOrNull() ?: return null
            val c = carbs.toFloatOrNull() ?: return null
            val f = fat.toFloatOrNull() ?: return null
            return (p * 4 + c * 4 + f * 9).toInt()
        }
}

sealed class FoodEditorEvent {
    data class Load(val userId: String, val localId: String?) : FoodEditorEvent()
    data class NameChanged(val value: String) : FoodEditorEvent()
    data class BrandChanged(val value: String) : FoodEditorEvent()
    data class ServingChanged(val value: String) : FoodEditorEvent()
    data class CaloriesChanged(val value: String) : FoodEditorEvent()
    data class ProteinChanged(val value: String) : FoodEditorEvent()
    data class CarbsChanged(val value: String) : FoodEditorEvent()
    data class FatChanged(val value: String) : FoodEditorEvent()
    object UseMacroCalories : FoodEditorEvent()
    object Save : FoodEditorEvent()
    object ErrorConsumed : FoodEditorEvent()
}

class FoodEditorViewModel(
    private val foodRepository: FoodRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(FoodEditorUiState())
    val uiState: StateFlow<FoodEditorUiState> = _uiState.asStateFlow()

    fun onEvent(event: FoodEditorEvent) {
        when (event) {
            is FoodEditorEvent.Load -> load(event.userId, event.localId)
            is FoodEditorEvent.NameChanged -> _uiState.update { it.copy(name = event.value) }
            is FoodEditorEvent.BrandChanged -> _uiState.update { it.copy(brand = event.value) }
            is FoodEditorEvent.ServingChanged -> _uiState.update { it.copy(servingDescription = event.value) }
            is FoodEditorEvent.CaloriesChanged -> _uiState.update { it.copy(calories = event.value) }
            is FoodEditorEvent.ProteinChanged -> _uiState.update { it.copy(protein = event.value) }
            is FoodEditorEvent.CarbsChanged -> _uiState.update { it.copy(carbs = event.value) }
            is FoodEditorEvent.FatChanged -> _uiState.update { it.copy(fat = event.value) }
            is FoodEditorEvent.UseMacroCalories -> _uiState.update { state ->
                state.caloriesFromMacros?.let { state.copy(calories = it.toString()) } ?: state
            }
            is FoodEditorEvent.Save -> save()
            is FoodEditorEvent.ErrorConsumed -> _uiState.update { it.copy(errorMessage = null) }
        }
    }

    private fun load(userId: String, localId: String?) {
        _uiState.update { it.copy(userId = userId) }
        if (localId == null) return

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val preset = foodRepository.getPreset(localId)
            if (preset == null) {
                _uiState.update { it.copy(isLoading = false, errorMessage = "That food no longer exists.") }
                return@launch
            }
            // Built-ins are shared rows, so opening one seeds a new personal copy rather than
            // editing the original in place.
            val editingCopy = preset.isBuiltIn
            _uiState.update {
                it.copy(
                    localId = if (editingCopy) null else preset.localId,
                    isCopyOfBuiltIn = editingCopy,
                    name = preset.name,
                    brand = preset.brand.orEmpty(),
                    servingDescription = preset.servingDescription.orEmpty(),
                    calories = preset.calories.toString(),
                    protein = formatFloat(preset.protein),
                    carbs = formatFloat(preset.carbs),
                    fat = formatFloat(preset.fat),
                    isLoading = false
                )
            }
        }
    }

    private fun formatFloat(value: Float): String =
        if (value == value.toInt().toFloat()) value.toInt().toString() else value.toString()

    private fun save() {
        val state = _uiState.value

        val error = validate(state)
        if (error != null) {
            _uiState.update { it.copy(errorMessage = error) }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val result = foodRepository.savePreset(
                userId = state.userId,
                localId = state.localId,
                name = state.name.trim(),
                brand = state.brand.trim().takeIf { it.isNotEmpty() },
                servingDescription = state.servingDescription.trim().takeIf { it.isNotEmpty() },
                calories = state.calories.trim().toInt(),
                protein = state.protein.trim().toFloat(),
                carbs = state.carbs.trim().toFloat(),
                fat = state.fat.trim().toFloat()
            )
            _uiState.update {
                if (result.isSuccess) {
                    it.copy(isLoading = false, isSaved = true)
                } else {
                    it.copy(
                        isLoading = false,
                        errorMessage = result.exceptionOrNull()?.message ?: "Could not save that food."
                    )
                }
            }
        }
    }

    /** @return the first problem with the form, or null when it is ready to save. */
    private fun validate(state: FoodEditorUiState): String? {
        if (state.userId.isEmpty()) return "You need to be signed in to save a food."
        if (state.name.isBlank()) return "Name is required"

        val calories = state.calories.trim().toIntOrNull()
            ?: return "Calories must be a whole number"
        if (calories < 0) return "Calories can't be negative"

        val macros = listOf(
            "Protein" to state.protein,
            "Carbs" to state.carbs,
            "Fat" to state.fat
        )
        for ((label, raw) in macros) {
            val value = raw.trim().toFloatOrNull() ?: return "$label must be a number"
            if (value < 0f) return "$label can't be negative"
        }
        return null
    }
}
