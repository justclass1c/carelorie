package com.xxx.carelorie.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.xxx.carelorie.data.MealPresetWithItems
import com.xxx.carelorie.ui.components.dashboard.MEAL_TYPES
import com.xxx.carelorie.ui.components.dashboard.MacroChip
import com.xxx.carelorie.ui.layout.ContentWidth
import com.xxx.carelorie.ui.layout.constrainedWidth
import com.xxx.carelorie.ui.theme.MacroColors
import com.xxx.carelorie.ui.viewmodels.SavedMealsEvent
import com.xxx.carelorie.ui.viewmodels.SavedMealsViewModel

/**
 * Meals the user has saved, ready to log again in one tap.
 *
 * Reached from the overflow menu on any meal card, which is also where meals are saved from.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SavedMealsScreen(
    navController: NavController,
    userId: String,
    viewModel: SavedMealsViewModel
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(userId) { viewModel.onEvent(SavedMealsEvent.Start(userId)) }

    LaunchedEffect(uiState.message) {
        uiState.message?.let {
            // Consume before awaiting: showSnackbar suspends until the bar goes away, so
            // leaving the screen cancels this effect and the message would stay set and
            // replay every time you came back.
            viewModel.onEvent(SavedMealsEvent.MessageConsumed)
            snackbarHostState.showSnackbar(it)
        }
    }

    var renaming by remember { mutableStateOf<MealPresetWithItems?>(null) }
    var deleting by remember { mutableStateOf<MealPresetWithItems?>(null) }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Saved meals") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.TopCenter) {
            when {
                uiState.isLoading -> CircularProgressIndicator(Modifier.padding(top = 48.dp))

                uiState.meals.isEmpty() -> EmptyState()

                else -> LazyColumn(
                    modifier = Modifier.constrainedWidth(ContentWidth.Reading).fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(uiState.meals, key = { it.meal.localId }) { meal ->
                        SavedMealCard(
                            meal = meal,
                            onLog = { mealType ->
                                viewModel.onEvent(SavedMealsEvent.LogMeal(meal, mealType))
                            },
                            onRename = { renaming = meal },
                            onDelete = { deleting = meal }
                        )
                    }
                }
            }
        }
    }

    renaming?.let { meal ->
        RenameDialog(
            initial = meal.meal.name,
            onDismiss = { renaming = null },
            onConfirm = { name ->
                viewModel.onEvent(SavedMealsEvent.Rename(meal, name))
                renaming = null
            }
        )
    }

    deleting?.let { meal ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("Delete ${meal.meal.name}?") },
            text = { Text("This removes the saved meal. Anything you already logged from it stays in your diary.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.onEvent(SavedMealsEvent.Delete(meal))
                    deleting = null
                }) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { deleting = null }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun EmptyState() {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 64.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            Icons.Default.Bookmark,
            contentDescription = null,
            modifier = Modifier.size(40.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(12.dp))
        Text(
            "No saved meals yet",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(Modifier.height(6.dp))
        Text(
            "Log a meal you eat often, then choose \"Save as meal\" from that meal's menu on the " +
                "dashboard. It will appear here ready to add again.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun SavedMealCard(
    meal: MealPresetWithItems,
    onLog: (String) -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit
) {
    var menuOpen by remember { mutableStateOf(false) }
    var logMenuOpen by remember { mutableStateOf(false) }

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        meal.meal.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        "${meal.items.size} ${if (meal.items.size == 1) "item" else "items"} · " +
                            "saved from ${meal.meal.mealType}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Box {
                    IconButton(onClick = { menuOpen = true }) {
                        Icon(Icons.Default.MoreHoriz, contentDescription = "${meal.meal.name} options")
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text("Rename") },
                            leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) },
                            onClick = { menuOpen = false; onRename() }
                        )
                        DropdownMenuItem(
                            text = { Text("Delete") },
                            leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null) },
                            onClick = { menuOpen = false; onDelete() }
                        )
                    }
                }
            }

            Spacer(Modifier.height(10.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MacroChip("P", MacroColors.Protein, meal.totalProtein.toInt().toString())
                MacroChip("C", MacroColors.Carbs, meal.totalCarbs.toInt().toString())
                MacroChip("F", MacroColors.Fat, meal.totalFat.toInt().toString())
                MacroChip("Cal", MacroColors.Calories, meal.totalCalories.toString())
            }

            Spacer(Modifier.height(10.dp))

            meal.items.forEach { item ->
                Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                    Text(
                        item.foodName,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        "${item.calories} kcal",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            Box {
                Button(onClick = { logMenuOpen = true }, modifier = Modifier.fillMaxWidth()) {
                    Text("Log this meal")
                }
                DropdownMenu(expanded = logMenuOpen, onDismissRequest = { logMenuOpen = false }) {
                    MEAL_TYPES.forEach { mealType ->
                        DropdownMenuItem(
                            text = { Text("Add to $mealType") },
                            onClick = { logMenuOpen = false; onLog(mealType) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RenameDialog(initial: String, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var name by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Rename meal") },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(name) }, enabled = name.isNotBlank()) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
