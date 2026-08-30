package com.xxx.carelorie.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.xxx.carelorie.data.FoodRepository
import com.xxx.carelorie.data.SyncResult
import com.xxx.carelorie.data.local.FoodPresetEntity
import com.xxx.carelorie.data.nutrition.FoodCandidate
import com.xxx.carelorie.data.nutrition.FoodRecognitionService
import com.xxx.carelorie.data.nutrition.OpenFoodFactsService
import com.xxx.carelorie.data.nutrition.RecognitionResult
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class FoodQueryUiState(
    val userId: String = "",
    val query: String = "",
    val allPresets: List<FoodPresetEntity> = emptyList(),
    /** Starts true for the same reason as the food log: see FoodLogUiState.isLoading. */
    val isLoading: Boolean = true,
    val isOffline: Boolean = false,
    val message: String? = null,
    /** Kept so the snackbar's Undo can put a deleted food back. */
    val lastDeleted: FoodPresetEntity? = null,
    /** Foods found online, by barcode or by AI — not saved until the user adds one. */
    val searchResults: List<FoodCandidate> = emptyList(),
    /** Where [searchResults] came from, e.g. "Online results for \"milk\"". */
    val resultsLabel: String? = null,
    val isSearching: Boolean = false,
    val isAnalysing: Boolean = false
) {
    private fun matches(preset: FoodPresetEntity): Boolean =
        query.isBlank() ||
            preset.name.contains(query, ignoreCase = true) ||
            preset.brand?.contains(query, ignoreCase = true) == true

    /** Foods the user created — the only ones that can be edited or deleted. */
    val ownFoods: List<FoodPresetEntity>
        get() = allPresets.filter { !it.isBuiltIn && matches(it) }

    /** The dishes that ship with the app. Read-only; copy to edit. */
    val builtInFoods: List<FoodPresetEntity>
        get() = allPresets.filter { it.isBuiltIn && matches(it) }

    val isEmpty: Boolean get() = ownFoods.isEmpty() && builtInFoods.isEmpty()
    val hasNoCustomFoods: Boolean get() = allPresets.none { !it.isBuiltIn }

    val isBusy: Boolean get() = isSearching || isAnalysing
    val hasResults: Boolean get() = searchResults.isNotEmpty()
}

sealed class FoodQueryEvent {
    data class Start(val userId: String) : FoodQueryEvent()
    data class QueryChanged(val query: String) : FoodQueryEvent()
    data class Delete(val preset: FoodPresetEntity) : FoodQueryEvent()
    data class CopyToOwnFoods(val preset: FoodPresetEntity) : FoodQueryEvent()
    object UndoDelete : FoodQueryEvent()
    object Refresh : FoodQueryEvent()
    object MessageConsumed : FoodQueryEvent()

    // --- finding foods that aren't in the library yet
    object SearchOnline : FoodQueryEvent()
    data class BarcodeScanned(val barcode: String) : FoodQueryEvent()
    object AiSearch : FoodQueryEvent()
    data class AddResult(val candidate: FoodCandidate) : FoodQueryEvent()
    object ClearResults : FoodQueryEvent()
}

class FoodQueryViewModel(
    private val foodRepository: FoodRepository,
    private val openFoodFacts: OpenFoodFactsService,
    private val recognitionService: FoodRecognitionService
) : ViewModel() {

    private val _uiState = MutableStateFlow(FoodQueryUiState())
    val uiState: StateFlow<FoodQueryUiState> = _uiState.asStateFlow()

    private var presetsJob: Job? = null
    private var searchJob: Job? = null
    private var startedForUser: String? = null

    fun onEvent(event: FoodQueryEvent) {
        when (event) {
            is FoodQueryEvent.Start -> start(event.userId)
            is FoodQueryEvent.QueryChanged -> _uiState.update { it.copy(query = event.query) }
            is FoodQueryEvent.Delete -> delete(event.preset)
            is FoodQueryEvent.CopyToOwnFoods -> copy(event.preset)
            is FoodQueryEvent.UndoDelete -> undoDelete()
            is FoodQueryEvent.Refresh -> refresh(_uiState.value.userId)
            is FoodQueryEvent.MessageConsumed ->
                _uiState.update { it.copy(message = null, lastDeleted = null) }

            is FoodQueryEvent.SearchOnline -> searchOnline()
            is FoodQueryEvent.BarcodeScanned -> lookupBarcode(event.barcode)
            is FoodQueryEvent.AiSearch -> aiSearch()
            is FoodQueryEvent.AddResult -> addResult(event.candidate)
            is FoodQueryEvent.ClearResults -> _uiState.update {
                it.copy(searchResults = emptyList(), resultsLabel = null)
            }
        }
    }

    /** Idempotent — safe to call on every recomposition of the screen. */
    private fun start(userId: String) {
        _uiState.update { it.copy(userId = userId) }
        if (startedForUser == userId) {
            refresh(userId)
            return
        }
        startedForUser = userId

        // Reads from Room, so the list is there with no connection and updates the instant
        // anything is saved in the editor.
        presetsJob?.cancel()
        presetsJob = viewModelScope.launch {
            foodRepository.observePresets(userId).collect { presets ->
                _uiState.update { it.copy(allPresets = presets, isLoading = false) }
            }
        }
        refresh(userId)
    }

    private fun refresh(userId: String) {
        if (userId.isEmpty()) return
        viewModelScope.launch {
            val result = foodRepository.refreshPresets(userId)
            _uiState.update {
                it.copy(isLoading = false, isOffline = result == SyncResult.OFFLINE)
            }
        }
    }

    // ------------------------------------------------------------------ finding new foods

    /**
     * Open Food Facts lookup by name.
     *
     * Deliberately an explicit action rather than a keystroke: the typed query filters the local
     * library instantly, and only this button reaches the network.
     */
    private fun searchOnline() {
        val query = _uiState.value.query
        if (query.isBlank()) {
            _uiState.update { it.copy(message = "Type something to search for.") }
            return
        }
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            _uiState.update { it.copy(isSearching = true, resultsLabel = null) }
            val results = openFoodFacts.search(query)
            _uiState.update {
                it.copy(
                    searchResults = results,
                    resultsLabel = if (results.isEmpty()) null else "Online results for \"$query\"",
                    isSearching = false,
                    message = if (results.isEmpty()) {
                        "No results found online for \"$query\"."
                    } else null
                )
            }
        }
    }

    private fun lookupBarcode(barcode: String) {
        if (barcode.isBlank()) {
            _uiState.update { it.copy(message = "Scan cancelled or unreadable.") }
            return
        }
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            _uiState.update { it.copy(isSearching = true, resultsLabel = null) }
            val candidate = openFoodFacts.lookupBarcode(barcode)
            _uiState.update {
                if (candidate == null) {
                    it.copy(
                        isSearching = false,
                        message = "That barcode isn't in the database. Try searching by name."
                    )
                } else {
                    it.copy(
                        searchResults = listOf(candidate),
                        resultsLabel = "Scanned barcode",
                        isSearching = false
                    )
                }
            }
        }
    }

    /** Same two-step as the food search screen: online results as context, then an AI estimate. */
    private fun aiSearch() {
        val query = _uiState.value.query
        if (query.isBlank()) {
            _uiState.update { it.copy(message = "Type a food name first.") }
            return
        }
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            _uiState.update { it.copy(isAnalysing = true, resultsLabel = null) }

            val onlineResults = try {
                openFoodFacts.search(query).take(5)
            } catch (_: Exception) {
                emptyList()
            }
            val context = if (onlineResults.isNotEmpty()) {
                "Search results from food database:\n" + onlineResults.joinToString("\n") { c ->
                    "- ${c.preset.name}: ${c.calories}kcal, P:${c.protein}g, C:${c.carbs}g, F:${c.fat}g."
                }
            } else "No direct database match found."

            when (val result = recognitionService.estimateNutrition(query, context)) {
                is RecognitionResult.Success -> _uiState.update {
                    it.copy(
                        searchResults = result.candidates,
                        resultsLabel = "AI estimate for \"$query\"",
                        isAnalysing = false
                    )
                }
                // Falling back to whatever the database did return beats showing nothing.
                is RecognitionResult.Failure -> _uiState.update {
                    it.copy(
                        searchResults = onlineResults,
                        resultsLabel = if (onlineResults.isEmpty()) null else "Online results for \"$query\"",
                        isAnalysing = false,
                        message = if (onlineResults.isEmpty()) {
                            result.reason
                        } else {
                            "AI couldn't estimate; showing online results instead."
                        }
                    )
                }
                RecognitionResult.NotConfigured -> _uiState.update {
                    it.copy(isAnalysing = false, message = "AI service isn't set up yet.")
                }
            }
        }
    }

    /** Saves a found food into the user's own library. */
    private fun addResult(candidate: FoodCandidate) {
        viewModelScope.launch {
            val preset = candidate.preset
            val result = foodRepository.savePreset(
                userId = _uiState.value.userId,
                localId = null,
                name = preset.name,
                brand = candidate.detail?.brand,
                servingDescription = candidate.detail?.servingDescription,
                calories = preset.calories,
                protein = preset.protein,
                carbs = preset.carbs,
                fat = preset.fat
            )
            _uiState.update { state ->
                state.copy(
                    // Drop it from the results so it is obvious it has been taken.
                    searchResults = if (result.isSuccess) {
                        state.searchResults.filterNot { it.selectionId == candidate.selectionId }
                    } else {
                        state.searchResults
                    },
                    message = if (result.isSuccess) {
                        "Added ${preset.name} to your foods"
                    } else {
                        "Could not add ${preset.name}"
                    }
                )
            }
        }
    }

    // ------------------------------------------------------------------ library management

    private fun delete(preset: FoodPresetEntity) {
        if (preset.isBuiltIn) {
            _uiState.update { it.copy(message = "Built-in foods can't be deleted.") }
            return
        }
        viewModelScope.launch {
            foodRepository.deletePreset(preset)
            // The row is already gone from the list via the Room flow; hold onto it so Undo
            // can recreate it.
            _uiState.update {
                it.copy(message = "Deleted ${preset.name}", lastDeleted = preset)
            }
        }
    }

    private fun undoDelete() {
        val preset = _uiState.value.lastDeleted ?: return
        viewModelScope.launch {
            foodRepository.savePreset(
                userId = _uiState.value.userId,
                localId = null,
                name = preset.name,
                brand = preset.brand,
                servingDescription = preset.servingDescription,
                calories = preset.calories,
                protein = preset.protein,
                carbs = preset.carbs,
                fat = preset.fat
            )
            _uiState.update { it.copy(message = null, lastDeleted = null) }
        }
    }

    private fun copy(preset: FoodPresetEntity) {
        viewModelScope.launch {
            val result = foodRepository.copyPresetForUser(_uiState.value.userId, preset)
            _uiState.update {
                it.copy(
                    message = if (result.isSuccess) {
                        "Copied ${preset.name} to your foods"
                    } else {
                        "Could not copy ${preset.name}"
                    }
                )
            }
        }
    }
}
