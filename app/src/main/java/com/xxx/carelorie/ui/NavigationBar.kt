package com.xxx.carelorie.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardDoubleArrowLeft
import androidx.compose.material.icons.filled.KeyboardDoubleArrowRight
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.outlined.FitnessCenter
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Restaurant
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarDefaults
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
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
data class Screens(
    val route: String,
    val label: String,
    /** Shown when the tab is selected. */
    val icon: ImageVector,
    /**
     * Shown when it is not.
     *
     * Filled-versus-outlined is how a tab bar signals selection without a highlight behind it —
     * it survives being looked at out of the corner of the eye, which a colour change alone
     * does not.
     */
    val outlineIcon: ImageVector
)

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
    Screens(Routes.DASHBOARD, "Home", Icons.Filled.Home, Icons.Outlined.Home),
    Screens(Routes.FOOD_LOG, "Diary", Icons.AutoMirrored.Filled.MenuBook, Icons.AutoMirrored.Outlined.MenuBook),
    Screens(Routes.FOOD_QUERY, "Foods", Icons.Filled.Restaurant, Icons.Outlined.Restaurant),
    Screens(Routes.GOAL, "Goal", Icons.Filled.FitnessCenter, Icons.Outlined.FitnessCenter),
    Screens(Routes.PROFILE, "Profile", Icons.Filled.Person, Icons.Outlined.Person)
)

/** Routes that show no navigation chrome at all. */
private fun hidesNavigation(route: String?): Boolean {
    if (route == null) return true
    return route == Routes.LOGIN ||
        route == Routes.REGISTER ||
        route == Routes.ONBOARDING ||
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
    // Survives rotation and window resizing, so collapsing does not undo itself.
    var railExpanded by rememberSaveable { mutableStateOf(true) }

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
                Column {
                    // A hairline above the bar instead of a tonal slab. It separates the bar from
                    // the content by the smallest thing that works, which is what keeps the eye on
                    // the page rather than on the chrome.
                    HorizontalDivider(
                        thickness = Dp.Hairline,
                        color = MaterialTheme.colorScheme.outlineVariant
                    )
                    NavigationBar(
                        windowInsets = NavigationBarDefaults.windowInsets,
                        containerColor = MaterialTheme.colorScheme.surface,
                        tonalElevation = 0.dp
                    ) {
                        entries.forEach { screen ->
                            val selected = isSelected(screen.route)
                            NavigationBarItem(
                                selected = selected,
                                onClick = { onNavigate(screen.route) },
                                label = { NavLabel(screen.label) },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) screen.icon else screen.outlineIcon,
                                        contentDescription = screen.label
                                    )
                                },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = MaterialTheme.colorScheme.primary,
                                    selectedTextColor = MaterialTheme.colorScheme.primary,
                                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                    // The pill behind a selected icon is Material's signature and
                                    // fights the flat tab bar this is going for. Colour alone marks
                                    // the selection, helped by the filled-versus-outlined icon.
                                    indicatorColor = Color.Transparent
                                )
                            )
                        }
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
                    ),
                    header = {
                        // Collapsing drops the labels and keeps the icons, which is what the
                        // prototype's « control does. Worth the row of pixels on a 10" tablet
                        // in landscape, where the rail is otherwise pure margin.
                        IconButton(onClick = { railExpanded = !railExpanded }) {
                            Icon(
                                imageVector = if (railExpanded) {
                                    Icons.Default.KeyboardDoubleArrowLeft
                                } else {
                                    Icons.Default.KeyboardDoubleArrowRight
                                },
                                contentDescription = if (railExpanded) {
                                    "Collapse navigation"
                                } else {
                                    "Expand navigation"
                                }
                            )
                        }
                    }
                ) {
                    entries.forEach { screen ->
                        NavigationRailItem(
                            selected = isSelected(screen.route),
                            onClick = { onNavigate(screen.route) },
                            label = if (railExpanded) {
                                { NavLabel(screen.label) }
                            } else {
                                null
                            },
                            icon = {
                                Icon(
                                    imageVector = if (isSelected(screen.route)) {
                                        screen.icon
                                    } else {
                                        screen.outlineIcon
                                    },
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
