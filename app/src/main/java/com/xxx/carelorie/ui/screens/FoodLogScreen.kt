package com.xxx.carelorie.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Remove
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
import com.xxx.carelorie.Routes
import com.xxx.carelorie.data.remote.RemoteFoodLog
import com.xxx.carelorie.ui.components.dashboard.MEAL_TYPES
import com.xxx.carelorie.ui.components.food.FoodLogCalendar
import com.xxx.carelorie.ui.layout.isWideScreen
import com.xxx.carelorie.ui.theme.MacroColors
import com.xxx.carelorie.ui.theme.overLimitColor
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
    userId: String,
    viewModel: FoodLogViewModel
) {
    val wide = isWideScreen
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(userId) {
        viewModel.onEvent(FoodLogEvent.Start(userId))
    }

    LaunchedEffect(uiState.message) {
        uiState.message?.let {
            // Consume before awaiting: showSnackbar suspends until the bar goes away, so
            // leaving the screen cancels this effect and the message would stay set and
            // replay every time you came back.
            viewModel.onEvent(FoodLogEvent.MessageConsumed)
            snackbarHostState.showSnackbar(it)
        }
    }

    val onDateSelected: (LocalDate) -> Unit = { date ->
        viewModel.onEvent(FoodLogEvent.ChangeDate(userId, date))
    }
    val onDelete: (RemoteFoodLog) -> Unit = { log ->
        viewModel.onEvent(FoodLogEvent.DeleteLog(userId, log))
    }
    // Adding goes to food search for the day being viewed, not for today.
    val onAddToMeal: (String) -> Unit = { meal ->
        navController.navigate(Routes.foodSearch(meal, uiState.selectedDate))
    }
    var editing by remember { mutableStateOf<RemoteFoodLog?>(null) }

    Box(modifier = Modifier.fillMaxSize()) {
        if (wide) {
            // Tablet: calendar stays open beside the log instead of pushing it down.
            // statusBarsPadding on the Row, not on each column: the narrow branch had it and
            // this one did not, so the tablet heading drew underneath the clock.
            Row(modifier = Modifier.fillMaxSize().statusBarsPadding()) {
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
                    LogBody(
                        uiState = uiState,
                        onDelete = onDelete,
                        onAddToMeal = onAddToMeal,
                        onEdit = { editing = it }
                    )
                    Spacer(Modifier.height(24.dp))
                }
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .statusBarsPadding()
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
                LogBody(
                    uiState = uiState,
                    onDelete = onDelete,
                    onAddToMeal = onAddToMeal,
                    onEdit = { editing = it }
                )
                Spacer(Modifier.height(24.dp))
            }
        }

        editing?.let { entry ->
            EditEntryDialog(
                entry = entry,
                onDismiss = { editing = null },
                onSave = { quantity, meal ->
                    viewModel.onEvent(
                        FoodLogEvent.UpdateLog(userId, entry.localId, quantity, meal)
                    )
                    editing = null
                },
                onDelete = {
                    onDelete(entry)
                    editing = null
                }
            )
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
private fun LogBody(
    uiState: FoodLogUiState,
    onDelete: (RemoteFoodLog) -> Unit,
    onAddToMeal: (String) -> Unit,
    onEdit: (RemoteFoodLog) -> Unit
) {
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

        // Past and present days always show all four meals so there is somewhere to tap "+".
        // Only the future has nothing to offer.
        uiState.isFuture -> FutureDayNotice()

        else -> {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                uiState.mealGroups.forEach { group ->
                    MealGroupCard(
                        group = group,
                        onDelete = onDelete,
                        onAdd = { onAddToMeal(group.mealType) },
                        onEdit = onEdit
                    )
                }
                if (uiState.otherEntries.isNotEmpty()) {
                    // No add button: "Other" is a catch-all for unrecognised meal types, not a
                    // meal you can deliberately log into.
                    MealGroupCard(
                        group = MealGroup("Other", uiState.otherEntries),
                        onDelete = onDelete,
                        onEdit = onEdit
                    )
                }
            }
        }
    }
}

@Composable
private fun FutureDayNotice() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 48.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "This day hasn't happened yet",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "Pick today or an earlier date to log a meal.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun MealGroupCard(
    group: MealGroup,
    onDelete: (RemoteFoodLog) -> Unit,
    onEdit: (RemoteFoodLog) -> Unit,
    onAdd: (() -> Unit)? = null
) {
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
                onAdd?.let {
                    IconButton(onClick = it, modifier = Modifier.size(32.dp)) {
                        Icon(
                            Icons.Default.Add,
                            contentDescription = "Add food to ${group.mealType}",
                            modifier = Modifier.size(20.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            Spacer(Modifier.height(4.dp))

            Text(
                text = "P ${group.protein.toInt()}g  ·  C ${group.carbs.toInt()}g  ·  F ${group.fat.toInt()}g",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

            if (group.entries.isEmpty()) {
                Text(
                    text = "Nothing logged",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            group.entries.forEach { entry ->
                LogEntryRow(
                    entry = entry,
                    onDelete = { onDelete(entry) },
                    onEdit = { onEdit(entry) }
                )
            }
        }
    }
}

@Composable
private fun LogEntryRow(entry: RemoteFoodLog, onDelete: () -> Unit, onEdit: () -> Unit) {
    val time = FoodLogViewModel.formatLoggedTime(entry.createdAt)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onEdit)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = entry.foodName,
                style = MaterialTheme.typography.bodyLarge
            )
            if (entry.servings != 1f) {
                Text(
                    text = "${formatServings(entry.servings)} servings",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
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
    // Warn with yellow/red when the day's intake has gone over the limit.
    val effectiveColor = overLimitColor(current, target) ?: color

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(contentAlignment = Alignment.Center) {
            CircularProgressIndicator(
                progress = { progress },
                modifier = Modifier.size(48.dp),
                color = effectiveColor,
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
                strokeWidth = 4.dp
            )
            Text(
                text = label,
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp,
                color = effectiveColor
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

/** "2", "1.5" — trailing ".0" on a whole number reads like a bug. */
private fun formatServings(value: Float): String =
    if (value % 1f == 0f) value.toInt().toString() else value.toString()

/**
 * Edit a diary entry: change how many servings it was, or move it to another meal.
 *
 * Also the only place the nutrition detail gathered at logging time is visible, which is why the
 * whole row opens this rather than just an edit icon.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditEntryDialog(
    entry: RemoteFoodLog,
    onDismiss: () -> Unit,
    onSave: (Float, String) -> Unit,
    onDelete: () -> Unit
) {
    var quantity by remember(entry.localId) { mutableFloatStateOf(entry.servings) }
    var meal by remember(entry.localId) { mutableStateOf(entry.mealType) }
    var mealMenuOpen by remember { mutableStateOf(false) }

    // Macros are stored as the total for the logged servings, so one serving is total / quantity
    // and the preview scales from there.
    val perServing = if (entry.servings > 0f) entry.servings else 1f
    val factor = quantity / perServing

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(entry.foodName) },
        text = {
            Column {
                entry.brand?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                entry.servingDescription?.let {
                    Text("1 serving = $it", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }

                Spacer(Modifier.height(16.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Servings", style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.weight(1f))
                    IconButton(
                        onClick = { quantity = (quantity - 0.5f).coerceAtLeast(0.25f) },
                        enabled = quantity > 0.25f
                    ) {
                        Icon(Icons.Default.Remove, contentDescription = "Fewer servings")
                    }
                    Text(
                        text = formatServings(quantity),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Medium
                    )
                    IconButton(
                        onClick = { quantity = (quantity + 0.5f).coerceAtMost(20f) },
                        enabled = quantity < 20f
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "More servings")
                    }
                }

                Spacer(Modifier.height(8.dp))

                Box {
                    OutlinedButton(
                        onClick = { mealMenuOpen = true },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(meal)
                        Spacer(Modifier.weight(1f))
                        Icon(Icons.Default.ArrowDropDown, contentDescription = "Change meal")
                    }
                    DropdownMenu(
                        expanded = mealMenuOpen,
                        onDismissRequest = { mealMenuOpen = false }
                    ) {
                        MEAL_TYPES.forEach { option ->
                            DropdownMenuItem(
                                text = { Text(option) },
                                onClick = {
                                    meal = option
                                    mealMenuOpen = false
                                }
                            )
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))
                HorizontalDivider()
                Spacer(Modifier.height(12.dp))

                Text(
                    text = "${(entry.calories * factor).toInt()} kcal",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("P ${(entry.protein * factor).toInt()}g", color = MacroColors.Protein)
                    Text("C ${(entry.carbs * factor).toInt()}g", color = MacroColors.Carbs)
                    Text("F ${(entry.fat * factor).toInt()}g", color = MacroColors.Fat)
                }

                if (entry.hasNutritionDetail) {
                    Spacer(Modifier.height(12.dp))
                    listOfNotNull(
                        entry.fiberGrams?.let { "Fibre" to "${(it * factor).toInt()} g" },
                        entry.sugarGrams?.let { "Sugar" to "${(it * factor).toInt()} g" },
                        entry.saturatedFatGrams?.let { "Saturated fat" to "${(it * factor).toInt()} g" },
                        entry.sodiumMilligrams?.let { "Sodium" to "${(it * factor).toInt()} mg" }
                    ).forEach { (label, value) ->
                        Row(modifier = Modifier.fillMaxWidth()) {
                            Text(label, style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.weight(1f))
                            Text(value, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }

                // An AI guess stays labelled as one, even after it is in the diary.
                entry.nutritionSource?.let { source ->
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = source.lowercase().replace('_', ' ')
                            .replaceFirstChar { it.uppercase() },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(quantity, meal) }) { Text("Save") }
        },
        dismissButton = {
            Row {
                TextButton(
                    onClick = onDelete,
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) { Text("Delete") }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        }
    )
}
