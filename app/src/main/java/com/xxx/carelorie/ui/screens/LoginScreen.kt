package com.xxx.carelorie.ui.screens

import android.widget.Toast
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.xxx.carelorie.Routes
import com.xxx.carelorie.ui.layout.constrainedWidth
import com.xxx.carelorie.ui.theme.CarelorieTheme
import com.xxx.carelorie.ui.viewmodels.AuthUiEvent
import com.xxx.carelorie.ui.viewmodels.AuthViewModel

@Composable
fun LoginScreen(onLoginSuccess: (String) -> Unit, navController: NavController, viewModel: AuthViewModel) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    LaunchedEffect(uiState.isSuccess) {
        val userId = uiState.successUserId
        if (uiState.isSuccess && userId != null) {
            onLoginSuccess(userId)
            viewModel.onEvent(AuthUiEvent.ResetState)
        }
    }

    LaunchedEffect(uiState.errorMessage) {
        uiState.errorMessage?.let {
            Toast.makeText(context, it, Toast.LENGTH_LONG).show()
            viewModel.onEvent(AuthUiEvent.ErrorConsumed)
        }
    }

    // Centred so the form stays a readable column on a tablet instead of stretching to 10".
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
    Column(
        modifier = Modifier
            .fillMaxHeight()
            .constrainedWidth()
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .statusBarsPadding()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Welcome back.",
            style = MaterialTheme.typography.headlineMedium
        )

        Spacer(modifier = Modifier.height(32.dp))

        // email text box
        OutlinedTextField(
            value = uiState.email,
            onValueChange = { viewModel.onEvent(AuthUiEvent.EmailChanged(it)) },
            label = { Text("Email") },
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = uiState.password,
            onValueChange = { viewModel.onEvent(AuthUiEvent.PasswordChanged(it)) },
            label = { Text("Password") },
            shape = RoundedCornerShape(10.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaterialTheme.colorScheme.primary
            ),
            singleLine = true,
            visualTransformation = if (uiState.isPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
            trailingIcon = {
                val icon = if (uiState.isPasswordVisible) Icons.Filled.Visibility else Icons.Filled.VisibilityOff
                IconButton(onClick = { viewModel.onEvent(AuthUiEvent.TogglePasswordVisibility) }) {
                    Icon(imageVector = icon, contentDescription = "Toggle password visibility")
                }
            },
            modifier = Modifier.fillMaxWidth()
        )
        
        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(
                checked = uiState.isRememberMeChecked,
                onCheckedChange = { viewModel.onEvent(AuthUiEvent.RememberMeChanged(it)) }
            )
            Text(
                text = "Remember me",
                style = MaterialTheme.typography.bodyMedium
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center
        ) {
            Button(
                onClick = { navController.navigate(Routes.REGISTER) },
                shape = RoundedCornerShape(20.dp),
                contentPadding = PaddingValues(vertical = 12.dp, horizontal = 36.dp),
            ) {
                Text(text = "Register")
            }

            Spacer(modifier = Modifier.width(36.dp))

            Button(
                onClick = {
                    viewModel.onEvent(AuthUiEvent.LoginClicked)
                },
                shape = RoundedCornerShape(20.dp),
                contentPadding = PaddingValues(vertical = 12.dp, horizontal = 36.dp),
                enabled = !uiState.isLoading
            ) {
                if (uiState.isLoading) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), color = MaterialTheme.colorScheme.onPrimary)
                } else {
                    Text(text = "Login")
                }
            }
        }
    }
    }
}

@Preview(showBackground = true)
@Composable
fun LoginScreenPreview() {
    CarelorieTheme {
        Text("Login Screen Preview")
    }
}
