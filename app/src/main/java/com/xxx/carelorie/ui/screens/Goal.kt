package com.xxx.carelorie.ui.screens

import android.content.res.Configuration
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.outlined.Info
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.navigation.NavController
import com.xxx.carelorie.ui.components.dashboard.CarelorieCalendar
import com.xxx.carelorie.ui.components.dashboard.StreakBar
import com.xxx.carelorie.ui.components.dashboard.WeightGraph
import com.xxx.carelorie.ui.components.dashboard.WeightUpdateDialog
import com.xxx.carelorie.ui.layout.isWideScreen
import com.xxx.carelorie.ui.viewmodels.DashboardEvent
import com.xxx.carelorie.ui.viewmodels.DashboardViewModel
import java.time.YearMonth

@Composable
fun GoalScreen(navController: NavController, userId: String, viewModel: DashboardViewModel) {
    val uiState by viewModel.uiState.collectAsState()
    val lifecycleOwner = LocalLifecycleOwner.current
    // Width, not orientation: a tablet held in portrait still has room for two columns, and
    // the old orientation check gave it the phone layout.
    val useTwoColumns = isWideScreen

    var selectedMonth by rememberSaveable { mutableStateOf(YearMonth.now()) }
    var showWeightDialog by rememberSaveable { mutableStateOf(false) }

    DisposableEffect(lifecycleOwner, userId) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.onEvent(DashboardEvent.LoadData(userId))
                viewModel.onEvent(DashboardEvent.RequestGoalInsight(userId))
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

// Read once and branch on the local: checking the state and then re-reading it with !!
// could throw if the error cleared between the two reads.
val loadError = uiState.error

LaunchedEffect(uiState.weightHistory) {
    if (uiState.weightHistory.isNotEmpty() && uiState.goalInsight == null && !uiState.isGoalInsightLoading) {
        viewModel.onEvent(DashboardEvent.RequestGoalInsight(userId))
    }
}

    if (uiState.isLoading && uiState.weightHistory.isEmpty() && uiState.trackedDates.isEmpty()) {
        // Show only the loading indicator while the initial data load is in progress.
        // The navigation bar is rendered by the shell outside this screen.
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
    } else if (loadError != null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(loadError, color = MaterialTheme.colorScheme.error)
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
                .statusBarsPadding()
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (useTwoColumns) {
                // Wide layout: calendar and weight graph side by side
                StreakBar(streakCount = uiState.currentStreak)

                Spacer(Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.Top
                ) {
                    Column(
                        modifier = Modifier.weight(1f)
                    ) {
                        CarelorieCalendar(
                            currentMonth = selectedMonth,
                            trackedDates = uiState.trackedDates,
                            onMonthChange = { newMonth ->
                                selectedMonth = newMonth
                                viewModel.onEvent(DashboardEvent.ChangeMonth(userId, newMonth))
                            }
                        )
                        
                        Spacer(Modifier.height(16.dp))
                    }

                    Spacer(Modifier.width(16.dp))

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

                        Spacer(Modifier.height(8.dp))

                        AIInsightBox(
                            insight = uiState.goalInsight,
                            isLoading = uiState.isGoalInsightLoading,
                            onRefresh = { viewModel.onEvent(DashboardEvent.RequestGoalInsight(userId)) }
                        )
                    }
                }
            } else {
                // Narrow layout: single column
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

                Spacer(Modifier.height(16.dp))

                AIInsightBox(
                    insight = uiState.goalInsight,
                    isLoading = uiState.isGoalInsightLoading,
                    onRefresh = { viewModel.onEvent(DashboardEvent.RequestGoalInsight(userId)) }
                )

                Spacer(Modifier.height(16.dp))
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

@Composable
private fun AIInsightBox(
    insight: String?,
    isLoading: Boolean,
    onRefresh: () -> Unit
) {
    val insightColor = Color(0xFFCF4A4A)

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFF5F5F5)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = BorderStroke(1.dp, Color(0xFFE0E0E0)),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Outlined.Info,
                    contentDescription = null,
                    tint = Color(0xFF888888),
                    modifier = Modifier.height(18.dp).width(18.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = "AI Health Insight",
                    style = MaterialTheme.typography.titleSmall,
                    color = Color(0xFF555555)
                )
            }

            Spacer(Modifier.height(12.dp))

            when {
                isLoading -> {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.height(16.dp).width(16.dp),
                            strokeWidth = 2.dp,
                            color = insightColor
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "Analyzing your data...",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color(0xFF999999)
                        )
                    }
                }
                insight != null -> {
                    Text(
                        text = insight.replace(Regex("\\*\\*"), ""),
                        style = MaterialTheme.typography.bodyLarge,
                        color = Color.Black,
                        lineHeight = 24.sp
                    )
                }
                else -> {
                    Text(
                        text = "Update your weight to get a personalized health insight.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color(0xFF999999)
                    )
                }
            }

            if (!isLoading) {
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    IconButton(
                        onClick = onRefresh,
                        modifier = Modifier.height(32.dp).width(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Refresh,
                            contentDescription = "Refresh insight",
                            tint = Color(0xFF999999),
                            modifier = Modifier.height(18.dp).width(18.dp)
                        )
                    }
                }
            }
        }
    }
}
