package com.xxx.carelorie.ui.components.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun StreakBar(streakCount: Int) {
    val configuration = LocalConfiguration.current
    val isTablet = configuration.screenWidthDp >= 600

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(4.dp))
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Current Streak: $streakCount days",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 8.dp)
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

@Composable
fun StreakRow(count: Int, startIdx: Int, limit: Int) {
    val primaryColor = MaterialTheme.colorScheme.primary
    val outlineColor = MaterialTheme.colorScheme.outline
    
    Row(
        modifier = Modifier.padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.Center
    ) {
        for (i in 0 until limit) {
            val currentIdx = startIdx + i
            val isFilled = currentIdx < count
            Box(
                modifier = Modifier
                    .size(20.dp)
                    .padding(2.dp)
                    .border(1.dp, outlineColor)
                    .background(if (isFilled) primaryColor else Color.Transparent)
            )
        }
    }
}
