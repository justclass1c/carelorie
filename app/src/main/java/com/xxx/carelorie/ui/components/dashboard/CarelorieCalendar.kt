package com.xxx.carelorie.ui.components.dashboard

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale

@Composable
fun CarelorieCalendar(
    modifier: Modifier = Modifier,
    currentMonth: YearMonth,
    trackedDates: Set<LocalDate>,
    onMonthChange: (YearMonth) -> Unit
) {
    val daysInMonth = currentMonth.lengthOfMonth()
    val firstDayOfMonth = currentMonth.atDay(1)
    val dayOfWeekOffset = firstDayOfMonth.dayOfWeek.value % 7 // 0 for Sunday if we start week on Sunday

    val days = (1..daysInMonth).map { currentMonth.atDay(it) }
    val placeholders = (0 until dayOfWeekOffset).map { null }
    val allCells = placeholders + days

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp)
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { onMonthChange(currentMonth.minusMonths(1)) }) {
                Icon(Icons.Default.ChevronLeft, contentDescription = "Previous Month")
            }
            
            Text(
                text = "${currentMonth.month.getDisplayName(TextStyle.FULL, Locale.getDefault())} ${currentMonth.year}",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 16.dp)
            )

            IconButton(onClick = { onMonthChange(currentMonth.plusMonths(1)) }) {
                Icon(Icons.Default.ChevronRight, contentDescription = "Next Month")
            }
        }

        Spacer(Modifier.height(8.dp))

        // Days of week header
        Row(modifier = Modifier.fillMaxWidth()) {
            listOf("S", "M", "T", "W", "T", "F", "S").forEach { day ->
                Text(
                    text = day,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Spacer(Modifier.height(4.dp))

        // Calendar Grid
        val cellHeight = 40.dp
        val cellModifier = Modifier
            .height(cellHeight)
            .fillMaxWidth()

        // The grid needs an explicit height: nesting it in a scrolling column would otherwise
        // hand it an infinite height constraint. Derived from the rows actually present rather
        // than hardcoded, so a five-row month doesn't leave a blank row and changing the cell
        // height stays self-consistent.
        val rowCount = (allCells.size + 6) / 7

        LazyVerticalGrid(
            columns = GridCells.Fixed(7),
            modifier = Modifier.height(cellHeight * rowCount),
            userScrollEnabled = false
        ) {
            items(allCells) { date ->
                if (date != null) {
                    val isTracked = trackedDates.contains(date)
                    Text(
                        text = date.dayOfMonth.toString(),
                        modifier = cellModifier.padding(4.dp),
                        textAlign = TextAlign.Center,
                        color = if (isTracked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onBackground,
                        fontWeight = if (isTracked) FontWeight.Bold else FontWeight.Normal
                    )
                } else {
                    Spacer(modifier = cellModifier)
                }
            }
        }
        
        // Legend
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Indicator("Untracked", MaterialTheme.colorScheme.onBackground)
            Spacer(Modifier.size(16.dp))
            Indicator("Tracked", MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
fun Indicator(label: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        androidx.compose.foundation.Canvas(modifier = Modifier.size(12.dp)) {
            drawCircle(color = color)
        }
        Text(
            text = label,
            modifier = Modifier.padding(start = 4.dp),
            fontSize = 12.sp,
            color = color
        )
    }
}
