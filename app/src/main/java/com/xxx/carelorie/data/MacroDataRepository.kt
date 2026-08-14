package com.xxx.carelorie.data

import java.time.LocalDate

data class DailyMacroIntake(
    val date: LocalDate,
    val protein: Float, // grams
    val carbs: Float,   // grams
    val fat: Float      // grams
) {
    val calories: Int
        get() = (protein * 4 + carbs * 4 + fat * 9).toInt()
}

class MacroDataRepository {
    
    /**
     * Retrieves the macro intake data for the past 7 days.
     * In the future, this should query the Room database using the logged-in user's ID.
     */
    fun fetchWeeklyMacroIntake(): List<DailyMacroIntake> {
        val today = LocalDate.now()
        // Returning dummy data for visualization purposes
        return (0..6).map { i ->
            val date = today.minusDays(i.toLong())
            DailyMacroIntake(
                date = date,
                protein = (20..80).random().toFloat(),
                carbs = (50..150).random().toFloat(),
                fat = (10..60).random().toFloat()
            )
        }.reversed()
    }
}
