package com.xxx.carelorie.ui.components.dashboard

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xxx.carelorie.data.remote.RemoteFoodLog

@Composable
fun MealSection(
    todayLogs: List<RemoteFoodLog>,
    onAddMealClick: (String) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        MealCard(
            title = "Breakfast",
            logs = todayLogs.filter { it.mealType.equals("Breakfast", ignoreCase = true) },
            onAddClick = { onAddMealClick("Breakfast") }
        )
        MealCard(
            title = "Lunch",
            logs = todayLogs.filter { it.mealType.equals("Lunch", ignoreCase = true) },
            onAddClick = { onAddMealClick("Lunch") }
        )
        MealCard(
            title = "Dinner",
            logs = todayLogs.filter { it.mealType.equals("Dinner", ignoreCase = true) },
            onAddClick = { onAddMealClick("Dinner") }
        )
    }
}

@Composable
fun MealCard(
    title: String,
    logs: List<RemoteFoodLog>,
    onAddClick: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { expanded = !expanded },
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
                Icon(
                    imageVector = if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = if (expanded) "Collapse" else "Expand",
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
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
                
                Icon(
                    imageVector = Icons.Default.MoreHoriz,
                    contentDescription = "Options",
                    tint = Color.Red, // Keeping as red per mockup
                    modifier = Modifier.size(24.dp)
                )
                
                Spacer(modifier = Modifier.width(12.dp))
                
                Surface(
                    shape = CircleShape,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    modifier = Modifier
                        .size(32.dp)
                        .clickable { onAddClick() },
                    color = Color.Transparent
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "Add Item",
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
            val totalCalories = logs.sumOf { it.calories.toDouble() }.toInt()

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(start = 44.dp)
            ) {
                MacroChip(letter = "P", color = Color(0xFFE91E63), value = totalProtein.toInt().toString())
                MacroChip(letter = "C", color = Color(0xFF2196F3), value = totalCarbs.toInt().toString())
                MacroChip(letter = "F", color = Color(0xFF4CAF50), value = totalFat.toInt().toString())
                MacroChip(letter = "C", color = Color(0xFFFF9800), value = totalCalories.toString())
            }
            
            AnimatedVisibility(visible = expanded) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp, start = 44.dp)
                ) {
                    if (logs.isEmpty()) {
                        Text(
                            text = "No items logged yet.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        logs.forEach { log ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = log.foodName,
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "${log.calories} kcal",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
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
        modifier = Modifier
            .height(24.dp),
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
