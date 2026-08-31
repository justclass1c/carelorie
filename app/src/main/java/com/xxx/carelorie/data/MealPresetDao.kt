package com.xxx.carelorie.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface MealPresetDao {

    @Transaction
    @Query("SELECT * FROM meal_presets WHERE ownerUserId = :userId ORDER BY createdAt DESC")
    fun observeForUser(userId: String): Flow<List<MealPresetWithItems>>


    @Query("SELECT COUNT(*) FROM meal_presets WHERE ownerUserId = :userId AND name = :name")
    suspend fun countByName(userId: String, name: String): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMeal(meal: MealPresetEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertItems(items: List<MealPresetItemEntity>)

    @Query("DELETE FROM meal_preset_items WHERE mealPresetId = :mealPresetId")
    suspend fun deleteItemsFor(mealPresetId: String)

    @Query("DELETE FROM meal_presets WHERE localId = :localId")
    suspend fun deleteMeal(localId: String)

    /** Replaces a saved meal wholesale — the items are owned by the meal, never edited alone. */
    @Transaction
    suspend fun upsert(meal: MealPresetEntity, items: List<MealPresetItemEntity>) {
        insertMeal(meal)
        deleteItemsFor(meal.localId)
        insertItems(items)
    }

    @Transaction
    suspend fun delete(localId: String) {
        deleteItemsFor(localId)
        deleteMeal(localId)
    }
}
