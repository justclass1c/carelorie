package com.xxx.carelorie.ui.screens

import android.content.res.Configuration
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.navigation.NavController
import com.xxx.carelorie.ui.components.dashboard.CarelorieCalendar
import com.xxx.carelorie.ui.components.dashboard.StreakBar
import com.xxx.carelorie.ui.components.dashboard.WeightGraph
import com.xxx.carelorie.ui.components.dashboard.WeightUpdateDialog
import com.xxx.carelorie.ui.viewmodels.DashboardEvent
import com.xxx.carelorie.ui.viewmodels.DashboardViewModel
import java.time.YearMonth

@Composable
fun GoalScreen(navController: NavController, userId: Int, viewModel: DashboardViewModel) {
    val uiState by viewModel.uiState.collectAsState()
    val lifecycleOwner = LocalLifecycleOwner.current
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    
    var selectedMonth by rememberSaveable { mutableStateOf(YearMonth.now()) }
    var showWeightDialog by rememberSaveable { mutableStateOf(false) }

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
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (isLandscape) {
                // Landscape Layout
                StreakBar(streakCount = uiState.currentStreak)

                Spacer(Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.Top
                ) {
                    CarelorieCalendar(
                        modifier = Modifier.weight(1f),
                        currentMonth = selectedMonth,
                        trackedDates = uiState.trackedDates,
                        onMonthChange = { newMonth ->
                            selectedMonth = newMonth
                            viewModel.onEvent(DashboardEvent.ChangeMonth(userId, newMonth))
                        }
                    )

                    Column(
                        modifier = Modifier.weight(1f),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        WeightGraph(
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(1f),
                            yearMonth = selectedMonth,
                            weightHistory = uiState.weightHistory
                        )

                        Spacer(Modifier.height(8.dp))

                        Button(
                            onClick = { showWeightDialog = true },
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
                        ) {
                            Text("Update Weight")
                        }
                    }
                }
            } else {
                // Portrait Layout
                CarelorieCalendar(
                    currentMonth = selectedMonth,
                    trackedDates = uiState.trackedDates,
                    onMonthChange = { newMonth ->
                        selectedMonth = newMonth
                        viewModel.onEvent(DashboardEvent.ChangeMonth(userId, newMonth))
                    }
                )

                Spacer(Modifier.height(16.dp))

                StreakBar(streakCount = uiState.currentStreak)

                Spacer(Modifier.height(16.dp))

                WeightGraph(
                    modifier = Modifier
                        .fillMaxWidth(0.8f)
                        .aspectRatio(1f),
                    yearMonth = selectedMonth,
                    weightHistory = uiState.weightHistory
                )

                Spacer(Modifier.height(8.dp))

                Button(
                    onClick = { showWeightDialog = true },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
                ) {
                    Text("Update Weight")
                }
            }

            Spacer(Modifier.height(32.dp))
        }
    }

    if (showWeightDialog) {
        WeightUpdateDialog(
            onDismiss = { showWeightDialog = false },
            onConfirm = { weight, date ->
                viewModel.onEvent(DashboardEvent.UpdateWeight(userId, weight, date))
                showWeightDialog = false
            }
        )
    }
}
