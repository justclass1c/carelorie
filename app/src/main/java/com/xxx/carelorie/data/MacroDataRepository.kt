package com.xxx.carelorie.data

import com.xxx.carelorie.data.remote.RemoteMacroIntake
import com.xxx.carelorie.data.remote.SupabaseRepository
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

class MacroDataRepository(private val remoteRepository: SupabaseRepository) {
    
    /**
     * Retrieves the macro intake data for the past 7 days.
     * Tries to fetch from Supabase if possible.
     */
    suspend fun fetchWeeklyMacroIntake(userId: Int): List<DailyMacroIntake> {
        return try {
            val remoteData = remoteRepository.fetchWeeklyMacros(userId)
            if (remoteData.isEmpty()) {
                generateDummyData()
            } else {
                remoteData.map { 
                    DailyMacroIntake(
                        date = LocalDate.parse(it.date),
                        protein = it.protein,
                        carbs = it.carbs,
                        fat = it.fat
                    )
                }
            }
        } catch (e: Exception) {
            // Fallback to dummy data on error (e.g., no internet)
            generateDummyData()
        }
    }

    private fun generateDummyData(): List<DailyMacroIntake> {
        val today = LocalDate.now()
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

    suspend fun syncDailyMacros(userId: Int, intake: DailyMacroIntake) {
        val remote = RemoteMacroIntake(
            userId = userId,
            date = intake.date.toString(),
            protein = intake.protein,
            carbs = intake.carbs,
            fat = intake.fat
        )
        remoteRepository.saveDailyMacros(remote)
    }
}
