package com.xxx.carelorie.ui.components.dashboard

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xxx.carelorie.data.WeightRecord
import java.time.LocalDate
import java.time.YearMonth

@Composable
fun WeightGraph(
    modifier: Modifier = Modifier,
    yearMonth: YearMonth,
    weightHistory: List<WeightRecord>
) {
    val monthPrefix = yearMonth.toString() // YYYY-MM
    val textMeasurer = rememberTextMeasurer()
    val primaryColor = MaterialTheme.colorScheme.primary
    val outlineColor = MaterialTheme.colorScheme.outline
    val onSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant
    val onBackground = MaterialTheme.colorScheme.onBackground

    val monthlyWeights = weightHistory
        .filter { it.date.startsWith(monthPrefix) }
        .sortedBy { it.date }

    Box(
        modifier = modifier
            .padding(16.dp)
            .border(1.dp, outlineColor)
            .padding(8.dp)
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val width = size.width
            val height = size.height
            
            // Use fixed DP values for consistent padding across devices
            val paddingLeft = 60.dp.toPx()
            val paddingBottom = 40.dp.toPx()
            val paddingTop = 40.dp.toPx()
            val arrowSize = 10.dp.toPx()
            
            if (width <= 0 || height <= 0) return@Canvas

            // Draw Y and X axes as a single connected path for visibility
            val axisPath = Path().apply {
                // Start at top of Y-axis
                moveTo(paddingLeft, paddingTop)
                // Draw down to origin (bottom-left)
                lineTo(paddingLeft, height - paddingBottom)
                // Draw right to end of X-axis
                lineTo(width - arrowSize, height - paddingBottom)
            }
            
            drawPath(
                path = axisPath,
                color = outlineColor,
                style = Stroke(width = 3.dp.toPx()) // Thicker axis lines
            )

            // Y-axis arrow head (pointing up)
            val yArrow = Path().apply {
                moveTo(paddingLeft - arrowSize / 2, paddingTop + arrowSize)
                lineTo(paddingLeft, paddingTop)
                lineTo(paddingLeft + arrowSize / 2, paddingTop + arrowSize)
            }
            drawPath(yArrow, color = outlineColor, style = Stroke(width = 3.dp.toPx()))

            // X-axis arrow head (pointing right)
            val xArrow = Path().apply {
                moveTo(width - arrowSize - arrowSize, height - paddingBottom - arrowSize / 2)
                lineTo(width - arrowSize, height - paddingBottom)
                lineTo(width - arrowSize - arrowSize, height - paddingBottom + arrowSize / 2)
            }
            drawPath(xArrow, color = outlineColor, style = Stroke(width = 3.dp.toPx()))

            // Axis Labels
            drawText(
                textMeasurer = textMeasurer,
                text = "Weight (kg)",
                topLeft = Offset(paddingLeft - 60.dp.toPx(), paddingTop - 35.dp.toPx()),
                style = TextStyle(fontSize = 11.sp, color = onBackground, fontWeight = FontWeight.Bold)
            )
            drawText(
                textMeasurer = textMeasurer,
                text = "Days",
                topLeft = Offset(width - 40.dp.toPx(), height - paddingBottom + 10.dp.toPx()),
                style = TextStyle(fontSize = 11.sp, color = onBackground, fontWeight = FontWeight.Bold)
            )

            if (monthlyWeights.isNotEmpty()) {
                val minWeight = monthlyWeights.minOf { it.weight } * 0.95f
                val maxWeight = monthlyWeights.maxOf { it.weight } * 1.05f
                val weightRange = if (maxWeight == minWeight) 1f else maxWeight - minWeight
                
                val graphWidth = width - paddingLeft - arrowSize - 20f
                val graphHeight = height - paddingBottom - paddingTop - 40f
                
                val firstDay = LocalDate.parse(monthlyWeights.first().date).dayOfMonth
                val lastDay = LocalDate.parse(monthlyWeights.last().date).dayOfMonth
                val dayRange = (lastDay - firstDay).toFloat()
                
                // Add margin between dots and the Y-axis/arrow
                val dataMargin = 16.dp.toPx()
                
                val points = monthlyWeights.map { record ->
                    val dateObj = LocalDate.parse(record.date)
                    val day = dateObj.dayOfMonth
                    val x = if (dayRange > 0) {
                        paddingLeft + dataMargin + ((day - firstDay) / dayRange) * (graphWidth - dataMargin * 2)
                    } else {
                        paddingLeft + dataMargin
                    }
                    val y = (height - paddingBottom) - ((record.weight - minWeight) / weightRange) * graphHeight
                    Triple(Offset(x, y), day, record.weight)
                }

                // Draw Y-axis weight ticks (min, mid, max)
                val rawMin = monthlyWeights.minOf { it.weight }
                val rawMax = monthlyWeights.maxOf { it.weight }
                val ticks = listOf(rawMin, (rawMin + rawMax) / 2f, rawMax).distinct()
                ticks.forEach { tickWeight ->
                    val y = (height - paddingBottom) - ((tickWeight - minWeight) / weightRange) * graphHeight
                    val weightStr = if (tickWeight == tickWeight.toInt().toFloat()) "${tickWeight.toInt()}kg" else "%.1fkg".format(tickWeight)
                    drawText(
                        textMeasurer = textMeasurer,
                        text = weightStr,
                        topLeft = Offset(paddingLeft - 52.dp.toPx(), y - 6.dp.toPx()),
                        style = TextStyle(fontSize = 9.sp, color = onSurfaceVariant)
                    )
                }

                if (points.size > 1) {
                    val path = Path()
                    path.moveTo(points[0].first.x, points[0].first.y)
                    
                    for (i in 1 until points.size) {
                        val p0 = points[i - 1].first
                        val p1 = points[i].first
                        
                        val controlPoint1 = Offset(p0.x + (p1.x - p0.x) / 2, p0.y)
                        val controlPoint2 = Offset(p0.x + (p1.x - p0.x) / 2, p1.y)
                        
                        path.cubicTo(
                            controlPoint1.x, controlPoint1.y,
                            controlPoint2.x, controlPoint2.y,
                            p1.x, p1.y
                        )
                    }
                    
                    drawPath(
                        path = path,
                        color = primaryColor,
                        style = Stroke(width = 4f)
                    )
                }
                
                // Draw points and X-axis day labels
                points.forEach { (point, day, weight) ->
                    drawCircle(color = primaryColor, radius = 6f, center = point)
                    
                    // Draw day on X-axis
                    val dayStr = day.toString()
                    drawText(
                        textMeasurer = textMeasurer,
                        text = dayStr,
                        topLeft = Offset(point.x - 6.dp.toPx(), height - paddingBottom + 6.dp.toPx()),
                        style = TextStyle(fontSize = 9.sp, color = onSurfaceVariant, fontWeight = FontWeight.Bold)
                    )
                }
            }
        }
    }
}
