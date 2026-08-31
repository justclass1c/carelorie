package com.xxx.carelorie.ui.components.dashboard

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xxx.carelorie.ui.theme.MacroColors
import com.xxx.carelorie.data.DailyMacroIntake
import com.xxx.carelorie.data.NutritionTargets
import java.time.LocalDate

@Composable
fun WeeklyMacroChart(
    data: List<DailyMacroIntake>,
    targets: NutritionTargets,
    modifier: Modifier = Modifier
) {
    // Scale to the day the user actually ate most, but never below their own combined target —
    // so a normal week fills a sensible portion of the bar and a heavy day still fits instead of
    // clipping. The old fixed 300 g did neither.
    val targetTotal = targets.proteinGrams + targets.carbsGrams + targets.fatGrams
    val busiestDay = data.maxOfOrNull { it.protein + it.carbs + it.fat } ?: 0f
    val chartMax = maxOf(targetTotal, busiestDay, 1f)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp)
    ) {
        Text(
            text = "Calendar",
            style = MaterialTheme.typography.headlineSmall.copy(fontSize = 20.sp),
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(bottom = 24.dp)
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(180.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom
        ) {
            val today = LocalDate.now()
            // Defensive check for empty or null-like data
            if (data.isNotEmpty()) {
                data.forEach { intake ->
                    val isToday = intake.date == today
                    DayBarColumn(intake = intake, isToday = isToday, chartMax = chartMax)
                }
            } else {
                Text(
                    "No data available",
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}

@Composable
fun DayBarColumn(intake: DailyMacroIntake, isToday: Boolean, chartMax: Float) {
    // From the shared key, not repeated here. These four were duplicated in this file while
    // MacroColors claimed to be the single source, so the chart and the legend beside it could
    // drift apart — and neither adapted to dark mode.
    val proteinColor = MacroColors.Protein
    val carbsColor = MacroColors.Carbs
    val fatColor = MacroColors.Fat
    val todayColor = MacroColors.Today
    val trackColor = MaterialTheme.colorScheme.surfaceVariant

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.width(32.dp)
    ) {
        // The Stacked Bar
        Box(
            modifier = Modifier
                .width(24.dp)
                .height(140.dp)
                // A filled track instead of an outlined box: the bar now reads as filling
                // something rather than floating inside a wireframe.
                .clip(RoundedCornerShape(16.dp))
                .background(trackColor)
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val scale = size.height / chartMax
                
                val pHeight = intake.protein * scale
                val cHeight = intake.carbs * scale
                val fHeight = intake.fat * scale

                // Draw Fat (top of stack)
                drawRect(
                    color = fatColor,
                    topLeft = Offset(0f, (size.height - pHeight - cHeight - fHeight).coerceAtLeast(0f)),
                    size = Size(size.width, fHeight.coerceAtLeast(0f))
                )
                // Draw Carbs (middle)
                drawRect(
                    color = carbsColor,
                    topLeft = Offset(0f, (size.height - pHeight - cHeight).coerceAtLeast(0f)),
                    size = Size(size.width, cHeight.coerceAtLeast(0f))
                )
                // Draw Protein (bottom)
                drawRect(
                    color = proteinColor,
                    topLeft = Offset(0f, (size.height - pHeight).coerceAtLeast(0f)),
                    size = Size(size.width, pHeight.coerceAtLeast(0f))
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Day Label - Simplified to avoid experimental API and non-observable locale crashes
        val dayInitial = try {
            // Using a simple substring of the English name as a stable fallback
            intake.date.dayOfWeek.name.take(1)
        } catch (e: Exception) {
            "?"
        }
        
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(28.dp)
                .then(
                    if (isToday) {
                        Modifier.background(todayColor.copy(alpha = 0.14f), CircleShape)
                    } else {
                        Modifier
                    }
                )
        ) {
            Text(
                text = dayInitial,
                style = MaterialTheme.typography.bodyLarge.copy(
                    fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
                    fontSize = 16.sp
                ),
                color = if (isToday) todayColor else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
