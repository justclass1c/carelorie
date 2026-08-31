package com.xxx.carelorie.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.navigation.NavController
import com.xxx.carelorie.Routes
import androidx.compose.ui.graphics.Color
import com.xxx.carelorie.ui.components.LargeTitle
import com.xxx.carelorie.ui.components.dashboard.MacroCard
import com.xxx.carelorie.ui.components.dashboard.MacroRow
import com.xxx.carelorie.ui.components.dashboard.MealSection
import com.xxx.carelorie.ui.components.dashboard.ProgressPreview
import com.xxx.carelorie.ui.layout.isWideScreen
import com.xxx.carelorie.ui.theme.MacroColors
import com.xxx.carelorie.ui.viewmodels.DashboardEvent
import com.xxx.carelorie.ui.viewmodels.DashboardViewModel
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@Composable
fun Dashboard(
    navController: NavController,
    userId: String,
    viewModel: DashboardViewModel
) {
    val wide = isWideScreen
    val uiState by viewModel.uiState.collectAsState()
    val currentDate = LocalDate.now().format(DateTimeFormatter.ofPattern("dd MMMM"))
    val lifecycleOwner = LocalLifecycleOwner.current
    val snackbarHostState = remember { SnackbarHostState() }

    DisposableEffect(lifecycleOwner, userId) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.onEvent(DashboardEvent.LoadData(userId))
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    LaunchedEffect(uiState.message) {
        uiState.message?.let {
            // Consume before awaiting: showSnackbar suspends until the bar goes away, so
            // leaving the screen cancels this effect and the message would stay set and
            // replay every time you came back.
            viewModel.onEvent(DashboardEvent.MessageConsumed)
            snackbarHostState.showSnackbar(it)
        }
    }

    if (uiState.isLoading && uiState.todayLogs.isEmpty() && uiState.monthlyIntake.isEmpty()) {
        // Show only the loading indicator while the initial data load is in progress.
        // The navigation bar is rendered by the shell outside this screen.
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    val error = uiState.error
    if (error != null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(error, color = MaterialTheme.colorScheme.error)
                Spacer(Modifier.height(8.dp))
                Button(onClick = { viewModel.onEvent(DashboardEvent.LoadData(userId)) }) {
                    Text("Try again")
                }
            }
        }
        return
    }

    Scaffold(
        // The shell behind this already paints the themed background; a second opaque layer here
        // would sit on top of it and lose the grouped grey the cards are meant to float on.
        containerColor = Color.Transparent,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { navController.navigate(Routes.DIET_CHAT) },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                shape = CircleShape,
                elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 6.dp)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Chat,
                    contentDescription = "AI Diet Advice"
                )
            }
        }
    ) { scaffoldPadding ->
        // NOTE: children of a verticalScroll column must never use fillMaxSize()/fillMaxHeight().
        // The scroll gives them an infinite height constraint, which is what was cutting the
        // dashboard off before the lower meal cards could be reached.
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(scaffoldPadding)
                .padding(horizontal = 20.dp)
                .padding(top = 12.dp)
        ) {
            LargeTitle(
                title = greetingName(uiState.username),
                subtitle = currentDate
            )

            Spacer(Modifier.height(20.dp))

            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Top
            ) {
                if (!wide) {
                    // Phone Layout: Vertical stack
                    ProgressPreview(weeklyData = uiState.weeklyIntake, targets = uiState.targets)

                    Spacer(Modifier.height(20.dp))

                    MacroRow(todayIntake = uiState.todayIntake, targets = uiState.targets)
                } else {
                    // Tablet Layout: Side-by-side centered
                    Row(
                        modifier = Modifier
                            .fillMaxWidth(0.9f) // Centered relative to parent Column, takes 90% width
                            .height(IntrinsicSize.Min),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Left: Calendar Chart (Sets the height)
                        ProgressPreview(
                            weeklyData = uiState.weeklyIntake,
                            targets = uiState.targets,
                            modifier = Modifier.weight(0.5f)
                        )

                        Spacer(modifier = Modifier.width(24.dp))

                        // Right: Macro Grid (Shrinks to match ProgressPreview height)
                        Column(
                            modifier = Modifier
                                .weight(0.5f)
                                .fillMaxHeight(),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Row(
                                modifier = Modifier.weight(1f),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                MacroCard("Protein", uiState.todayIntake.protein, uiState.targets.proteinGrams, Modifier.fillMaxHeight().aspectRatio(1f))
                                MacroCard("Carbs", uiState.todayIntake.carbs, uiState.targets.carbsGrams, Modifier.fillMaxHeight().aspectRatio(1f))
                            }
                            Row(
                                modifier = Modifier.weight(1f),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                MacroCard("Fat", uiState.todayIntake.fat, uiState.targets.fatGrams, Modifier.fillMaxHeight().aspectRatio(1f))
                                MacroCard("Calories", uiState.todayIntake.calories.toFloat(), uiState.targets.calories.toFloat(), Modifier.fillMaxHeight().aspectRatio(1f))
                            }
                        }
                    }
                }

                Spacer(Modifier.height(24.dp))

                MealSection(
                    todayLogs = uiState.todayLogs,
                    onAddMealClick = { mealType ->
                        navController.navigate(Routes.foodSearch(mealType))
                    },
                    onDeleteLog = { log ->
                        viewModel.onEvent(DashboardEvent.DeleteLog(userId, log))
                    },
                    onSaveAsMeal = { mealType, name ->
                        viewModel.onEvent(DashboardEvent.SaveMealAsPreset(userId, mealType, name))
                    },
                    onOpenSavedMeals = { navController.navigate(Routes.SAVED_MEALS) }
                )
            }

            // Clearance for the floating button. Scaffold reserves space for a bottom bar but not
            // for the FAB, so without this the last meal card's controls sit underneath it.
            Spacer(Modifier.height(96.dp))
        }
    }
}

/**
 * A large title is a name, not a sentence.
 *
 * Falls back to the date's own heading when there is no profile name yet, rather than greeting
 * somebody by their user id — which is what `username` holds until onboarding fills it in.
 */
private fun greetingName(username: String): String =
    if (username.isBlank() || username.length > 24) "Today" else "Hi, " + username + "."

