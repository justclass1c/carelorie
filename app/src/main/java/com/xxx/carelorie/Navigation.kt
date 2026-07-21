package com.xxx.carelorie

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.xxx.carelorie.ui.screens.Dashboard
import com.xxx.carelorie.ui.screens.LoginScreen

@Composable
fun AppNavigation(modifier: Modifier = Modifier, navController: NavHostController) {

    NavHost(
        navController = navController,
        startDestination = "login",
        modifier = modifier
    ) {
        // define destinations here
        // first defined destination is the first thing user will see once opening the app
        composable("login") { // this line to give label
            LoginScreen(
                onLoginSuccess = {
                    navController.navigate("dashboard") {
                        popUpTo("login") { inclusive = true }
                    }
                },
                navController = navController
            )
        }

        composable("dashboard") {
            Dashboard(navController = navController, username = "")
        }
    }
}
