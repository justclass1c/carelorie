package com.xxx.carelorie

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.xxx.carelorie.data.SessionManager
import com.xxx.carelorie.ui.screens.Dashboard
import com.xxx.carelorie.ui.screens.FoodLogScreen
import com.xxx.carelorie.ui.screens.FoodSearchScreen
import com.xxx.carelorie.ui.screens.GoalScreen
import com.xxx.carelorie.ui.screens.LoginScreen
import com.xxx.carelorie.ui.screens.Profile
import com.xxx.carelorie.ui.screens.RegisterScreen
import com.xxx.carelorie.ui.viewmodels.AuthViewModel
import com.xxx.carelorie.ui.viewmodels.DashboardViewModel
import com.xxx.carelorie.ui.viewmodels.FoodLogViewModel
import com.xxx.carelorie.ui.viewmodels.FoodSearchViewModel
import com.xxx.carelorie.ui.viewmodels.ProfileViewModel

@Composable
fun AppNavigation(
    modifier: Modifier = Modifier,
    navController: NavHostController,
    authViewModel: AuthViewModel,
    profileViewModel: ProfileViewModel,
    dashboardViewModel: DashboardViewModel,
    foodLogViewModel: FoodLogViewModel,
    foodSearchViewModel: FoodSearchViewModel,
    sessionManager: SessionManager
) {
    val savedUserId = sessionManager.getUserId()
    var currentUserId by rememberSaveable { mutableIntStateOf(savedUserId) }

    NavHost(
        navController = navController,
        startDestination = if (savedUserId != -1) "dashboard" else "login",
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
                    navController.navigate("profile?isOnboarding=true") {
                        popUpTo("register") { inclusive = true }
                    }
                }
            )
        }
        
        composable("dashboard") {
            if (currentUserId != -1) {
                Dashboard(
                    navController = navController, 
                    userId = currentUserId,
                    viewModel = dashboardViewModel
                )
            } else {
                // Fallback to login if somehow userId is lost
                LaunchedEffect(Unit) {
                    navController.navigate("login") {
                        popUpTo(0)
                    }
                }
            }
        }

        composable("food log") {
            if (currentUserId != -1) {
                FoodLogScreen(
                    navController = navController,
                    userId = currentUserId,
                    viewModel = foodLogViewModel
                )
            }
        }
        composable("goal") {
            if (currentUserId != -1) {
                GoalScreen(
                    navController = navController,
                    userId = currentUserId,
                    viewModel = dashboardViewModel
                )
            } else {
                LaunchedEffect(Unit) {
                    navController.navigate("login") {
                        popUpTo(0)
                    }
                }
            }
        }

        composable("foodSearch/{mealType}") { backStackEntry ->
            val mealType = backStackEntry.arguments?.getString("mealType") ?: "Breakfast"
            FoodSearchScreen(
                navController = navController,
                userId = currentUserId,
                mealType = mealType,
                viewModel = foodSearchViewModel
            )
        }

        composable(
            route = "profile?isOnboarding={isOnboarding}",
            arguments = listOf(navArgument("isOnboarding") {
                type = NavType.BoolType
                defaultValue = false
            })
        ) { backStackEntry ->
            val isOnboarding = backStackEntry.arguments?.getBoolean("isOnboarding") ?: false
            Profile(navController = navController, userId = currentUserId, viewModel = profileViewModel, isOnboarding = isOnboarding)
        }
    }
}
