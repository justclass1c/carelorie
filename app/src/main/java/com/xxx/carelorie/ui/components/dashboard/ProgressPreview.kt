package com.xxx.carelorie.ui.components.dashboard

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.xxx.carelorie.ui.components.CarelorieCard
import com.xxx.carelorie.data.DailyMacroIntake
import com.xxx.carelorie.data.NutritionTargets

@Composable
fun ProgressPreview(
    weeklyData: List<DailyMacroIntake>,
    targets: NutritionTargets,
    modifier: Modifier = Modifier
) {
    CarelorieCard(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        contentPadding = PaddingValues(0.dp)
    ) {
        WeeklyMacroChart(data = weeklyData, targets = targets)
    }
}
