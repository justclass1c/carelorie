package com.xxx.carelorie

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController

@Composable
fun AppNavigation(modifier: Modifier = Modifier) {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = "login",
        modifier = modifier
    ) {
        // define destinations here
        // first defined destination is the first thing user will see once opening the app
        composable("login") { // this line to give label
            LoginScreen(
                onLoginSuccess = {},
                navController = navController
            )
        }

        composable("dashboard") {
            Dashboard(navController = navController)
        }
    }
}
