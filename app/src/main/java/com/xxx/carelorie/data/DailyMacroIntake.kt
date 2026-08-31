package com.xxx.carelorie.data

import java.time.LocalDate

/**
 * One day's macronutrient total, as shown on the dashboard chart.
 *
 * Protein, carbs and fat are summed from the food log. Calories are now taken from the stored
 * values on each log entry instead of being re-derived from the macros, because the source data
 * (APIs, AI estimates, user-entered foods) may not round-trip exactly through the 4/4/9 formula.
 * Using the stored calories keeps the dashboard total identical to the food log total.
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
    val fat: Float,     // grams
    val calories: Int
) {
    /** Fallback for callers that only have macros; kept inside the module. */
    constructor(date: LocalDate, protein: Float, carbs: Float, fat: Float) : this(
        date,
        protein,
        carbs,
        fat,
        calories = (protein * 4 + carbs * 4 + fat * 9).toInt()
    )
}
