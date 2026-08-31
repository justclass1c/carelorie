package com.xxx.carelorie.data

import java.time.LocalDate

/**
 * One day's macronutrient total, as shown on the dashboard chart.
 *
 * Calories are derived rather than stored, so they can never disagree with the macros they were
 * calculated from: 4 kcal per gram of protein and carbohydrate, 9 per gram of fat.
 *
 * This used to live alongside `MacroDataRepository`, which fetched the `macros` table from
 * Supabase. That repository was wired into DashboardViewModel but never actually called — every
 * figure on screen is summed from the food log instead — so it was removed and the model, which
 * is genuinely used, moved here.
 */
data class DailyMacroIntake(
    val date: LocalDate,
    val protein: Float, // grams
    val carbs: Float,   // grams
    val fat: Float      // grams
) {
    val calories: Int
        get() = (protein * 4 + carbs * 4 + fat * 9).toInt()
}
