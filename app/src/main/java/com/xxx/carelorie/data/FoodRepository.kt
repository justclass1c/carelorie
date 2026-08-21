package com.xxx.carelorie.data

import com.xxx.carelorie.data.remote.RemoteFoodLog
import com.xxx.carelorie.data.remote.RemoteFoodPreset
import com.xxx.carelorie.data.remote.SupabaseRepository
import java.time.LocalDateTime

class FoodRepository(private val supabaseRepository: SupabaseRepository) {

    suspend fun getFoodPresets(userId: Int): List<RemoteFoodPreset> {
        return try {
            val presets = supabaseRepository.fetchFoodPresets(userId)
            if (presets.isEmpty()) {
                // Seed default presets if none found in Supabase
                val defaultPresets = listOf(
                    RemoteFoodPreset(name = "Bowl of Rice", calories = 200, protein = 4f, carbs = 45f, fat = 0.5f),
                    RemoteFoodPreset(name = "Egg", calories = 70, protein = 6f, carbs = 0.6f, fat = 5f),
                    RemoteFoodPreset(name = "Chicken Breast", calories = 165, protein = 31f, carbs = 0f, fat = 3.6f),
                    RemoteFoodPreset(name = "Broccoli", calories = 55, protein = 3.7f, carbs = 11f, fat = 0.6f),
                    RemoteFoodPreset(name = "Orange Juice", calories = 110, protein = 2f, carbs = 26f, fat = 0.5f)
                )
                supabaseRepository.seedFoodPresets(defaultPresets)
                defaultPresets
            } else {
                presets
            }
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    suspend fun logFood(userId: Int, mealType: String, food: RemoteFoodPreset) {
        try {
            val log = RemoteFoodLog(
                userId = userId,
                mealType = mealType,
                foodName = food.name,
                calories = food.calories,
                protein = food.protein,
                carbs = food.carbs,
                fat = food.fat,
                createdAt = LocalDateTime.now().toString()
            )
            supabaseRepository.addFoodLog(log)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    suspend fun getDailyLogs(userId: Int, date: String): List<RemoteFoodLog> {
        return try {
            supabaseRepository.fetchFoodLogs(userId, date)
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    suspend fun getWeeklyLogs(userId: Int): List<RemoteFoodLog> {
        return try {
            val oneWeekAgo = java.time.LocalDate.now().minusDays(7).toString()
            supabaseRepository.fetchFoodLogsRange(userId, oneWeekAgo)
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    suspend fun getMonthlyLogs(userId: Int, yearMonth: java.time.YearMonth): List<RemoteFoodLog> {
        return try {
            val startDate = yearMonth.atDay(1).toString()
            supabaseRepository.fetchFoodLogsRange(userId, startDate)
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }
}
