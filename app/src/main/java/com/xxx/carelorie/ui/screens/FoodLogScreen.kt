package com.xxx.carelorie.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.xxx.carelorie.data.remote.RemoteFoodLog
import com.xxx.carelorie.ui.components.food.FoodLogCalendar
import com.xxx.carelorie.ui.theme.MacroColors
import com.xxx.carelorie.ui.viewmodels.FoodLogEvent
import com.xxx.carelorie.ui.viewmodels.FoodLogUiState
import com.xxx.carelorie.ui.viewmodels.FoodLogViewModel
import com.xxx.carelorie.ui.viewmodels.MealGroup
import java.time.LocalDate
import java.time.format.DateTimeFormatter

private val DATE_LABEL = DateTimeFormatter.ofPattern("EEE, MMM d")

@Composable
fun FoodLogScreen(
    navController: NavController,
    userId: Int,
    viewModel: FoodLogViewModel,
    isWideScreen: Boolean = false
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(userId) {
        viewModel.onEvent(FoodLogEvent.Start(userId))
    }

    LaunchedEffect(uiState.message) {
        uiState.message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.onEvent(FoodLogEvent.MessageConsumed)
        }
    }

    val onDateSelected: (LocalDate) -> Unit = { date ->
        viewModel.onEvent(FoodLogEvent.ChangeDate(userId, date))
    }
    val onDelete: (RemoteFoodLog) -> Unit = { log ->
        viewModel.onEvent(FoodLogEvent.DeleteLog(userId, log))
    }

    Box(modifier = Modifier.fillMaxSize()) {
        if (isWideScreen) {
            // Tablet: calendar stays open beside the log instead of pushing it down.
            Row(modifier = Modifier.fillMaxSize()) {
                Column(
                    modifier = Modifier
                        .weight(0.4f)
                        .fillMaxHeight()
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp)
                ) {
                    Text(
                        text = "Food Log",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )
                    FoodLogCalendar(
                        month = uiState.calendarMonth,
                        selectedDate = uiState.selectedDate,
                        loggedDates = uiState.loggedDates,
                        onMonthChange = { viewModel.onEvent(FoodLogEvent.ChangeMonth(it)) },
                        onDateSelected = onDateSelected
                    )
                    Spacer(Modifier.height(16.dp))
                    CalendarLegend()
                }

                VerticalDivider()

                Column(
                    modifier = Modifier
                        .weight(0.6f)
                        .fillMaxHeight()
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp)
                ) {
                    OfflineBanner(uiState.isOffline)
                    DateNavigator(
                        uiState = uiState,
                        onPrevious = { onDateSelected(uiState.selectedDate.minusDays(1)) },
                        onNext = { onDateSelected(uiState.selectedDate.plusDays(1)) },
                        onDateClick = null // calendar is already visible in this layout
                    )
                    Spacer(Modifier.height(16.dp))
                    MacroSummaryRow(uiState)
                    Spacer(Modifier.height(16.dp))
                    LogBody(uiState = uiState, onDelete = onDelete)
                    Spacer(Modifier.height(24.dp))
                }
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp)
            ) {
                Text(
                    text = "Food Log",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                OfflineBanner(uiState.isOffline)

                DateNavigator(
                    uiState = uiState,
                    onPrevious = { onDateSelected(uiState.selectedDate.minusDays(1)) },
                    onNext = { onDateSelected(uiState.selectedDate.plusDays(1)) },
                    onDateClick = { viewModel.onEvent(FoodLogEvent.ToggleCalendar) }
                )

                AnimatedVisibility(visible = uiState.isCalendarVisible) {
                    Column {
                        Spacer(Modifier.height(12.dp))
                        FoodLogCalendar(
                            month = uiState.calendarMonth,
                            selectedDate = uiState.selectedDate,
                            loggedDates = uiState.loggedDates,
                            onMonthChange = { viewModel.onEvent(FoodLogEvent.ChangeMonth(it)) },
                            onDateSelected = { date ->
                                onDateSelected(date)
                                viewModel.onEvent(FoodLogEvent.ToggleCalendar)
                            }
                        )
                        Spacer(Modifier.height(8.dp))
                        CalendarLegend()
                    }
                }

                Spacer(Modifier.height(20.dp))
                MacroSummaryRow(uiState)
                Spacer(Modifier.height(20.dp))
                LogBody(uiState = uiState, onDelete = onDelete)
                Spacer(Modifier.height(24.dp))
            }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }
}

@Composable
private fun OfflineBanner(isOffline: Boolean) {
    AnimatedVisibility(visible = isOffline) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp)
                .background(
                    MaterialTheme.colorScheme.surfaceVariant,
                    RoundedCornerShape(8.dp)
                )
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.CloudOff,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = "Showing saved history. New entries upload when you reconnect.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun DateNavigator(
    uiState: FoodLogUiState,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onDateClick: (() -> Unit)?
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onPrevious) {
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                contentDescription = "Previous day"
            )
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .padding(horizontal = 8.dp)
                .then(
                    if (onDateClick != null) {
                        Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable(onClick = onDateClick)
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    } else {
                        Modifier
                    }
                )
        ) {
            Text(
                text = if (uiState.isToday) {
                    "Today"
                } else {
                    uiState.selectedDate.format(DATE_LABEL)
                },
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Medium
            )
            if (onDateClick != null) {
                Spacer(Modifier.width(6.dp))
                Icon(
                    Icons.Default.CalendarMonth,
                    contentDescription = "Choose a date",
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        IconButton(onClick = onNext, enabled = !uiState.isToday && !uiState.isFuture) {
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = "Next day"
            )
        }
    }
}

@Composable
private fun MacroSummaryRow(uiState: FoodLogUiState) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        MacroSummaryItem("P", uiState.totalProtein, uiState.targets.proteinGrams, MacroColors.Protein)
        MacroSummaryItem("C", uiState.totalCarbs, uiState.targets.carbsGrams, MacroColors.Carbs)
        MacroSummaryItem("F", uiState.totalFat, uiState.targets.fatGrams, MacroColors.Fat)
        MacroSummaryItem(
            "Cal",
            uiState.totalCalories.toFloat(),
            uiState.targets.calories.toFloat(),
            MacroColors.Calories
        )
    }
}

@Composable
private fun LogBody(uiState: FoodLogUiState, onDelete: (RemoteFoodLog) -> Unit) {
    when {
        uiState.isLoading && uiState.logs.isEmpty() -> {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 48.dp),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        }

        uiState.logs.isEmpty() -> {
            EmptyDay(isToday = uiState.isToday, isFuture = uiState.isFuture)
        }

        else -> {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                uiState.mealGroups.filterNot { it.isEmpty }.forEach { group ->
                    MealGroupCard(group = group, onDelete = onDelete)
                }
                if (uiState.otherEntries.isNotEmpty()) {
                    MealGroupCard(
                        group = MealGroup("Other", uiState.otherEntries),
                        onDelete = onDelete
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyDay(isToday: Boolean, isFuture: Boolean) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 48.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = when {
                    isFuture -> "Nothing logged yet"
                    isToday -> "No meals logged today"
                    else -> "Nothing was logged on this day"
                },
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = if (isToday) {
                    "Add food from the Dashboard to see it here."
                } else {
                    "Pick another date from the calendar."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun MealGroupCard(group: MealGroup, onDelete: (RemoteFoodLog) -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = group.mealType,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = "${group.calories} kcal",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(Modifier.height(4.dp))

            Text(
                text = "P ${group.protein.toInt()}g  ·  C ${group.carbs.toInt()}g  ·  F ${group.fat.toInt()}g",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

            group.entries.forEach { entry ->
                LogEntryRow(entry = entry, onDelete = { onDelete(entry) })
            }
        }
    }
}

@Composable
private fun LogEntryRow(entry: RemoteFoodLog, onDelete: () -> Unit) {
    val time = FoodLogViewModel.formatLoggedTime(entry.createdAt)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = entry.foodName,
                style = MaterialTheme.typography.bodyLarge
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (time != null) {
                    Text(
                        text = time,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.width(8.dp))
                }
                Text(
                    text = "${entry.protein.toInt()}P · ${entry.carbs.toInt()}C · ${entry.fat.toInt()}F",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Text(
            text = "${entry.calories}",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium
        )

        IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
            Icon(
                Icons.Default.Delete,
                contentDescription = "Remove ${entry.foodName}",
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun CalendarLegend() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        LegendDot(MaterialTheme.colorScheme.primaryContainer, "Tracked")
        Spacer(Modifier.width(16.dp))
        LegendDot(MaterialTheme.colorScheme.surfaceVariant, "Not tracked")
    }
}

@Composable
private fun LegendDot(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(12.dp)
                .background(color, RoundedCornerShape(6.dp))
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun MacroSummaryItem(label: String, current: Float, target: Float, color: Color) {
    val progress = if (target > 0f) (current / target).coerceIn(0f, 1f) else 0f

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(contentAlignment = Alignment.Center) {
            CircularProgressIndicator(
                progress = { progress },
                modifier = Modifier.size(48.dp),
                color = color,
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
                strokeWidth = 4.dp
            )
            Text(
                text = label,
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp,
                color = color
            )
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = "${current.toInt()} / ${target.toInt()}",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
