package com.xxx.carelorie.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.xxx.carelorie.data.SessionManager
import com.xxx.carelorie.data.ThemeManager
import com.xxx.carelorie.data.UserProfile
import com.xxx.carelorie.data.UserRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.util.regex.Pattern

data class ProfileUiState(
    val userId: String = "",
    val name: String = "",
    val birthday: String = "",
    val gender: String = "",
    val height: String = "",
    val weight: String = "",
    val liftingExperience: String = "",
    val theme: String = "system",
    val calorieLimit: String = "",
    val proteinLimit: String = "",
    val carbsLimit: String = "",
    val fatLimit: String = "",
    val isEditMode: Boolean = false,
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val isSaveSuccess: Boolean = false,
    val isOnboarding: Boolean = false,
    val isLoggedOut: Boolean = false
)

sealed class ProfileUiEvent {
    data class LoadProfile(val userId: String, val isOnboarding: Boolean = false) : ProfileUiEvent()
    data class NameChanged(val name: String) : ProfileUiEvent()
    data class BirthdayChanged(val birthday: String) : ProfileUiEvent()
    data class GenderChanged(val gender: String) : ProfileUiEvent()
    data class HeightChanged(val height: String) : ProfileUiEvent()
    data class WeightChanged(val weight: String) : ProfileUiEvent()
    data class ExperienceChanged(val experience: String) : ProfileUiEvent()
    data class ThemeChanged(val theme: String) : ProfileUiEvent()
    data class CalorieLimitChanged(val value: String) : ProfileUiEvent()
    data class ProteinLimitChanged(val value: String) : ProfileUiEvent()
    data class CarbsLimitChanged(val value: String) : ProfileUiEvent()
    data class FatLimitChanged(val value: String) : ProfileUiEvent()
    data class CancelEdit(val userId: String) : ProfileUiEvent()
    object ToggleEditMode : ProfileUiEvent()
    object SaveProfile : ProfileUiEvent()
    object Logout : ProfileUiEvent()
    object DeleteAccount : ProfileUiEvent()
    object ErrorConsumed : ProfileUiEvent()
    object ResetSaveStatus : ProfileUiEvent()
}

class ProfileViewModel(
    private val repository: UserRepository,
    private val sessionManager: SessionManager,
    private val themeManager: ThemeManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(ProfileUiState())
    val uiState: StateFlow<ProfileUiState> = _uiState.asStateFlow()

    fun onEvent(event: ProfileUiEvent) {
        when (event) {
            is ProfileUiEvent.LoadProfile -> loadProfile(event.userId, event.isOnboarding)
            is ProfileUiEvent.NameChanged -> _uiState.update { it.copy(name = event.name) }
            is ProfileUiEvent.BirthdayChanged -> _uiState.update { it.copy(birthday = event.birthday) }
            is ProfileUiEvent.GenderChanged -> _uiState.update { it.copy(gender = event.gender) }
            is ProfileUiEvent.HeightChanged -> _uiState.update { it.copy(height = event.height) }
            is ProfileUiEvent.WeightChanged -> _uiState.update { it.copy(weight = event.weight) }
            is ProfileUiEvent.ExperienceChanged -> _uiState.update { it.copy(liftingExperience = event.experience) }
            is ProfileUiEvent.ThemeChanged -> onThemeChanged(event.theme)
            is ProfileUiEvent.CalorieLimitChanged -> _uiState.update { it.copy(calorieLimit = event.value) }
            is ProfileUiEvent.ProteinLimitChanged -> _uiState.update { it.copy(proteinLimit = event.value) }
            is ProfileUiEvent.CarbsLimitChanged -> _uiState.update { it.copy(carbsLimit = event.value) }
            is ProfileUiEvent.FatLimitChanged -> _uiState.update { it.copy(fatLimit = event.value) }
            is ProfileUiEvent.CancelEdit -> cancelEdit(event.userId)
            is ProfileUiEvent.ToggleEditMode -> _uiState.update { it.copy(isEditMode = !it.isEditMode) }
            is ProfileUiEvent.SaveProfile -> saveProfile()
            is ProfileUiEvent.Logout -> logout()
            is ProfileUiEvent.DeleteAccount -> deleteAccount()
            is ProfileUiEvent.ErrorConsumed -> _uiState.update { it.copy(errorMessage = null) }
            is ProfileUiEvent.ResetSaveStatus -> _uiState.update { it.copy(isSaveSuccess = false) }
        }
    }

    private fun logout() {
        sessionManager.clearSession()
        _uiState.update { it.copy(isLoggedOut = true) }
    }

    private fun deleteAccount() {
        viewModelScope.launch {
            repository.deleteAccount(_uiState.value.userId)
            sessionManager.clearSession()
            _uiState.update { it.copy(isLoggedOut = true) }
        }
    }

    private fun loadProfile(userId: String, isOnboarding: Boolean) {
        _uiState.update { it.copy(userId = userId, isLoading = true, isOnboarding = isOnboarding) }
        viewModelScope.launch {
            val profile = repository.getProfile(userId)
            if (profile != null) {
                themeManager.setThemeMode(profile.theme)
                _uiState.update {
                    it.copy(
                        name = profile.name,
                        birthday = profile.birthday,
                        gender = profile.gender,
                        height = profile.height,
                        weight = profile.weight?.toString() ?: "",
                        liftingExperience = profile.liftingExperience,
                        theme = profile.theme,
                        calorieLimit = profile.calorieLimit.toString(),
                        proteinLimit = formatFloat(profile.proteinLimit),
                        carbsLimit = formatFloat(profile.carbsLimit),
                        fatLimit = formatFloat(profile.fatLimit),
                        isLoading = false,
                        isEditMode = if (isOnboarding) true else it.isEditMode
                    )
                }
            } else {
                _uiState.update { it.copy(isLoading = false, isEditMode = true) }
            }
        }
    }

    private fun onThemeChanged(theme: String) {
        _uiState.update { it.copy(theme = theme) }
        themeManager.setThemeMode(theme)
        viewModelScope.launch {
            repository.updateTheme(_uiState.value.userId, theme)
        }
    }

    private fun formatFloat(value: Float): String =
        if (value == value.toInt().toFloat()) value.toInt().toString() else value.toString()

    private fun cancelEdit(userId: String) {
        // Exit edit mode and reload the saved profile so any unsaved changes are discarded.
        _uiState.update { it.copy(isEditMode = false) }
        loadProfile(userId, isOnboarding = false)
    }

    private val birthdayPattern = Pattern.compile("^(0[1-9]|[12][0-9]|3[01])/(0[1-9]|1[0-2])/(19|20)\\d{2}$")

    private fun saveProfile() {
        val state = _uiState.value
        if (state.name.isBlank()) {
            _uiState.update { it.copy(errorMessage = "Name is required") }
            return
        }

        if (state.birthday.isNotEmpty()) {
            if (!birthdayPattern.matcher(state.birthday).matches()) {
                _uiState.update { it.copy(errorMessage = "Invalid birthday format (dd/mm/yyyy)") }
                return
            }
            val year = state.birthday.split("/").last().toIntOrNull()
            val currentYear = LocalDate.now().year
            val minYear = currentYear - 100
            if (year != null && year < minYear) {
                _uiState.update { it.copy(errorMessage = "Year should not be less than $minYear") }
                return
            }
        }
        
        // Simple numeric check for height if not empty
        if (state.height.isNotEmpty() && state.height.toDoubleOrNull() == null) {
             _uiState.update { it.copy(errorMessage = "Height must be a number") }
            return
        }

        if (state.weight.isNotEmpty() && state.weight.toDoubleOrNull() == null) {
            _uiState.update { it.copy(errorMessage = "Weight must be a number") }
            return
        }

        if (state.liftingExperience.isNotEmpty() && state.liftingExperience.toIntOrNull() == null) {
            _uiState.update { it.copy(errorMessage = "Lifting experience must be a number of years") }
            return
        }

        if (state.calorieLimit.isNotEmpty() && state.calorieLimit.toIntOrNull() == null) {
            _uiState.update { it.copy(errorMessage = "Calorie limit must be a number") }
            return
        }
        if (state.proteinLimit.isNotEmpty() && state.proteinLimit.toFloatOrNull() == null) {
            _uiState.update { it.copy(errorMessage = "Protein limit must be a number") }
            return
        }
        if (state.carbsLimit.isNotEmpty() && state.carbsLimit.toFloatOrNull() == null) {
            _uiState.update { it.copy(errorMessage = "Carbs limit must be a number") }
            return
        }
        if (state.fatLimit.isNotEmpty() && state.fatLimit.toFloatOrNull() == null) {
            _uiState.update { it.copy(errorMessage = "Fat limit must be a number") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val profile = UserProfile(
                userId = state.userId,
                name = state.name,
                birthday = state.birthday,
                gender = state.gender,
                height = state.height,
                weight = state.weight.toFloatOrNull(),
                liftingExperience = state.liftingExperience,
                theme = state.theme,
                calorieLimit = state.calorieLimit.toIntOrNull() ?: 2000,
                proteinLimit = state.proteinLimit.toFloatOrNull() ?: 120f,
                carbsLimit = state.carbsLimit.toFloatOrNull() ?: 200f,
                fatLimit = state.fatLimit.toFloatOrNull() ?: 65f
            )
            repository.saveProfile(profile)
            _uiState.update { it.copy(isLoading = false, isEditMode = false, isSaveSuccess = true, isOnboarding = false) }
        }
    }
}
