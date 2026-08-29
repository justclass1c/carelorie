package com.xxx.carelorie

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.xxx.carelorie.data.SessionManager
import com.xxx.carelorie.ui.screens.Dashboard
import com.xxx.carelorie.ui.screens.DietChatScreen
import com.xxx.carelorie.ui.screens.FoodLogScreen
import com.xxx.carelorie.ui.screens.FoodSearchScreen
import com.xxx.carelorie.ui.screens.GoalScreen
import com.xxx.carelorie.ui.screens.LoginScreen
import com.xxx.carelorie.ui.screens.Profile
import com.xxx.carelorie.ui.screens.RegisterScreen
import com.xxx.carelorie.ui.screens.ReviewFoodsScreen
import com.xxx.carelorie.ui.viewmodels.AuthViewModel
import com.xxx.carelorie.ui.viewmodels.CarelorieViewModelFactories
import com.xxx.carelorie.ui.viewmodels.DashboardViewModel
import com.xxx.carelorie.ui.viewmodels.FoodLogViewModel
import com.xxx.carelorie.ui.viewmodels.FoodSearchViewModel
import com.xxx.carelorie.ui.viewmodels.ProfileViewModel

/** Route names in one place so the navigation bar and the graph can never drift apart. */
object Routes {
    const val LOGIN = "login"
    const val REGISTER = "register"
    const val DASHBOARD = "dashboard"
    const val FOOD_LOG = "foodLog"
    const val GOAL = "goal"
    const val PROFILE = "profile"
    const val FOOD_SEARCH = "foodSearch"
    const val REVIEW_FOODS = "reviewFoods"
    const val DIET_CHAT = "dietChat"

    fun foodSearch(mealType: String) = "$FOOD_SEARCH/$mealType"
    fun profile(isOnboarding: Boolean = false) = "$PROFILE?isOnboarding=$isOnboarding"
}

@Composable
fun AppNavigation(
    modifier: Modifier = Modifier,
    navController: NavHostController,
    widthSizeClass: WindowWidthSizeClass = WindowWidthSizeClass.Compact,
    authViewModel: AuthViewModel,
    profileViewModel: ProfileViewModel,
    dashboardViewModel: DashboardViewModel,
    foodSearchViewModel: FoodSearchViewModel,
    sessionManager: SessionManager
) {
    val savedUserId = sessionManager.getUserId()
    var currentUserId by rememberSaveable { mutableStateOf(savedUserId) }
    val isWideScreen = widthSizeClass != WindowWidthSizeClass.Compact

    NavHost(
        navController = navController,
        startDestination = if (savedUserId.isNotEmpty()) Routes.DASHBOARD else Routes.LOGIN,
        modifier = modifier
    ) {
        composable(Routes.LOGIN) {
            LoginScreen(
                onLoginSuccess = { userId: String ->
                    currentUserId = userId
                    navController.navigate(Routes.DASHBOARD) {
                        popUpTo(Routes.LOGIN) { inclusive = true }
                    }
                },
                navController = navController,
                viewModel = authViewModel
            )
        }

        composable(Routes.REGISTER) {
            RegisterScreen(
                navController = navController,
                viewModel = authViewModel,
                onRegisterSuccess = { userId: String ->
                    currentUserId = userId
                    navController.navigate(Routes.profile(isOnboarding = true)) {
                        popUpTo(Routes.REGISTER) { inclusive = true }
                    }
                }
            )
        }

        composable(Routes.DASHBOARD) {
            RequireUser(currentUserId, navController) { userId ->
                Dashboard(
                    navController = navController,
                    userId = userId,
                    viewModel = dashboardViewModel,
                    widthSizeClass = widthSizeClass
                )
            }
        }

        composable(Routes.FOOD_LOG) {
            RequireUser(currentUserId, navController) { userId ->
                val foodLogViewModel: FoodLogViewModel =
                    viewModel(factory = CarelorieViewModelFactories.FoodLog)
                FoodLogScreen(
                    navController = navController,
                    userId = userId,
                    viewModel = foodLogViewModel,
                    isWideScreen = isWideScreen
                )
            }
        }

        composable(Routes.GOAL) {
            RequireUser(currentUserId, navController) { userId ->
                GoalScreen(
                    navController = navController,
                    userId = userId,
                    viewModel = dashboardViewModel
                )
            }
        }

        composable("${Routes.FOOD_SEARCH}/{mealType}") { backStackEntry ->
            val mealType = backStackEntry.arguments?.getString("mealType") ?: "Breakfast"
            FoodSearchScreen(
                navController = navController,
                userId = currentUserId,
                mealType = mealType,
                viewModel = foodSearchViewModel,
                isWideScreen = isWideScreen
            )
        }

        composable(Routes.REVIEW_FOODS) {
            ReviewFoodsScreen(
                navController = navController,
                userId = currentUserId,
                viewModel = foodSearchViewModel
            )
        }

        composable(
            route = "${Routes.PROFILE}?isOnboarding={isOnboarding}",
            arguments = listOf(navArgument("isOnboarding") {
                type = NavType.BoolType
                defaultValue = false
            })
        ) { backStackEntry ->
            val isOnboarding = backStackEntry.arguments?.getBoolean("isOnboarding") ?: false
            Profile(
                navController = navController,
                userId = currentUserId,
                viewModel = profileViewModel,
                isOnboarding = isOnboarding
            )
        }

        composable(Routes.DIET_CHAT) {
            val dietChatViewModel: com.xxx.carelorie.ui.viewmodels.DietChatViewModel =
                viewModel(factory = CarelorieViewModelFactories.DietChat(currentUserId))
            DietChatScreen(
                navController = navController,
                viewModel = dietChatViewModel
            )
        }
    }
}

/**
 * Renders [content] only when a user is signed in, otherwise bounces back to login.
 * Replaces the copy-pasted `if (currentUserId != "") ... else LaunchedEffect` blocks.
 */
@Composable
private fun RequireUser(
    userId: String,
    navController: NavHostController,
    content: @Composable (String) -> Unit
) {
    if (userId.isNotEmpty()) {
        content(userId)
    } else {
        LaunchedEffect(Unit) {
            navController.navigate(Routes.LOGIN) { popUpTo(0) }
        }
    }
}
