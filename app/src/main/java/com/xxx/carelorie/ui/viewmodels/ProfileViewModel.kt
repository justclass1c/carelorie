package com.xxx.carelorie.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.xxx.carelorie.data.SessionManager
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
    val userId: Int = -1,
    val name: String = "",
    val birthday: String = "",
    val gender: String = "",
    val height: String = "",
    val liftingExperience: String = "",
    val isEditMode: Boolean = false,
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val isSaveSuccess: Boolean = false,
    val isOnboarding: Boolean = false,
    val isLoggedOut: Boolean = false
)

sealed class ProfileUiEvent {
    data class LoadProfile(val userId: Int, val isOnboarding: Boolean = false) : ProfileUiEvent()
    data class NameChanged(val name: String) : ProfileUiEvent()
    data class BirthdayChanged(val birthday: String) : ProfileUiEvent()
    data class GenderChanged(val gender: String) : ProfileUiEvent()
    data class HeightChanged(val height: String) : ProfileUiEvent()
    data class ExperienceChanged(val experience: String) : ProfileUiEvent()
    object ToggleEditMode : ProfileUiEvent()
    object SaveProfile : ProfileUiEvent()
    object Logout : ProfileUiEvent()
    object ErrorConsumed : ProfileUiEvent()
    object ResetSaveStatus : ProfileUiEvent()
}

class ProfileViewModel(
    private val repository: UserRepository,
    private val sessionManager: SessionManager
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
            is ProfileUiEvent.ExperienceChanged -> _uiState.update { it.copy(liftingExperience = event.experience) }
            is ProfileUiEvent.ToggleEditMode -> _uiState.update { it.copy(isEditMode = !it.isEditMode) }
            is ProfileUiEvent.SaveProfile -> saveProfile()
            is ProfileUiEvent.Logout -> logout()
            is ProfileUiEvent.ErrorConsumed -> _uiState.update { it.copy(errorMessage = null) }
            is ProfileUiEvent.ResetSaveStatus -> _uiState.update { it.copy(isSaveSuccess = false) }
        }
    }

    private fun logout() {
        sessionManager.clearSession()
        _uiState.update { it.copy(isLoggedOut = true) }
    }

    private fun loadProfile(userId: Int, isOnboarding: Boolean) {
        _uiState.update { it.copy(userId = userId, isLoading = true, isOnboarding = isOnboarding) }
        viewModelScope.launch {
            val profile = repository.getProfile(userId)
            if (profile != null) {
                _uiState.update {
                    it.copy(
                        name = profile.name,
                        birthday = profile.birthday,
                        gender = profile.gender,
                        height = profile.height,
                        liftingExperience = profile.liftingExperience,
                        isLoading = false,
                        isEditMode = if (isOnboarding) true else it.isEditMode
                    )
                }
            } else {
                _uiState.update { it.copy(isLoading = false, isEditMode = true) }
            }
        }
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

        if (state.liftingExperience.isNotEmpty() && state.liftingExperience.toIntOrNull() == null) {
            _uiState.update { it.copy(errorMessage = "Lifting experience must be a number of years") }
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
                liftingExperience = state.liftingExperience
            )
            repository.saveProfile(profile)
            _uiState.update { it.copy(isLoading = false, isEditMode = false, isSaveSuccess = true, isOnboarding = false) }
        }
    }
}
