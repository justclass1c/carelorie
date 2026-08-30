package com.xxx.carelorie.ui.components.dashboard

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.xxx.carelorie.data.DailyMacroIntake
import com.xxx.carelorie.data.NutritionTargets
import com.xxx.carelorie.ui.theme.overLimitColor

@Composable
fun MacroRow(todayIntake: DailyMacroIntake, targets: NutritionTargets) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        MacroCard("Protein", todayIntake.protein, targets.proteinGrams, Modifier.weight(1f))
        MacroCard("Carbs", todayIntake.carbs, targets.carbsGrams, Modifier.weight(1f))
        MacroCard("Fat", todayIntake.fat, targets.fatGrams, Modifier.weight(1f))
        MacroCard("Calories", todayIntake.calories.toFloat(), targets.calories.toFloat(), Modifier.weight(1f))
    }
}

@Composable
fun MacroCard(macro: String, value: Float, maxValue: Float, modifier: Modifier = Modifier) {
    val unit = if (macro == "Calories") "kcal" else "g"
    val containerColor = overLimitColor(value, maxValue) ?: MaterialTheme.colorScheme.surface

    Card(
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(
            containerColor = containerColor
        ),
        modifier = modifier.aspectRatio(1f),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(2.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = macro,
                style = MaterialTheme.typography.labelSmall
            )

            Text(
                text = formatNumber(value),
                style = MaterialTheme.typography.titleMedium
            )

            Text(
                text = "of ${formatNumber(maxValue)}$unit",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

        }
    }
}

private fun formatNumber(value: Float): String =
    if (value == value.toInt().toFloat()) value.toInt().toString() else value.toString()
