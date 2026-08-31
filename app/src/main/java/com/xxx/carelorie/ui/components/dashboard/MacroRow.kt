package com.xxx.carelorie.ui.components.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xxx.carelorie.data.DailyMacroIntake
import com.xxx.carelorie.data.NutritionTargets
import com.xxx.carelorie.ui.components.CarelorieCard
import com.xxx.carelorie.ui.theme.MacroColors
import com.xxx.carelorie.ui.theme.overLimitColor

@Composable
fun MacroRow(todayIntake: DailyMacroIntake, targets: NutritionTargets) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        MacroCard("Protein", todayIntake.protein, targets.proteinGrams, Modifier.weight(1f))
        MacroCard("Carbs", todayIntake.carbs, targets.carbsGrams, Modifier.weight(1f))
        MacroCard("Fat", todayIntake.fat, targets.fatGrams, Modifier.weight(1f))
        MacroCard("Calories", todayIntake.calories.toFloat(), targets.calories.toFloat(), Modifier.weight(1f))
    }
}

/**
 * One macro's standing for today.
 *
 * Going over the limit used to flood the whole tile with the warning colour, which made the number
 * on it unreadable at exactly the moment it mattered. The alert now lives in the value's own colour
 * and a tinted ring, so the tile stays legible and still reads as "over" at a glance.
 */
@Composable
fun MacroCard(macro: String, value: Float, maxValue: Float, modifier: Modifier = Modifier) {
    val unit = if (macro == "Calories") "kcal" else "g"
    val alert = overLimitColor(value, maxValue)
    val accent = when (macro) {
        "Protein" -> MacroColors.Protein
        "Carbs" -> MacroColors.Carbs
        "Fat" -> MacroColors.Fat
        else -> MacroColors.Calories
    }
    val valueColor = alert ?: MaterialTheme.colorScheme.onSurface

    CarelorieCard(
        modifier = modifier.aspectRatio(1f),
        shape = MaterialTheme.shapes.medium,
        contentPadding = PaddingValues(6.dp),
        // The caller already sized this: weight(1f) across the row, then a 1:1 ratio.
        fillWidth = false
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // A small colour key, so the tile is identifiable before the label is read.
            Box(
                modifier = Modifier
                    .size(width = 18.dp, height = 3.dp)
                    .clip(CircleShape)
                    .background(alert ?: accent)
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = macro,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
            Text(
                text = formatNumber(value),
                style = MaterialTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = (-0.4).sp
                ),
                color = valueColor,
                maxLines = 1
            )
            Text(
                text = "of ${formatNumber(maxValue)}$unit",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
        }
    }
}

private fun formatNumber(value: Float): String =
    if (value == value.toInt().toFloat()) value.toInt().toString() else value.toString()
