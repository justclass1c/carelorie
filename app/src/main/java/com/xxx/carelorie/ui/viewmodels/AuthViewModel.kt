package com.xxx.carelorie.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.xxx.carelorie.data.SessionManager
import com.xxx.carelorie.data.User
import com.xxx.carelorie.data.UserRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.regex.Pattern

data class AuthUiState(
    val email: String = "",
    val password: String = "",
    val confirmPassword: String = "",
    val isPasswordVisible: Boolean = false,
    val isConfirmPasswordVisible: Boolean = false,
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val isSuccess: Boolean = false,
    val successUserId: String? = null,
    val isRememberMeChecked: Boolean = false
)

sealed class AuthUiEvent {
    data class EmailChanged(val email: String) : AuthUiEvent()
    data class PasswordChanged(val password: String) : AuthUiEvent()
    data class ConfirmPasswordChanged(val password: String) : AuthUiEvent()
    object TogglePasswordVisibility : AuthUiEvent()
    object ToggleConfirmPasswordVisibility : AuthUiEvent()
    object LoginClicked : AuthUiEvent()
    object RegisterClicked : AuthUiEvent()
    object ErrorConsumed : AuthUiEvent()
    object ResetState : AuthUiEvent()
    data class RememberMeChanged(val isChecked: Boolean) : AuthUiEvent()
}

class AuthViewModel(
    private val repository: UserRepository,
    private val sessionManager: SessionManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(AuthUiState())
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

    private val emailPattern = Pattern.compile(
        "^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,6}$"
    )

    fun onEvent(event: AuthUiEvent) {
        when (event) {
            is AuthUiEvent.EmailChanged -> {
                _uiState.update { it.copy(email = event.email) }
            }
            is AuthUiEvent.PasswordChanged -> {
                _uiState.update { it.copy(password = event.password) }
            }
            is AuthUiEvent.ConfirmPasswordChanged -> {
                _uiState.update { it.copy(confirmPassword = event.password) }
            }
            is AuthUiEvent.TogglePasswordVisibility -> {
                _uiState.update { it.copy(isPasswordVisible = !it.isPasswordVisible) }
            }
            is AuthUiEvent.ToggleConfirmPasswordVisibility -> {
                _uiState.update { it.copy(isConfirmPasswordVisible = !it.isConfirmPasswordVisible) }
            }
            is AuthUiEvent.LoginClicked -> login()
            is AuthUiEvent.RegisterClicked -> register()
            is AuthUiEvent.ErrorConsumed -> {
                _uiState.update { it.copy(errorMessage = null) }
            }
            is AuthUiEvent.ResetState -> {
                _uiState.value = AuthUiState()
            }
            is AuthUiEvent.RememberMeChanged -> {
                _uiState.update { it.copy(isRememberMeChecked = event.isChecked) }
            }
        }
    }

    private fun register() {
        val email = _uiState.value.email
        val password = _uiState.value.password
        val confirmPassword = _uiState.value.confirmPassword

        if (!validateEmail(email)) {
            _uiState.update { it.copy(errorMessage = "Invalid email format") }
            return
        }
        if (password.length < 6) {
            _uiState.update { it.copy(errorMessage = "Password must be at least 6 characters") }
            return
        }
        if (!password.any { it.isUpperCase() }) {
            _uiState.update { it.copy(errorMessage = "Password must contain at least one uppercase letter") }
            return
        }
        if (!password.any { !it.isLetterOrDigit() }) {
            _uiState.update { it.copy(errorMessage = "Password must contain at least one special character") }
            return
        }
        if (password != confirmPassword) {
            _uiState.update { it.copy(errorMessage = "Passwords do not match") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val existingUser = repository.getUserByEmail(email)
            if (existingUser != null) {
                _uiState.update { it.copy(isLoading = false, errorMessage = "User already exists") }
                return@launch
            }

            val result = repository.registerUser(User(email = email, password = password))
            if (result.isSuccess) {
                val userId = result.getOrNull()
                if (userId != null) {
                    // Registration counts as a fresh login; we remember by default here
                    // to avoid confusing the "Remember Me" toggle which is on the login screen.
                    sessionManager.saveUserId(userId, rememberMe = true)
                }
                _uiState.update { it.copy(isLoading = false, isSuccess = true, successUserId = userId) }
            } else {
                _uiState.update { 
                    it.copy(
                        isLoading = false, 
                        errorMessage = "Registration failed: ${result.exceptionOrNull()?.message}"
                    ) 
                }
            }
        }
    }

    private fun login() {
        val email = _uiState.value.email
        val password = _uiState.value.password

        if (email.isEmpty() || password.isEmpty()) {
            _uiState.update { it.copy(errorMessage = "Fields cannot be empty") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val user = repository.getUserByEmail(email)
            if (user != null && user.password == password) {
                sessionManager.saveUserId(user.userId, _uiState.value.isRememberMeChecked)
                _uiState.update { it.copy(isLoading = false, isSuccess = true, successUserId = user.userId) }
            } else {
                _uiState.update { it.copy(isLoading = false, errorMessage = "Invalid email or password") }
            }
        }
    }

    private fun validateEmail(email: String): Boolean {
        return emailPattern.matcher(email).matches()
    }
}
