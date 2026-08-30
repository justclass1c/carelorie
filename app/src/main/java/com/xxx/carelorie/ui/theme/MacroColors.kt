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

    val Warning = Color(0xFFFFEB3B) // yellow: over the limit by less than 20%
    val Over = Color(0xFFF44336)    // red: over the limit by 20% or more
}

/**
 * Returns a warning colour when [current] has gone over [limit], or null while it is within
 * the limit. Yellow means under 20% over; red means 20% or more over.
 */
fun overLimitColor(current: Float, limit: Float): Color? {
    if (limit <= 0f || current <= limit) return null
    val overRatio = (current - limit) / limit
    return if (overRatio < 0.20f) MacroColors.Warning else MacroColors.Over
}
