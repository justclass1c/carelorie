package com.xxx.carelorie.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarDefaults
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.xxx.carelorie.AppNavigation
import com.xxx.carelorie.CarelorieApplication
import com.xxx.carelorie.Routes
import com.xxx.carelorie.ui.layout.isWideScreen
import com.xxx.carelorie.ui.viewmodels.AuthViewModel
import com.xxx.carelorie.ui.viewmodels.CarelorieViewModelFactories
import com.xxx.carelorie.ui.viewmodels.DashboardViewModel
import com.xxx.carelorie.ui.viewmodels.FoodLogViewModel
import com.xxx.carelorie.ui.viewmodels.FoodQueryViewModel
import com.xxx.carelorie.ui.viewmodels.FoodSearchViewModel
import com.xxx.carelorie.ui.viewmodels.ProfileViewModel

/**
 * One navigation destination. [label] is what the bar and rail show, so keep it short — see
 * [NavLabel].
 */
data class Screens(val route: String, val label: String, val icon: ImageVector)

/**
 * Label for a bar or rail destination.
 *
 * Five destinations share the bottom bar, so on a narrow phone each gets roughly 72dp. A label
 * allowed to wrap makes its item taller than its neighbours, which pushes that item's icon up
 * and knocks the whole row out of alignment. Ellipsising is the graceful failure here, and it
 * also holds up at large system font scales.
 */
@Composable
private fun NavLabel(text: String) {
    Text(text, maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
}

val entries = listOf(
    Screens(Routes.DASHBOARD, "Dashboard", Icons.Default.Dashboard),
    Screens(Routes.FOOD_LOG, "Food Log", Icons.AutoMirrored.Filled.MenuBook),
    Screens(Routes.FOOD_QUERY, "Presets", Icons.Default.Restaurant),
    Screens(Routes.GOAL, "Goal", Icons.Default.FitnessCenter),
    Screens(Routes.PROFILE, "Profile", Icons.Default.Person)
)

/** Routes that show no navigation chrome at all. */
private fun hidesNavigation(route: String?): Boolean {
    if (route == null) return true
    return route == Routes.LOGIN ||
        route == Routes.REGISTER ||
        route == Routes.REVIEW_FOODS ||
        route == Routes.DIET_CHAT ||
        route.startsWith(Routes.FOOD_EDITOR) ||
        route.startsWith(Routes.FOOD_SEARCH)
}

/**
 * App shell.
 *
 * Compact widths (phones, and tablets in split screen) get the bottom bar. Medium and expanded
 * widths get a side rail, which keeps vertical space for content and matches the tablet layout
 * in the prototype.
 */
@Composable
fun BottomNavBar(modifier: Modifier = Modifier) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination
    val currentRoute = currentDestination?.route

    // Created here rather than in MainActivity.onCreate so they are owned by a ViewModelStore
    // and survive rotation and window resizing.
    val authViewModel: AuthViewModel = viewModel(factory = CarelorieViewModelFactories.Auth)
    val profileViewModel: ProfileViewModel = viewModel(factory = CarelorieViewModelFactories.Profile)
    val dashboardViewModel: DashboardViewModel = viewModel(factory = CarelorieViewModelFactories.Dashboard)
    val foodSearchViewModel: FoodSearchViewModel = viewModel(factory = CarelorieViewModelFactories.FoodSearch)
    // Owned here rather than by their NavBackStackEntry: switching tabs pops the entry and
    // clears its ViewModelStore, so these were rebuilt — and their lists re-read from Room —
    // every single time you came back to the tab.
    val foodLogViewModel: FoodLogViewModel = viewModel(factory = CarelorieViewModelFactories.FoodLog)
    val foodQueryViewModel: FoodQueryViewModel = viewModel(factory = CarelorieViewModelFactories.FoodQuery)

    val container = (LocalContext.current.applicationContext as CarelorieApplication).container
    val sessionManager = container.sessionManager

    val useRail = isWideScreen
    val showNavigation = !hidesNavigation(currentRoute)

    val isSelected: (String) -> Boolean = { route ->
        currentDestination?.hierarchy?.any {
            it.route == route || it.route?.startsWith("$route?") == true
        } == true
    }
    val onNavigate: (String) -> Unit = { route ->
        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    Scaffold(
        modifier = modifier,
        bottomBar = {
            if (showNavigation && !useRail) {
                NavigationBar(windowInsets = NavigationBarDefaults.windowInsets) {
                    entries.forEach { screen ->
                        NavigationBarItem(
                            selected = isSelected(screen.route),
                            onClick = { onNavigate(screen.route) },
                            label = { NavLabel(screen.label) },
                            icon = {
                                Icon(
                                    imageVector = screen.icon,
                                    contentDescription = screen.label
                                )
                            }
                        )
                    }
                }
            }
        }
    ) { contentPadding ->
        Row(modifier = Modifier.fillMaxSize()) {
            if (showNavigation && useRail) {
                NavigationRail(
                    modifier = Modifier.padding(
                        top = contentPadding.calculateTopPadding(),
                        bottom = contentPadding.calculateBottomPadding()
                    )
                ) {
                    entries.forEach { screen ->
                        NavigationRailItem(
                            selected = isSelected(screen.route),
                            onClick = { onNavigate(screen.route) },
                            label = { NavLabel(screen.label) },
                            icon = {
                                Icon(
                                    imageVector = screen.icon,
                                    contentDescription = screen.label
                                )
                            }
                        )
                    }
                }
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .padding(
                        bottom = contentPadding.calculateBottomPadding(),
                        // Only apply the Scaffold's side padding if the rail ISN'T there,
                        // and only when navigation chrome is visible. Full-bleed screens
                        // (food search, etc.) handle their own side insets via their own
                        // Scaffold, so adding them here again duplicates the cutout inset
                        // and creates an uneven left gap in landscape.
                        start = if (!showNavigation || useRail) 0.dp else contentPadding.calculateStartPadding(androidx.compose.ui.unit.LayoutDirection.Ltr),
                        end = if (!showNavigation) 0.dp else contentPadding.calculateEndPadding(androidx.compose.ui.unit.LayoutDirection.Ltr)
                    )
            ) {
                AppNavigation(
                    navController = navController,
                    authViewModel = authViewModel,
                    profileViewModel = profileViewModel,
                    dashboardViewModel = dashboardViewModel,
                    foodSearchViewModel = foodSearchViewModel,
                    foodLogViewModel = foodLogViewModel,
                    foodQueryViewModel = foodQueryViewModel,
                    sessionManager = sessionManager
                )
            }
        }
    }
}
