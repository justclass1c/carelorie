package com.xxx.carelorie.ui.components.dashboard

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
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
import com.xxx.carelorie.data.DailyMacroIntake
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.*
import androidx.compose.ui.platform.LocalLocale

@Composable
fun WeeklyMacroChart(data: List<DailyMacroIntake>) {
    Column(
        modifier = Modifier
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
            data.forEach { intake ->
                val isToday = intake.date == today
                DayBarColumn(intake = intake, isToday = isToday)
            }
        }
    }
}

@Composable
fun DayBarColumn(intake: DailyMacroIntake, isToday: Boolean) {
    val proteinColor = Color(0xFFE91E63) // Pink
    val carbsColor = Color(0xFF2196F3)   // Blue
    val fatColor = Color(0xFF4CAF50)     // Green
    val borderColor = MaterialTheme.colorScheme.outline

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.width(32.dp)
    ) {
        // The Stacked Bar
        Box(
            modifier = Modifier
                .width(24.dp)
                .height(140.dp)
                .border(BorderStroke(1.dp, borderColor))
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val total = 300f // Assume max 300g for visualization scaling
                val scale = size.height / total
                
                val pHeight = intake.protein * scale
                val cHeight = intake.carbs * scale
                val fHeight = intake.fat * scale

                // Draw Fat (top of stack)
                drawRect(
                    color = fatColor,
                    topLeft = Offset(0f, size.height - pHeight - cHeight - fHeight),
                    size = Size(size.width, fHeight)
                )
                // Draw Carbs (middle)
                drawRect(
                    color = carbsColor,
                    topLeft = Offset(0f, size.height - pHeight - cHeight),
                    size = Size(size.width, cHeight)
                )
                // Draw Protein (bottom)
                drawRect(
                    color = proteinColor,
                    topLeft = Offset(0f, size.height - pHeight),
                    size = Size(size.width, pHeight)
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Day Label
        val dayInitial = intake.date.dayOfWeek.getDisplayName(TextStyle.NARROW, LocalLocale.current.platformLocale)
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(28.dp)
                .then(
                    if (isToday) Modifier.border(1.dp, Color(0xFF2196F3), CircleShape) 
                    else Modifier
                )
        ) {
            Text(
                text = dayInitial,
                style = MaterialTheme.typography.bodyLarge.copy(
                    fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
                    fontSize = 16.sp
                ),
                color = if (isToday) Color(0xFF2196F3) else MaterialTheme.colorScheme.onSurface
            )
        }
    }
}
