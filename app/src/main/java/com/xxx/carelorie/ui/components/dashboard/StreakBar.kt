package com.xxx.carelorie.ui.components.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import com.xxx.carelorie.ui.components.CarelorieCard
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun StreakBar(streakCount: Int) {
    val configuration = LocalConfiguration.current
    val isTablet = configuration.screenWidthDp >= 600

    CarelorieCard(modifier = Modifier.padding(horizontal = 4.dp)) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
        Text(
            text = if (streakCount == 1) "1 day streak" else "$streakCount day streak",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(bottom = 10.dp)
        )
        
        if (isTablet) {
            // Tablet view: One single centered line
            StreakRow(count = streakCount, startIdx = 0, limit = 30)
        } else {
            // Phone view: Two centered rows
            val boxesPerRow = 15
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                StreakRow(count = streakCount, startIdx = 0, limit = boxesPerRow)
                StreakRow(count = streakCount, startIdx = boxesPerRow, limit = boxesPerRow)
            }
        }
        }
    }
}

/**
 * One row of the streak grid.
 *
 * Rounded, filled cells with an unfilled state that is a faint tint rather than an outlined empty
 * square. Thirty hairline boxes read as graph paper; thirty soft dots read as progress.
 */
@Composable
fun StreakRow(count: Int, startIdx: Int, limit: Int) {
    val filled = MaterialTheme.colorScheme.primary
    val empty = MaterialTheme.colorScheme.surfaceVariant

    Row(
        modifier = Modifier.padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        for (i in 0 until limit) {
            val isFilled = (startIdx + i) < count
            Box(
                modifier = Modifier
                    .size(14.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (isFilled) filled else empty)
            )
        }
    }
}
