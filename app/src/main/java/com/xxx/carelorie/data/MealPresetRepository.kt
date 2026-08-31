package com.xxx.carelorie.data

import com.xxx.carelorie.data.remote.RemoteFoodLog
import com.xxx.carelorie.data.remote.RemoteFoodPreset
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID

/**
 * Saved meals: create one from what is already logged, then log the whole thing again later.
 *
 * Writes go straight to Room. Unlike food logs there is no offline queue to worry about, because
 * saved meals never leave the device — see [MealPresetEntity].
 */
class MealPresetRepository(
    private val mealPresetDao: MealPresetDao,
    private val foodRepository: FoodRepository
) {

    fun observeMeals(userId: String): Flow<List<MealPresetWithItems>> =
        mealPresetDao.observeForUser(userId)

    suspend fun nameIsTaken(userId: String, name: String): Boolean =
        mealPresetDao.countByName(userId, name.trim()) > 0

    /**
     * Saves [logs] as a reusable meal.
     *
     * Takes food logs rather than presets because the dashboard's "Save as meal" acts on what the
     * user actually ate, quantities included.
     */
    suspend fun saveFromLogs(
        userId: String,
        name: String,
        mealType: String,
        logs: List<RemoteFoodLog>
    ): String {
        val mealId = UUID.randomUUID().toString()
        val meal = MealPresetEntity(
            localId = mealId,
            ownerUserId = userId,
            name = name.trim(),
            mealType = mealType,
            createdAt = LocalDateTime.now().toString()
        )
        val items = logs.map { log ->
            MealPresetItemEntity(
                mealPresetId = mealId,
                foodName = log.foodName,
                calories = log.calories,
                protein = log.protein,
                carbs = log.carbs,
                fat = log.fat,
                quantity = log.quantity
            )
        }
        mealPresetDao.upsert(meal, items)
        return mealId
    }

    suspend fun rename(meal: MealPresetWithItems, name: String) {
        mealPresetDao.upsert(meal.meal.copy(name = name.trim()), meal.items)
    }

    suspend fun delete(localId: String) = mealPresetDao.delete(localId)

    /**
     * Writes every food in a saved meal into the diary.
     *
     * Returns how many entries were added. Goes through [FoodRepository.logFood] one item at a
     * time so each entry joins the normal offline queue and syncs like any other.
     */
    suspend fun logMeal(
        userId: String,
        meal: MealPresetWithItems,
        mealType: String,
        date: LocalDate
    ): Int {
        for (item in meal.items) {
            // The stored macros are already the total for `quantity` servings, which is the
            // convention FoodLogEntity and every other logFood caller use.
            foodRepository.logFood(
                userId = userId,
                mealType = mealType,
                food = RemoteFoodPreset(
                    userId = userId,
                    name = item.foodName,
                    calories = item.calories,
                    protein = item.protein,
                    carbs = item.carbs,
                    fat = item.fat
                ),
                quantity = item.quantity,
                date = date,
                sourcePresetId = item.sourcePresetId
            )
        }
        return meal.items.size
    }
}
