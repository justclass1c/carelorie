package com.xxx.carelorie.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
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
import com.xxx.carelorie.ui.components.dashboard.MacroRow
import com.xxx.carelorie.ui.components.dashboard.MealSection
import com.xxx.carelorie.ui.components.dashboard.ProgressPreview
import com.xxx.carelorie.ui.viewmodels.DashboardEvent
import com.xxx.carelorie.ui.viewmodels.DashboardViewModel
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@Composable
fun Dashboard(navController: NavController, userId: Int, viewModel: DashboardViewModel) {
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
            snackbarHostState.showSnackbar(it)
            viewModel.onEvent(DashboardEvent.MessageConsumed)
        }
    }

    if (uiState.error != null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(uiState.error!!, color = MaterialTheme.colorScheme.error)
                Spacer(Modifier.height(8.dp))
                Button(onClick = { viewModel.onEvent(DashboardEvent.LoadData(userId)) }) {
                    Text("Try again")
                }
            }
        }
        return
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // NOTE: children of a verticalScroll column must never use fillMaxSize()/fillMaxHeight().
        // The scroll gives them an infinite height constraint, which is what was cutting the
        // dashboard off before the lower meal cards could be reached.
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(24.dp)
        ) {
            Column(horizontalAlignment = Alignment.Start) {
                Text(
                    text = "Welcome, ${uiState.username}.",
                    style = MaterialTheme.typography.headlineSmall
                )
                Text(
                    text = currentDate,
                    style = MaterialTheme.typography.headlineSmall,
                    fontSize = 20.sp
                )
            }

            Spacer(Modifier.height(20.dp))

            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Top
            ) {
                ProgressPreview(weeklyData = uiState.weeklyIntake)

                Spacer(Modifier.height(20.dp))

                MacroRow(todayIntake = uiState.todayIntake)

                Spacer(Modifier.height(20.dp))

                MealSection(
                    todayLogs = uiState.todayLogs,
                    onAddMealClick = { mealType ->
                        navController.navigate("foodSearch/$mealType")
                    },
                    onDeleteLog = { log ->
                        viewModel.onEvent(DashboardEvent.DeleteLog(userId, log))
                    }
                )
            }

            // Breathing room so the last card clears the navigation bar.
            Spacer(Modifier.height(24.dp))
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }
}
