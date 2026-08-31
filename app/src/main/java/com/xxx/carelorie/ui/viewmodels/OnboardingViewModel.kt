package com.xxx.carelorie.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.xxx.carelorie.data.NutritionTargets
import com.xxx.carelorie.data.UserProfile
import com.xxx.carelorie.data.UserRepository
import com.xxx.carelorie.data.onboarding.OnboardingFlow
import com.xxx.carelorie.data.onboarding.OnboardingStep
import com.xxx.carelorie.data.onboarding.TdeeCalculator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDateTime

data class OnboardingUiState(
    val userId: String = "",
    /** The answers so far. Held in memory and written once, on finish or on exit. */
    val draft: UserProfile? = null,
    val stepIndex: Int = 0,
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
    val isFinished: Boolean = false,
    val errorMessage: String? = null
) {
    val step: OnboardingStep? get() = OnboardingFlow.steps.getOrNull(stepIndex)
    val progress: Float get() = OnboardingFlow.progressAt(stepIndex)
    val isFirstStep: Boolean get() = stepIndex == 0
    val isLastStep: Boolean get() = stepIndex == OnboardingFlow.steps.lastIndex

    /** Next is always available — a question can be left blank and revisited later. */
    val currentAnswered: Boolean
        get() = draft?.let { step?.isAnswered(it) } ?: false

    /** Targets computed from the answers so far, or the defaults when there are too few. */
    val previewTargets: NutritionTargets
        get() = draft?.let { TdeeCalculator.estimate(it) } ?: NutritionTargets.DEFAULT

    val estimatedTdee: Int? get() = draft?.let { TdeeCalculator.estimateTdee(it) }
}

sealed class OnboardingEvent {
    data class Start(val userId: String) : OnboardingEvent()
    data class Answer(val value: Any?) : OnboardingEvent()
    object Next : OnboardingEvent()
    object Back : OnboardingEvent()
    /** Saves whatever has been answered so far and leaves. Setup can be resumed later. */
    object SkipAll : OnboardingEvent()
    object Finish : OnboardingEvent()
    object ErrorConsumed : OnboardingEvent()
}

/**
 * Drives the setup flow.
 *
 * Answers accumulate on an in-memory draft profile and are written when the user finishes or
 * leaves — so abandoning setup halfway still keeps what was answered, and reopening it resumes
 * at the first blank question. Nothing here can block: onboarding is optional by design, and
 * every screen downstream copes with a profile that is entirely empty.
 */
class OnboardingViewModel(
    private val userRepository: UserRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(OnboardingUiState())
    val uiState: StateFlow<OnboardingUiState> = _uiState.asStateFlow()

    fun onEvent(event: OnboardingEvent) {
        when (event) {
            is OnboardingEvent.Start -> start(event.userId)
            is OnboardingEvent.Answer -> answer(event.value)
            OnboardingEvent.Next -> move(+1)
            OnboardingEvent.Back -> move(-1)
            OnboardingEvent.SkipAll -> saveAndLeave(markComplete = false)
            OnboardingEvent.Finish -> saveAndLeave(markComplete = true)
            OnboardingEvent.ErrorConsumed -> _uiState.update { it.copy(errorMessage = null) }
        }
    }

    private fun start(userId: String) {
        if (_uiState.value.userId == userId && _uiState.value.draft != null) return
        viewModelScope.launch {
            val existing = try {
                userRepository.getProfile(userId)
            } catch (e: Exception) {
                null
            } ?: UserProfile(userId = userId)

            _uiState.update {
                it.copy(
                    userId = userId,
                    draft = existing,
                    // Resume where they stopped rather than making them page through
                    // answers they already gave.
                    stepIndex = OnboardingFlow.resumeIndex(existing),
                    isLoading = false
                )
            }
        }
    }

    private fun answer(value: Any?) {
        val state = _uiState.value
        val draft = state.draft ?: return
        val step = state.step ?: return

        val updated = when (step) {
            is OnboardingStep.Options -> (value as? String)?.let { step.write(draft, it) }
            is OnboardingStep.Measure -> step.write(draft, value as? Float)
            is OnboardingStep.BirthDate -> (value as? String)?.let { draft.copy(birthday = it) }
            is OnboardingStep.Name -> (value as? String)?.let { draft.copy(name = it) }
            is OnboardingStep.Summary -> draft
        } ?: return

        // Keep the cached expenditure in step with the answers it was derived from, so the
        // summary screens and the AI briefing never quote a stale number.
        val withTdee = updated.copy(estimatedTdee = TdeeCalculator.estimateTdee(updated))
        _uiState.update { it.copy(draft = withTdee) }
    }

    private fun move(delta: Int) {
        _uiState.update {
            it.copy(stepIndex = (it.stepIndex + delta).coerceIn(0, OnboardingFlow.steps.lastIndex))
        }
    }

    /**
     * Writes the draft and signals the screen to leave.
     *
     * [markComplete] stamps `onboardingCompletedAt`, which is what stops the app prompting the
     * user to finish setting up. Skipping deliberately leaves it null.
     */
    private fun saveAndLeave(markComplete: Boolean) {
        val state = _uiState.value
        val draft = state.draft
        if (draft == null) {
            _uiState.update { it.copy(isFinished = true) }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true) }
            try {
                val targets = TdeeCalculator.estimate(draft)
                val finished = draft.copy(
                    estimatedTdee = TdeeCalculator.estimateTdee(draft),
                    onboardingCompletedAt = if (markComplete) {
                        LocalDateTime.now().toString()
                    } else {
                        draft.onboardingCompletedAt
                    },
                    // Only overwrite the macro targets when there was enough to compute them.
                    // A half-finished setup leaves the existing limits alone rather than
                    // resetting someone's numbers to the defaults.
                    calorieLimit = targets?.calories ?: draft.calorieLimit,
                    proteinLimit = targets?.proteinGrams ?: draft.proteinLimit,
                    carbsLimit = targets?.carbsGrams ?: draft.carbsLimit,
                    fatLimit = targets?.fatGrams ?: draft.fatLimit
                )
                userRepository.saveProfile(finished)
                _uiState.update { it.copy(draft = finished, isSaving = false, isFinished = true) }
            } catch (e: Exception) {
                // Losing the answers would be worse than a failed sync, so leave anyway —
                // saveProfile writes to Room before it tries the network.
                _uiState.update {
                    it.copy(
                        isSaving = false,
                        isFinished = true,
                        errorMessage = "Saved on this device; we'll sync your plan later."
                    )
                }
            }
        }
    }
}
