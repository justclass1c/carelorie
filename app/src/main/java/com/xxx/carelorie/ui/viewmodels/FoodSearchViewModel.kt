package com.xxx.carelorie.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.xxx.carelorie.data.FoodRepository
import com.xxx.carelorie.data.local.toRemote
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
import java.time.LocalDate

/** What the results list is currently showing. */
enum class SearchMode { PRESETS, ONLINE, SCAN, AI }

/** Which slice of the local food library the preset list is showing. */
enum class PresetFilter(val label: String) { ALL("All"), MINE("Mine") }

data class FoodSearchUiState(
    val query: String = "",
    val mealType: String = "Breakfast",
    /** The day being logged into — the food log can open this screen for a past date. */
    val logDate: LocalDate = LocalDate.now(),
    val presets: List<FoodCandidate> = emptyList(),
    val results: List<FoodCandidate> = emptyList(),
    /** Keyed by selectionId for uniqueness. */
    val selected: Map<String, FoodCandidate> = emptyMap(),
    val mode: SearchMode = SearchMode.PRESETS,
    val presetFilter: PresetFilter = PresetFilter.ALL,
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

    fun isSelected(candidate: FoodCandidate) = selected.containsKey(candidate.selectionId)
}

sealed class FoodSearchEvent {
    data class LoadPresets(val userId: String) : FoodSearchEvent()
    data class SearchQueryChanged(val query: String) : FoodSearchEvent()
    data class MealTypeChanged(val mealType: String) : FoodSearchEvent()
    data class LogDateChanged(val date: LocalDate) : FoodSearchEvent()
    data class ToggleSelection(val candidate: FoodCandidate) : FoodSearchEvent()
    data class ChangeQuantity(val candidate: FoodCandidate, val quantity: Float) : FoodSearchEvent()
    data class BarcodeScanned(val barcode: String) : FoodSearchEvent()
    data class PhotoCaptured(val imageBase64: String) : FoodSearchEvent()
    /** The picked image could not be read — surfaced rather than silently doing nothing. */
    data class PhotoFailed(val reason: String) : FoodSearchEvent()
    data class PresetFilterChanged(val filter: PresetFilter) : FoodSearchEvent()
    object AiSearch : FoodSearchEvent()
    data class LogSelected(val userId: String) : FoodSearchEvent()
    data class SaveSelectedAsPresets(val userId: String) : FoodSearchEvent()
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
            is FoodSearchEvent.LogDateChanged -> _uiState.update { it.copy(logDate = event.date) }
            is FoodSearchEvent.ToggleSelection -> toggleSelection(event.candidate)
            is FoodSearchEvent.ChangeQuantity -> changeQuantity(event.candidate, event.quantity)
            is FoodSearchEvent.BarcodeScanned -> lookupBarcode(event.barcode)
            is FoodSearchEvent.PhotoCaptured -> analysePhoto(event.imageBase64)
            is FoodSearchEvent.PhotoFailed ->
                _uiState.update { it.copy(isAnalysing = false, message = event.reason) }
            is FoodSearchEvent.PresetFilterChanged -> _uiState.update {
                // Changing the filter always returns to the local library, since the filter
                // has no meaning over online or AI results.
                it.copy(
                    presetFilter = event.filter,
                    mode = SearchMode.PRESETS,
                    results = filterPresets(it.presets, it.query, event.filter)
                )
            }
            is FoodSearchEvent.AiSearch -> aiSearch()
            is FoodSearchEvent.LogSelected -> logSelected(event.userId)
            is FoodSearchEvent.SaveSelectedAsPresets -> saveSelectedAsPresets(event.userId)
            is FoodSearchEvent.SearchOnline -> searchOnline()
            is FoodSearchEvent.ClearSelection -> _uiState.update { it.copy(selected = emptyMap()) }
            is FoodSearchEvent.ShowPresets -> _uiState.update {
                it.copy(mode = SearchMode.PRESETS, results = filterPresets(it.presets, it.query))
            }
            is FoodSearchEvent.MessageConsumed -> _uiState.update { it.copy(message = null) }
            is FoodSearchEvent.ResetLogged -> _uiState.update { it.copy(isLoggingComplete = false) }
        }
    }

    private var presetsJob: Job? = null

    /**
     * Observes the local preset table, so a food saved in the editor appears here at once and
     * the list still works with no connection. The Supabase pull is a background refresh on top.
     */
    private fun loadPresets(userId: String) {
        _uiState.update { it.copy(isLoading = true) }

        presetsJob?.cancel()
        presetsJob = viewModelScope.launch {
            foodRepository.observePresets(userId).collect { entities ->
                val candidates = entities.map { entity ->
                    FoodCandidate(
                        preset = entity.toRemote(),
                        detail = NutritionDetail(
                            servingDescription = entity.servingDescription,
                            brand = entity.brand,
                            source = if (entity.isBuiltIn) {
                                NutritionSource.APP_PRESET
                            } else {
                                NutritionSource.USER_ENTERED
                            }
                        )
                    )
                }
                _uiState.update {
                    it.copy(
                        presets = candidates,
                        results = if (it.mode == SearchMode.PRESETS) {
                            filterPresets(candidates, it.query)
                        } else {
                            it.results
                        },
                        isLoading = false
                    )
                }
            }
        }

        viewModelScope.launch { foodRepository.refreshPresets(userId) }
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

    private fun filterPresets(
        presets: List<FoodCandidate>,
        query: String,
        filter: PresetFilter = _uiState.value.presetFilter
    ): List<FoodCandidate> = presets
        // A built-in preset has no owner; anything with a userId was created by this user.
        .filter { filter == PresetFilter.ALL || it.preset.userId != null }
        .filter { query.isBlank() || it.preset.name.contains(query, ignoreCase = true) }

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
                        "No results found online for \"$query\". Check your spelling or try another term."
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
                        selected = it.selected + (candidate.selectionId to candidate),
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
            handleRecognitionResult(recognitionService.recognise(imageBase64))
        }
    }

    private fun aiSearch() {
        val query = _uiState.value.query
        if (query.isBlank()) {
            _uiState.update { it.copy(message = "Type a food name first.") }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isAnalysing = true, mode = SearchMode.AI) }
            
            // 1. Gather context from online search first
            val onlineResults = try {
                openFoodFacts.search(query).take(5)
            } catch (_: Exception) {
                emptyList()
            }
            
            val contextText = if (onlineResults.isNotEmpty()) {
                "Search results from food database:\n" + onlineResults.joinToString("\n") { c ->
                    "- ${c.preset.name}: ${c.calories}kcal, P:${c.protein}g, C:${c.carbs}g, F:${c.fat}g."
                }
            } else "No direct database match found."

            // 2. Ask AI to estimate, giving it the online results as a hint
            val aiResult = recognitionService.estimateNutrition(query, contextText)
            
            // 3. If AI fails but OpenFoodFacts returned matches, show those instead
            if (aiResult is RecognitionResult.Failure && onlineResults.isNotEmpty()) {
                _uiState.update {
                    it.copy(
                        isAnalysing = false,
                        mode = SearchMode.ONLINE,
                        results = onlineResults,
                        message = "AI couldn't estimate; showing online results for \"$query\"."
                    )
                }
            } else {
                handleRecognitionResult(aiResult)
            }
        }
    }

    private fun handleRecognitionResult(result: RecognitionResult) {
        when (result) {
            is RecognitionResult.Success -> _uiState.update { state ->
                state.copy(
                    results = result.candidates,
                    // Pre-select everything (should be one item for text search)
                    selected = state.selected + result.candidates.associateBy { it.selectionId },
                    isAnalysing = false,
                    message = if (result.candidates.isNotEmpty()) "Found ${result.candidates[0].preset.name}" else null
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
                    message = "AI is not configured. Add DEEPSEEK_API_KEY or GEMINI_API_KEY " +
                        "to local.properties and rebuild."
                )
            }
        }
    }

    private fun toggleSelection(candidate: FoodCandidate) {
        _uiState.update { state ->
            val key = candidate.selectionId
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
            val key = candidate.selectionId
            if (!state.selected.containsKey(key)) return@update state
            val updatedSelection = state.selected + (key to candidate.copy(quantity = safe))
            
            // Also update the quantity in the results list so the UI reflects the change
            val updatedResults = state.results.map {
                if (it.selectionId == key) it.copy(quantity = safe) else it
            }
            
            state.copy(selected = updatedSelection, results = updatedResults)
        }
    }

    /**
     * Keeps the selected foods in the user's library instead of logging them.
     *
     * This is what the Review screen's "Save Preset" button in AI mode is for — an AI estimate
     * is worth keeping so the next time the food is eaten it takes one tap and no API call.
     * Quantity is deliberately ignored: a saved food is a single serving, and the log applies
     * the multiplier.
     */
    private fun saveSelectedAsPresets(userId: String) {
        val toSave = _uiState.value.selectedList
        if (toSave.isEmpty()) return

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            var saved = 0
            for (candidate in toSave) {
                val preset = candidate.preset
                val result = foodRepository.savePreset(
                    userId = userId,
                    localId = null,
                    name = preset.name,
                    brand = candidate.detail?.brand,
                    servingDescription = candidate.detail?.servingDescription,
                    calories = preset.calories,
                    protein = preset.protein,
                    carbs = preset.carbs,
                    fat = preset.fat
                )
                if (result.isSuccess) saved++
            }
            _uiState.update {
                it.copy(
                    selected = emptyMap(),
                    isLoading = false,
                    isLoggingComplete = true,
                    message = if (saved > 0) {
                        "Saved $saved item(s) to Food Query"
                    } else {
                        "Could not save that food."
                    }
                )
            }
        }
    }

    private fun logSelected(userId: String) {
        val state = _uiState.value
        if (state.selected.isEmpty()) return
        val mealType = state.mealType
        val logDate = state.logDate
        val toLog = state.selectedList

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            toLog.forEach { candidate ->
                foodRepository.logFood(
                    userId = userId,
                    mealType = mealType,
                    food = candidate.toLoggablePreset(),
                    quantity = candidate.quantity,
                    date = logDate,
                    detail = candidate.detail
                )
            }
            _uiState.update {
                it.copy(
                    selected = emptyMap(),
                    isLoading = false,
                    isLoggingComplete = true,
                    message = if (logDate == LocalDate.now()) {
                        "Added ${toLog.size} item(s) to $mealType"
                    } else {
                        "Added ${toLog.size} item(s) to $mealType on $logDate"
                    }
                )
            }
        }
    }
}
