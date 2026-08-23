package com.xxx.carelorie.data

/**
 * Daily nutrition targets.
 *
 * These numbers were previously hardcoded separately in MacroRow, FoodLogViewModel and the
 * dashboard, so they could drift apart. One definition means the dashboard and the food log
 * always agree.
 *
 * They are placeholders until the onboarding flow lands, at which point [DEFAULT] gets
 * replaced by a value calculated from the user's height, weight, age, activity level and goal
 * (Mifflin-St Jeor). Nothing else has to change when that happens.
 */
data class NutritionTargets(
    val calories: Int,
    val proteinGrams: Float,
    val carbsGrams: Float,
    val fatGrams: Float
) {
    companion object {
        val DEFAULT = NutritionTargets(
            calories = 2000,
            proteinGrams = 120f,
            carbsGrams = 200f,
            fatGrams = 65f
        )
    }
}
