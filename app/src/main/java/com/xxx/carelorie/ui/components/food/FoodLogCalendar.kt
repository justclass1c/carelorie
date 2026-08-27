package com.xxx.carelorie.ui.components.food

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter

private val MONTH_TITLE = DateTimeFormatter.ofPattern("MMMM yyyy")
private val WEEKDAY_LABELS = listOf("S", "M", "T", "W", "T", "F", "S")

/**
 * Month grid for picking a day in the food log.
 *
 * Days with entries are marked, today is outlined, and the selected day is filled — so the
 * calendar doubles as an at-a-glance record of which days were actually tracked.
 */
@Composable
fun FoodLogCalendar(
    month: YearMonth,
    selectedDate: LocalDate,
    loggedDates: Set<LocalDate>,
    onMonthChange: (YearMonth) -> Unit,
    onDateSelected: (LocalDate) -> Unit,
    modifier: Modifier = Modifier
) {
    val today = LocalDate.now()
    val firstOfMonth = month.atDay(1)
    // Sunday-first grid: DayOfWeek.SUNDAY.value is 7, so modulo 7 maps it to column 0.
    val leadingBlanks = firstOfMonth.dayOfWeek.value % 7
    val cells: List<LocalDate?> =
        List(leadingBlanks) { null } + (1..month.lengthOfMonth()).map { month.atDay(it) }

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            IconButton(onClick = { onMonthChange(month.minusMonths(1)) }) {
                Icon(
                    Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                    contentDescription = "Previous month"
                )
            }
            Text(
                text = month.format(MONTH_TITLE),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium
            )
            IconButton(onClick = { onMonthChange(month.plusMonths(1)) }) {
                Icon(
                    Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = "Next month"
                )
            }
        }

        Row(modifier = Modifier.fillMaxWidth()) {
            WEEKDAY_LABELS.forEach { label ->
                Text(
                    text = label,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Laid out as fixed rows of seven rather than a LazyVerticalGrid, so the calendar can
        // sit inside a scrolling column without nesting two scroll containers.
        cells.chunked(7).forEach { week ->
            Row(modifier = Modifier.fillMaxWidth()) {
                week.forEach { date ->
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .aspectRatio(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        if (date != null) {
                            DayCell(
                                date = date,
                                isSelected = date == selectedDate,
                                isToday = date == today,
                                hasEntries = loggedDates.contains(date),
                                isFuture = date.isAfter(today),
                                onClick = { onDateSelected(date) }
                            )
                        }
                    }
                }
                // Pad the final short week so cells keep their width.
                repeat(7 - week.size) {
                    Box(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun DayCell(
    date: LocalDate,
    isSelected: Boolean,
    isToday: Boolean,
    hasEntries: Boolean,
    isFuture: Boolean,
    onClick: () -> Unit
) {
    val background = when {
        isSelected -> MaterialTheme.colorScheme.primary
        hasEntries -> MaterialTheme.colorScheme.primaryContainer
        else -> MaterialTheme.colorScheme.surface
    }
    val contentColor = when {
        isSelected -> MaterialTheme.colorScheme.onPrimary
        hasEntries -> MaterialTheme.colorScheme.onPrimaryContainer
        isFuture -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
        else -> MaterialTheme.colorScheme.onSurface
    }

    Box(
        modifier = Modifier
            .size(36.dp)
            .background(background, CircleShape)
            .then(
                if (isToday && !isSelected) {
                    Modifier.border(1.dp, MaterialTheme.colorScheme.primary, CircleShape)
                } else {
                    Modifier
                }
            )
            .clickable(enabled = !isFuture, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = date.dayOfMonth.toString(),
            color = contentColor,
            fontSize = 13.sp,
            fontWeight = if (isSelected || isToday) FontWeight.Bold else FontWeight.Normal
        )
    }
}
