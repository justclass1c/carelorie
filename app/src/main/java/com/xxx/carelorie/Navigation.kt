package com.xxx.carelorie

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
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
import com.xxx.carelorie.ui.screens.FoodEditorScreen
import com.xxx.carelorie.ui.screens.FoodLogScreen
import com.xxx.carelorie.ui.screens.FoodSearchScreen
import com.xxx.carelorie.ui.screens.GoalScreen
import com.xxx.carelorie.ui.screens.LoginScreen
import com.xxx.carelorie.ui.screens.FoodQueryScreen
import com.xxx.carelorie.ui.screens.Profile
import com.xxx.carelorie.ui.screens.RegisterScreen
import com.xxx.carelorie.ui.screens.ReviewFoodsScreen
import com.xxx.carelorie.ui.viewmodels.AuthViewModel
import com.xxx.carelorie.ui.viewmodels.CarelorieViewModelFactories
import com.xxx.carelorie.ui.viewmodels.DashboardViewModel
import com.xxx.carelorie.ui.viewmodels.FoodEditorViewModel
import com.xxx.carelorie.ui.viewmodels.FoodLogViewModel
import com.xxx.carelorie.ui.viewmodels.FoodSearchViewModel
import com.xxx.carelorie.ui.viewmodels.FoodQueryViewModel
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
    const val FOOD_QUERY = "foodQuery"
    const val FOOD_EDITOR = "foodEditor"

    fun foodSearch(mealType: String) = "$FOOD_SEARCH/$mealType"
    fun profile(isOnboarding: Boolean = false) = "$PROFILE?isOnboarding=$isOnboarding"

    /** Omit [presetLocalId] to create a new food; pass one to edit it. */
    fun foodEditor(presetLocalId: String? = null) =
        if (presetLocalId == null) FOOD_EDITOR else "$FOOD_EDITOR?presetLocalId=$presetLocalId"
}

@Composable
fun AppNavigation(
    modifier: Modifier = Modifier,
    navController: NavHostController,
    authViewModel: AuthViewModel,
    profileViewModel: ProfileViewModel,
    dashboardViewModel: DashboardViewModel,
    foodSearchViewModel: FoodSearchViewModel,
    foodLogViewModel: FoodLogViewModel,
    foodQueryViewModel: FoodQueryViewModel,
    sessionManager: SessionManager
) {
    val savedUserId = sessionManager.getUserId()
    var currentUserId by rememberSaveable { mutableStateOf(savedUserId) }

    // Navigation Compose defaults to a long cross-fade. Both screens are composed and drawn
    // through it, so switching tabs shows a ghost of the screen you just left — which reads as
    // a flicker rather than as an animation. A short fade keeps the transition honest and makes
    // tab switches feel immediate.
    val fade = tween<Float>(durationMillis = 110)

    NavHost(
        navController = navController,
        startDestination = if (savedUserId.isNotEmpty()) Routes.DASHBOARD else Routes.LOGIN,
        modifier = modifier,
        enterTransition = { fadeIn(animationSpec = fade) },
        exitTransition = { fadeOut(animationSpec = fade) },
        popEnterTransition = { fadeIn(animationSpec = fade) },
        popExitTransition = { fadeOut(animationSpec = fade) }
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
                    viewModel = dashboardViewModel
                )
            }
        }

        composable(Routes.FOOD_LOG) {
            RequireUser(currentUserId, navController) { userId ->
                FoodLogScreen(
                    navController = navController,
                    userId = userId,
                    viewModel = foodLogViewModel
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
            RequireUser(currentUserId, navController) { userId ->
                FoodSearchScreen(
                    navController = navController,
                    userId = userId,
                    mealType = mealType,
                    viewModel = foodSearchViewModel
                )
            }
        }

        composable(Routes.REVIEW_FOODS) {
            RequireUser(currentUserId, navController) { userId ->
                ReviewFoodsScreen(
                    navController = navController,
                    userId = userId,
                    viewModel = foodSearchViewModel
                )
            }
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

        composable(Routes.FOOD_QUERY) {
            RequireUser(currentUserId, navController) { userId ->
                FoodQueryScreen(
                    navController = navController,
                    userId = userId,
                    viewModel = foodQueryViewModel
                )
            }
        }

        composable(
            route = "${Routes.FOOD_EDITOR}?presetLocalId={presetLocalId}",
            arguments = listOf(navArgument("presetLocalId") {
                type = NavType.StringType
                nullable = true
                defaultValue = null
            })
        ) { backStackEntry ->
            RequireUser(currentUserId, navController) { userId ->
                val foodEditorViewModel: FoodEditorViewModel =
                    viewModel(factory = CarelorieViewModelFactories.FoodEditor)
                FoodEditorScreen(
                    navController = navController,
                    userId = userId,
                    presetLocalId = backStackEntry.arguments?.getString("presetLocalId"),
                    viewModel = foodEditorViewModel
                )
            }
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
