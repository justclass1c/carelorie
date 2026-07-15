package com.xxx.carelorie

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.xxx.carelorie.ui.theme.CarelorieTheme

@Composable
fun LoginScreen(onLoginSuccess: () -> Unit, navController: NavController) {
    // create mutable states for two variables
    var email by remember { (mutableStateOf("")) }
    var password by remember { (mutableStateOf("")) }

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
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
            value = email, // which variable is assigned for input
            onValueChange = { email = it },
            label = { Text("Email") },
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = password, // which variable is assigned for input
            onValueChange = { password = it },
            label = { Text("Password") },
            shape = RoundedCornerShape(10.dp),
            colors = OutlinedTextFieldDefaults.colors(
                // properties here
                focusedBorderColor = MaterialTheme.colorScheme.primary
            ),
            singleLine = true, // lock text into using only one line
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth()
        )
        
        Spacer(modifier = Modifier.height(24.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center
        ) {
            Button(
                onClick = { }, // navigate to home screen
                shape = RoundedCornerShape(20.dp), // size here determines border radius / rounding
                 //colors = ButtonColors(),
                contentPadding = PaddingValues(vertical = 12.dp, horizontal = 36.dp),
            ) {
                Text(text = "Register")
            }

            Spacer(modifier = Modifier.width(36.dp))

            Button(
                onClick = {
                    navController.navigate("dashboard")
                }, // navigate to home screen
                shape = RoundedCornerShape(20.dp),
                contentPadding = PaddingValues(vertical = 12.dp, horizontal = 36.dp),
            ) {
                Text(text = "Login")
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun LoginScreenPreview() {
    CarelorieTheme {
        LoginScreen(onLoginSuccess = {}, navController = NavController(context = LocalContext.current))
    }
}