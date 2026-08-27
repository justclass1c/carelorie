package com.xxx.carelorie.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * The macro colour key used across the whole app.
 *
 * These four values were previously copy-pasted into MealSection, MacroRow, FoodLogScreen,
 * FoodItemCard and FoodLogEntryCard. Keeping them in one place means the legend stays
 * consistent everywhere and there is a single line to change.
 */
object MacroColors {
    val Protein = Color(0xFFE91E63) // pink
    val Carbs = Color(0xFF2196F3)   // blue
    val Fat = Color(0xFF4CAF50)     // green
    val Calories = Color(0xFFFF9800) // amber
}
