package com.xxx.carelorie.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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

    if (uiState.error != null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(uiState.error!!, color = MaterialTheme.colorScheme.error)
                Spacer(Modifier.height(8.dp))
                Button(onClick = { viewModel.onEvent(DashboardEvent.LoadData(userId)) }) {
                    Text("Retry")
                }
            }
        }
    } else {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp)
                .verticalScroll(rememberScrollState())
        ) {
            // greeting and date at the top left
            Column(
                horizontalAlignment = Alignment.Start
            ) {
                Text(
                    text = "Welcome, ${uiState.username}.",
                    style = MaterialTheme.typography.headlineSmall,
                )

                Text(
                    text = currentDate,
                    style = MaterialTheme.typography.headlineSmall,
                    fontSize = 20.sp
                )
            }

            Spacer(Modifier.height(20.dp))

            // content below
            Column(
                modifier = Modifier.fillMaxSize(),
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
                    }
                )
            }
        }
    }
}
