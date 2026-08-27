package com.xxx.carelorie.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.xxx.carelorie.data.FoodRepository
import com.xxx.carelorie.data.nutrition.FoodCandidate
import com.xxx.carelorie.data.nutrition.FoodRecognitionService
import com.xxx.carelorie.data.nutrition.NutritionDetail
import com.xxx.carelorie.data.nutrition.NutritionSource
import com.xxx.carelorie.data.nutrition.OpenFoodFactsService
import com.xxx.carelorie.data.nutrition.RecognitionResult
import com.xxx.carelorie.data.remote.RemoteFoodPreset
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** What the results list is currently showing. */
enum class SearchMode { PRESETS, ONLINE, SCAN, AI }

data class FoodSearchUiState(
    val query: String = "",
    val mealType: String = "Breakfast",
    val presets: List<FoodCandidate> = emptyList(),
    val results: List<FoodCandidate> = emptyList(),
    /** Keyed by food name, since online results have no stable id until logged. */
    val selected: Map<String, FoodCandidate> = emptyMap(),
    val mode: SearchMode = SearchMode.PRESETS,
    val isLoading: Boolean = false,
    val isAnalysing: Boolean = false,
    val isLoggingComplete: Boolean = false,
    val message: String? = null,
    val aiIsStub: Boolean = false
) {
    val selectedCount: Int get() = selected.size
    val hasSelection: Boolean get() = selected.isNotEmpty()
    val selectedList: List<FoodCandidate> get() = selected.values.toList()

    val totalCalories: Int get() = selectedList.sumOf { it.calories }
    val totalProtein: Float get() = selectedList.sumOf { it.protein.toDouble() }.toFloat()
    val totalCarbs: Float get() = selectedList.sumOf { it.carbs.toDouble() }.toFloat()
    val totalFat: Float get() = selectedList.sumOf { it.fat.toDouble() }.toFloat()

    fun isSelected(candidate: FoodCandidate) = selected.containsKey(candidate.preset.name)
}

sealed class FoodSearchEvent {
    data class LoadPresets(val userId: Int) : FoodSearchEvent()
    data class SearchQueryChanged(val query: String) : FoodSearchEvent()
    data class MealTypeChanged(val mealType: String) : FoodSearchEvent()
    data class ToggleSelection(val candidate: FoodCandidate) : FoodSearchEvent()
    data class ChangeQuantity(val candidate: FoodCandidate, val quantity: Float) : FoodSearchEvent()
    data class BarcodeScanned(val barcode: String) : FoodSearchEvent()
    data class PhotoCaptured(val imageBase64: String) : FoodSearchEvent()
    data class LogSelected(val userId: Int) : FoodSearchEvent()
    object SearchOnline : FoodSearchEvent()
    object ClearSelection : FoodSearchEvent()
    object ShowPresets : FoodSearchEvent()
    object MessageConsumed : FoodSearchEvent()
    object ResetLogged : FoodSearchEvent()
}

class FoodSearchViewModel(
    private val foodRepository: FoodRepository,
    private val openFoodFacts: OpenFoodFactsService,
    private val recognitionService: FoodRecognitionService
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        FoodSearchUiState(aiIsStub = recognitionService is com.xxx.carelorie.data.nutrition.StubFoodRecognitionService)
    )
    val uiState: StateFlow<FoodSearchUiState> = _uiState.asStateFlow()

    private var searchJob: Job? = null

    fun onEvent(event: FoodSearchEvent) {
        when (event) {
            is FoodSearchEvent.LoadPresets -> loadPresets(event.userId)
            is FoodSearchEvent.SearchQueryChanged -> onQueryChanged(event.query)
            is FoodSearchEvent.MealTypeChanged -> _uiState.update { it.copy(mealType = event.mealType) }
            is FoodSearchEvent.ToggleSelection -> toggleSelection(event.candidate)
            is FoodSearchEvent.ChangeQuantity -> changeQuantity(event.candidate, event.quantity)
            is FoodSearchEvent.BarcodeScanned -> lookupBarcode(event.barcode)
            is FoodSearchEvent.PhotoCaptured -> analysePhoto(event.imageBase64)
            is FoodSearchEvent.LogSelected -> logSelected(event.userId)
            is FoodSearchEvent.SearchOnline -> searchOnline()
            is FoodSearchEvent.ClearSelection -> _uiState.update { it.copy(selected = emptyMap()) }
            is FoodSearchEvent.ShowPresets -> _uiState.update {
                it.copy(mode = SearchMode.PRESETS, results = filterPresets(it.presets, it.query))
            }
            is FoodSearchEvent.MessageConsumed -> _uiState.update { it.copy(message = null) }
            is FoodSearchEvent.ResetLogged -> _uiState.update { it.copy(isLoggingComplete = false) }
        }
    }

    private fun loadPresets(userId: Int) {
        _uiState.update { it.copy(isLoading = true) }
        viewModelScope.launch {
            val candidates = foodRepository.getFoodPresets(userId).map { preset ->
                FoodCandidate(
                    preset = preset,
                    detail = NutritionDetail(source = NutritionSource.APP_PRESET)
                )
            }
            _uiState.update {
                it.copy(
                    presets = candidates,
                    results = filterPresets(candidates, it.query),
                    mode = SearchMode.PRESETS,
                    isLoading = false
                )
            }
        }
    }

    /** Local filter is instant; the online search is an explicit action, not a keystroke. */
    private fun onQueryChanged(query: String) {
        _uiState.update {
            it.copy(
                query = query,
                mode = SearchMode.PRESETS,
                results = filterPresets(it.presets, query)
            )
        }
    }

    private fun filterPresets(presets: List<FoodCandidate>, query: String): List<FoodCandidate> =
        if (query.isBlank()) presets
        else presets.filter { it.preset.name.contains(query, ignoreCase = true) }

    private fun searchOnline() {
        val query = _uiState.value.query
        if (query.isBlank()) {
            _uiState.update { it.copy(message = "Type something to search for.") }
            return
        }
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, mode = SearchMode.ONLINE) }
            delay(150) // debounce rapid taps
            val results = openFoodFacts.search(query)
            _uiState.update {
                it.copy(
                    results = results,
                    isLoading = false,
                    message = if (results.isEmpty()) {
                        "Nothing found online for \"$query\"."
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
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, mode = SearchMode.SCAN) }
            val candidate = openFoodFacts.lookupBarcode(barcode)
            if (candidate == null) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        mode = SearchMode.PRESETS,
                        results = filterPresets(it.presets, it.query),
                        message = "That barcode isn't in the database. Try searching by name."
                    )
                }
            } else {
                // A scan is unambiguous, so select it straight away.
                _uiState.update {
                    it.copy(
                        results = listOf(candidate),
                        selected = it.selected + (candidate.preset.name to candidate),
                        isLoading = false,
                        message = "Found ${candidate.preset.name}"
                    )
                }
            }
        }
    }

    private fun analysePhoto(imageBase64: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isAnalysing = true, mode = SearchMode.AI) }
            when (val result = recognitionService.recognise(imageBase64)) {
                is RecognitionResult.Success -> _uiState.update { state ->
                    state.copy(
                        results = result.candidates,
                        // Pre-select everything the model saw; the user unticks what's wrong.
                        selected = state.selected + result.candidates.associateBy { it.preset.name },
                        isAnalysing = false,
                        message = "Found ${result.candidates.size} item(s). Check the amounts before logging."
                    )
                }
                is RecognitionResult.Failure -> _uiState.update {
                    it.copy(
                        isAnalysing = false,
                        mode = SearchMode.PRESETS,
                        results = filterPresets(it.presets, it.query),
                        message = result.reason
                    )
                }
                RecognitionResult.NotConfigured -> _uiState.update {
                    it.copy(
                        isAnalysing = false,
                        mode = SearchMode.PRESETS,
                        results = filterPresets(it.presets, it.query),
                        message = "Photo recognition isn't set up yet."
                    )
                }
            }
        }
    }

    private fun toggleSelection(candidate: FoodCandidate) {
        _uiState.update { state ->
            val key = candidate.preset.name
            val updated = if (state.selected.containsKey(key)) {
                state.selected - key
            } else {
                state.selected + (key to candidate)
            }
            state.copy(selected = updated)
        }
    }

    private fun changeQuantity(candidate: FoodCandidate, quantity: Float) {
        val safe = quantity.coerceIn(0.25f, 20f)
        _uiState.update { state ->
            val key = candidate.preset.name
            if (!state.selected.containsKey(key)) return@update state
            state.copy(selected = state.selected + (key to candidate.copy(quantity = safe)))
        }
    }

    private fun logSelected(userId: Int) {
        val state = _uiState.value
        if (state.selected.isEmpty()) return
        val mealType = state.mealType
        val toLog = state.selectedList

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            toLog.forEach { candidate ->
                foodRepository.logFood(userId, mealType, candidate.toLoggablePreset())
            }
            _uiState.update {
                it.copy(
                    selected = emptyMap(),
                    isLoading = false,
                    isLoggingComplete = true,
                    message = "Added ${toLog.size} item(s) to $mealType"
                )
            }
        }
    }
}
