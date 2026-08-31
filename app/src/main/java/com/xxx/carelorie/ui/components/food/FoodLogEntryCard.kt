package com.xxx.carelorie.ui.components.food

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xxx.carelorie.ui.theme.MacroColors
import com.xxx.carelorie.data.remote.RemoteFoodLog

@Composable
fun FoodLogEntryCard(
    log: RemoteFoodLog,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = log.foodName,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = log.mealType,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                MacroCompactInfo(label = "P", value = "${log.protein.toInt()}g", color = MacroColors.Protein)
                MacroCompactInfo(label = "C", value = "${log.carbs.toInt()}g", color = MacroColors.Carbs)
                MacroCompactInfo(label = "F", value = "${log.fat.toInt()}g", color = MacroColors.Fat)
                MacroCompactInfo(label = "T", value = "${log.calories}", color = MacroColors.Calories)
            }
        }
    }
}

@Composable
fun MacroCompactInfo(label: String, value: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = label,
            color = color,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = value,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium
        )
    }
}
