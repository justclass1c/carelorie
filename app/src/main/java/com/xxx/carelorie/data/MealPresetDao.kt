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
    @Query(
        """
        SELECT * FROM meal_presets
        WHERE ownerUserId = :userId AND isPendingDelete = 0
        ORDER BY createdAt DESC
        """
    )
    fun observeForUser(userId: String): Flow<List<MealPresetWithItems>>

    @Query(
        """
        SELECT COUNT(*) FROM meal_presets
        WHERE ownerUserId = :userId AND name = :name AND isPendingDelete = 0
        """
    )
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

    @Query("SELECT localId FROM meal_presets WHERE ownerUserId = :userId")
    suspend fun localIdsForUser(userId: String): List<String>

    /** Every saved meal belonging to a user, items included, for account deletion. */
    @Transaction
    suspend fun deleteAllForUser(userId: String) {
        for (localId in localIdsForUser(userId)) delete(localId)
    }

    // ---------------------------------------------------------------- sync bookkeeping

    /**
     * Every meal waiting to be uploaded, whoever owns it.
     *
     * Not filtered by user, matching `FoodLogDao.getUnsynced`: a meal saved offline should still
     * reach the server even if somebody else has signed in on the phone since. Each row carries
     * its own `ownerUserId`, so it uploads against the right account either way.
     */
    @Transaction
    @Query("SELECT * FROM meal_presets WHERE isSynced = 0 AND isPendingDelete = 0")
    suspend fun getUnsyncedAcrossUsers(): List<MealPresetWithItems>

    @Query("SELECT * FROM meal_presets WHERE ownerUserId = :userId AND isPendingDelete = 1")
    suspend fun getPendingDeletes(userId: String): List<MealPresetEntity>

    @Query("UPDATE meal_presets SET isSynced = 1, wasSynced = 1 WHERE localId = :localId")
    suspend fun markSynced(localId: String)

    @Query("UPDATE meal_presets SET isPendingDelete = 1, isSynced = 0 WHERE localId = :localId")
    suspend fun markPendingDelete(localId: String)

    @Query("SELECT * FROM meal_presets WHERE localId = :localId LIMIT 1")
    suspend fun getByLocalId(localId: String): MealPresetEntity?

    @Query(
        """
        SELECT localId FROM meal_presets
        WHERE ownerUserId = :userId AND isSynced = 1 AND isPendingDelete = 0
        """
    )
    suspend fun syncedLocalIdsForUser(userId: String): List<String>

    /**
     * Clears synced meals before re-inserting the server's copy.
     *
     * Anything unsynced or queued for deletion is left alone, so a meal saved offline survives a
     * refresh instead of being replaced by a server that has never heard of it.
     *
     * Goes through [delete] rather than one bulk statement because the items live in their own
     * table with no cascade on the Room side — deleting only the meals would leave their foods
     * behind as rows nothing can reach.
     */
    @Transaction
    suspend fun clearSyncedForUser(userId: String) {
        for (localId in syncedLocalIdsForUser(userId)) delete(localId)
    }
}
