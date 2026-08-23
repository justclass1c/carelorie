package com.xxx.carelorie.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarDefaults
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.xxx.carelorie.AppNavigation
import com.xxx.carelorie.data.SessionManager
import com.xxx.carelorie.ui.viewmodels.AuthViewModel
import com.xxx.carelorie.ui.viewmodels.DashboardViewModel
import com.xxx.carelorie.ui.viewmodels.FoodLogViewModel
import com.xxx.carelorie.ui.viewmodels.FoodSearchViewModel
import com.xxx.carelorie.ui.viewmodels.ProfileViewModel

data class Screens(val route: String, val label: String, val icon: ImageVector)
val entries = listOf( // list all screens for navigation bar here
    // label is for display, route works as argument for navigation
    Screens("dashboard", "Dashboard", Icons.Default.Home),
    Screens("food log", "Food Log", Icons.Default.Check), // to change icon
    Screens("goal", "Goal", Icons.Default.Build), // to change icon
    Screens("profile", "Profile", Icons.Default.Person)
)

@Composable
fun BottomNavBar(
    modifier: Modifier = Modifier, 
    authViewModel: AuthViewModel,
    profileViewModel: ProfileViewModel,
    dashboardViewModel: DashboardViewModel,
    foodLogViewModel: FoodLogViewModel,
    foodSearchViewModel: FoodSearchViewModel,
    sessionManager: SessionManager
) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    Scaffold(
        modifier = modifier,
        bottomBar = {
            val route = currentDestination?.route
            if (route != null && route != "login" && route != "register" && !route.startsWith("foodSearch")) {
                NavigationBar(windowInsets = NavigationBarDefaults.windowInsets) {
                    entries.forEach { screen ->
                        val isSelected = currentDestination.hierarchy.any { 
                            it.route == screen.route || it.route?.startsWith("${screen.route}?") == true 
                        }
                        NavigationBarItem(
                            selected = isSelected,
                            onClick = {
                                navController.navigate(screen.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            label = { Text(screen.label) },
                            icon = {
                                Icon(
                                    imageVector = screen.icon,
                                    contentDescription = screen.label,
                                )
                            }
                        )
                    }
                }
            }
        }
    ) { contentPadding ->
        AppNavigation(
            navController = navController,
            modifier = Modifier.padding(contentPadding),
            authViewModel = authViewModel,
            profileViewModel = profileViewModel,
            dashboardViewModel = dashboardViewModel,
            foodLogViewModel = foodLogViewModel,
            foodSearchViewModel = foodSearchViewModel,
            sessionManager = sessionManager
        )
    }
}
