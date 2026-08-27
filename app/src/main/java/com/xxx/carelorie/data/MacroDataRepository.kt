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
     * Macro intake for the past 7 days.
     *
     * Returns an empty list when there is nothing to show. It deliberately does NOT invent
     * placeholder values — a chart full of random numbers is indistinguishable from a bug.
     */
    suspend fun fetchWeeklyMacroIntake(userId: Int): List<DailyMacroIntake> {
        return try {
            remoteRepository.fetchWeeklyMacros(userId).map {
                DailyMacroIntake(
                    date = LocalDate.parse(it.date),
                    protein = it.protein,
                    carbs = it.carbs,
                    fat = it.fat
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
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
