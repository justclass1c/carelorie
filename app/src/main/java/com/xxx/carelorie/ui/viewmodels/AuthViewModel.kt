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
    val isRememberMeChecked: Boolean = false,
    val forgot: ForgotPasswordState = ForgotPasswordState()
)

data class ForgotPasswordState(
    val visible: Boolean = false,
    val step: Step = Step.EMAIL,
    val email: String = "",
    val recoveryKey: String = "",
    val newPassword: String = "",
    val confirmPassword: String = "",
    val passwordVisible: Boolean = false,
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val success: Boolean = false
) {
    enum class Step { EMAIL, RECOVERY_KEY, NEW_PASSWORD }
}

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

    object ForgotPasswordClicked : AuthUiEvent()
    object ForgotDismissed : AuthUiEvent()
    data class ForgotEmailChanged(val email: String) : AuthUiEvent()
    object ForgotContinueClicked : AuthUiEvent()
    data class ForgotRecoveryKeyChanged(val key: String) : AuthUiEvent()
    object VerifyRecoveryKeyClicked : AuthUiEvent()
    data class ForgotNewPasswordChanged(val password: String) : AuthUiEvent()
    data class ForgotConfirmPasswordChanged(val password: String) : AuthUiEvent()
    object ToggleForgotPasswordVisibility : AuthUiEvent()
    object ResetPasswordClicked : AuthUiEvent()
    object ForgotErrorConsumed : AuthUiEvent()
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
            is AuthUiEvent.ForgotPasswordClicked -> {
                _uiState.update {
                    it.copy(forgot = ForgotPasswordState(visible = true, email = it.email))
                }
            }
            is AuthUiEvent.ForgotDismissed -> {
                _uiState.update { it.copy(forgot = ForgotPasswordState()) }
            }
            is AuthUiEvent.ForgotEmailChanged -> {
                _uiState.update { it.copy(forgot = it.forgot.copy(email = event.email, errorMessage = null)) }
            }
            is AuthUiEvent.ForgotContinueClicked -> continueForgot()
            is AuthUiEvent.ForgotRecoveryKeyChanged -> {
                _uiState.update { it.copy(forgot = it.forgot.copy(recoveryKey = event.key, errorMessage = null)) }
            }
            is AuthUiEvent.VerifyRecoveryKeyClicked -> verifyRecoveryKey()
            is AuthUiEvent.ForgotNewPasswordChanged -> {
                _uiState.update { it.copy(forgot = it.forgot.copy(newPassword = event.password, errorMessage = null)) }
            }
            is AuthUiEvent.ForgotConfirmPasswordChanged -> {
                _uiState.update { it.copy(forgot = it.forgot.copy(confirmPassword = event.password, errorMessage = null)) }
            }
            is AuthUiEvent.ToggleForgotPasswordVisibility -> {
                _uiState.update { it.copy(forgot = it.forgot.copy(passwordVisible = !it.forgot.passwordVisible)) }
            }
            is AuthUiEvent.ResetPasswordClicked -> resetPassword()
            is AuthUiEvent.ForgotErrorConsumed -> {
                _uiState.update { it.copy(forgot = it.forgot.copy(errorMessage = null)) }
            }
        }
    }

    private fun continueForgot() {
        val forgot = _uiState.value.forgot
        if (!validateEmail(forgot.email)) {
            _uiState.update { it.copy(forgot = it.forgot.copy(errorMessage = "Enter a valid email address")) }
            return
        }
        _uiState.update {
            it.copy(forgot = it.forgot.copy(step = ForgotPasswordState.Step.RECOVERY_KEY, errorMessage = null))
        }
    }

    private fun verifyRecoveryKey() {
        val forgot = _uiState.value.forgot
        if (forgot.recoveryKey.isBlank()) {
            _uiState.update { it.copy(forgot = it.forgot.copy(errorMessage = "Enter your recovery key")) }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(forgot = it.forgot.copy(isLoading = true, errorMessage = null)) }
            val ok = repository.verifyRecoveryKey(forgot.email, forgot.recoveryKey.trim())
            if (ok) {
                _uiState.update {
                    it.copy(forgot = it.forgot.copy(isLoading = false, step = ForgotPasswordState.Step.NEW_PASSWORD, errorMessage = null))
                }
            } else {
                _uiState.update {
                    it.copy(forgot = it.forgot.copy(isLoading = false, errorMessage = "Incorrect recovery key, or this account has none."))
                }
            }
        }
    }

    private fun resetPassword() {
        val forgot = _uiState.value.forgot
        val validation = validateNewPassword(forgot.newPassword, forgot.confirmPassword)
        if (validation != null) {
            _uiState.update { it.copy(forgot = it.forgot.copy(errorMessage = validation)) }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(forgot = it.forgot.copy(isLoading = true, errorMessage = null)) }
            val ok = repository.resetPassword(forgot.email, forgot.newPassword)
            _uiState.update {
                if (ok) {
                    it.copy(forgot = it.forgot.copy(isLoading = false, success = true, visible = false))
                } else {
                    it.copy(forgot = it.forgot.copy(isLoading = false, errorMessage = "Could not reset password. Try again."))
                }
            }
        }
    }

    /** Returns an error message when [password] fails the register rules, else null. */
    private fun validateNewPassword(password: String, confirm: String): String? {
        if (password.length < 6) return "Password must be at least 6 characters"
        if (!password.any { it.isUpperCase() }) return "Password must contain at least one uppercase letter"
        if (!password.any { !it.isLetterOrDigit() }) return "Password must contain at least one special character"
        if (password != confirm) return "Passwords do not match"
        return null
    }

    private fun register() {
        val email = _uiState.value.email.trim()
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
            // Emails are compared case-insensitively, so "Foo@bar.com" and "foo@bar.com" are the
            // same account. Check the exact form and, if that differs, the lowercase form.
            val existingUser = repository.getUserByEmail(email)
                ?: if (email != email.lowercase()) repository.getUserByEmail(email.lowercase()) else null
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
            // The repository owns the comparison — passwords are hashed, and the ViewModel has
            // no business handling the stored value.
            val user = repository.authenticate(email, password)
            if (user != null) {
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
