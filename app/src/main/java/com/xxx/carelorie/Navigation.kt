package com.xxx.carelorie

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.xxx.carelorie.ui.screens.Dashboard
import com.xxx.carelorie.ui.screens.LoginScreen
import com.xxx.carelorie.ui.screens.Profile
import com.xxx.carelorie.ui.screens.RegisterScreen
import com.xxx.carelorie.ui.viewmodels.AuthViewModel
import com.xxx.carelorie.ui.viewmodels.DashboardViewModel
import com.xxx.carelorie.ui.viewmodels.ProfileViewModel

@Composable
fun AppNavigation(
    modifier: Modifier = Modifier,
    navController: NavHostController,
    authViewModel: AuthViewModel,
    profileViewModel: ProfileViewModel,
    dashboardViewModel: DashboardViewModel
) {
    var currentUserId by rememberSaveable { mutableIntStateOf(-1) }

    NavHost(
        navController = navController,
        startDestination = "login",
        modifier = modifier
    ) {
        composable("login") {
            LoginScreen(
                onLoginSuccess = { userId ->
                    currentUserId = userId
                    navController.navigate("dashboard") {
                        popUpTo("login") { inclusive = true }
                    }
                },
                navController = navController,
                viewModel = authViewModel
            )
        }

        composable("register") {
            RegisterScreen(
                navController = navController,
                viewModel = authViewModel,
                onRegisterSuccess = { userId ->
                    currentUserId = userId
                    navController.navigate("profile/true") {
                        popUpTo("register") { inclusive = true }
                    }
                }
            )
        }
        
        composable("dashboard") {
            Dashboard(
                navController = navController, 
                userId = currentUserId,
                viewModel = dashboardViewModel
            )
        }

        composable("food log") { }
        composable("goal") { }

        composable("profile") {
            Profile(navController = navController, userId = currentUserId, viewModel = profileViewModel, isOnboarding = false)
        }

        composable("profile/{isOnboarding}") { backStackEntry ->
            val isOnboarding = backStackEntry.arguments?.getString("isOnboarding")?.toBoolean() ?: false
            Profile(navController = navController, userId = currentUserId, viewModel = profileViewModel, isOnboarding = isOnboarding)
        }
    }
}
