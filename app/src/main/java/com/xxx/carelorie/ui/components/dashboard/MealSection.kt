package com.xxx.carelorie.ui.components.dashboard

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xxx.carelorie.data.remote.RemoteFoodLog
import com.xxx.carelorie.ui.theme.MacroColors

/** The four meal buckets, in the order they appear on the dashboard. */
val MEAL_TYPES = listOf("Breakfast", "Lunch", "Dinner", "Snack")

@Composable
fun MealSection(
    todayLogs: List<RemoteFoodLog>,
    onAddMealClick: (String) -> Unit,
    onDeleteLog: (RemoteFoodLog) -> Unit = {},
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        MEAL_TYPES.forEach { meal ->
            MealCard(
                title = meal,
                logs = todayLogs.filter { it.mealType.equals(meal, ignoreCase = true) },
                onAddClick = { onAddMealClick(meal) },
                onDeleteLog = onDeleteLog
            )
        }
    }
}

@Composable
fun MealCard(
    title: String,
    logs: List<RemoteFoodLog>,
    onAddClick: () -> Unit,
    onDeleteLog: (RemoteFoodLog) -> Unit
) {
    var expanded by rememberSaveable(title) { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }
    var deleteMode by rememberSaveable(title) { mutableStateOf(false) }

    // Nothing left to remove - drop back out of delete mode.
    LaunchedEffect(logs.isEmpty()) {
        if (logs.isEmpty()) deleteMode = false
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Column(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth()
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                // Only the chevron + title toggle expansion, so the icons on the right
                // stay independently tappable.
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .weight(1f)
                        .clickable { expanded = !expanded }
                ) {
                    Icon(
                        imageVector = if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                        contentDescription = if (expanded) "Collapse $title" else "Expand $title",
                        modifier = Modifier.size(28.dp),
                        tint = MaterialTheme.colorScheme.onSurface
                    )

                    Spacer(modifier = Modifier.width(16.dp))

                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Medium
                        ),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                Box {
                    IconButton(
                        onClick = { menuOpen = true },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.MoreHoriz,
                            contentDescription = "$title options",
                            tint = if (deleteMode) {
                                MaterialTheme.colorScheme.error
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    DropdownMenu(
                        expanded = menuOpen,
                        onDismissRequest = { menuOpen = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Save as meal") },
                            leadingIcon = { Icon(Icons.Default.Bookmark, contentDescription = null) },
                            enabled = false, // arrives with the Create Meal screen
                            onClick = { menuOpen = false }
                        )
                        DropdownMenuItem(
                            text = { Text(if (deleteMode) "Done removing" else "Remove food") },
                            leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null) },
                            enabled = logs.isNotEmpty() || deleteMode,
                            onClick = {
                                deleteMode = !deleteMode
                                if (deleteMode) expanded = true
                                menuOpen = false
                            }
                        )
                    }
                }

                Spacer(modifier = Modifier.width(4.dp))

                Surface(
                    shape = CircleShape,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    modifier = Modifier
                        .size(32.dp)
                        .clickable(onClick = onAddClick)
                        .semantics { contentDescription = "Add food to $title" },
                    color = Color.Transparent
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            val totalProtein = logs.sumOf { it.protein.toDouble() }.toFloat()
            val totalCarbs = logs.sumOf { it.carbs.toDouble() }.toFloat()
            val totalFat = logs.sumOf { it.fat.toDouble() }.toFloat()
            val totalCalories = logs.sumOf { it.calories }

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(start = 44.dp)
            ) {
                MacroChip(letter = "P", color = MacroColors.Protein, value = totalProtein.toInt().toString())
                MacroChip(letter = "C", color = MacroColors.Carbs, value = totalCarbs.toInt().toString())
                MacroChip(letter = "F", color = MacroColors.Fat, value = totalFat.toInt().toString())
                MacroChip(letter = "Cal", color = MacroColors.Calories, value = totalCalories.toString())
            }

            AnimatedVisibility(visible = expanded) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp, start = 44.dp)
                ) {
                    if (logs.isEmpty()) {
                        Text(
                            text = "Tap + to add your first ${title.lowercase()} item.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        logs.forEach { log ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = log.foodName,
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.weight(1f)
                                )
                                Text(
                                    text = "${log.calories} kcal",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                if (deleteMode) {
                                    Spacer(modifier = Modifier.width(8.dp))
                                    IconButton(
                                        onClick = { onDeleteLog(log) },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Delete,
                                            contentDescription = "Remove ${log.foodName}",
                                            tint = MaterialTheme.colorScheme.error,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun MacroChip(letter: String, color: Color, value: String) {
    Surface(
        modifier = Modifier.height(24.dp),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        color = MaterialTheme.colorScheme.surface
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = letter,
                color = color,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = value,
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 12.sp
            )
        }
    }
}
