package com.xxx.carelorie.data.local

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface FoodPresetDao {

    @Upsert
    suspend fun upsert(preset: FoodPresetEntity)

    @Upsert
    suspend fun upsertAll(presets: List<FoodPresetEntity>)

    /**
     * Everything the user can pick: their own foods plus the shared built-ins.
     * Their own come first so a personal copy sits above the dish it was copied from.
     */
    @Query(
        """
        SELECT * FROM food_presets
        WHERE (ownerUserId = :userId OR ownerUserId IS NULL) AND isPendingDelete = 0
        ORDER BY (ownerUserId IS NULL), name COLLATE NOCASE ASC
        """
    )
    fun observeForUser(userId: String): Flow<List<FoodPresetEntity>>

    @Query(
        """
        SELECT * FROM food_presets
        WHERE (ownerUserId = :userId OR ownerUserId IS NULL) AND isPendingDelete = 0
        ORDER BY (ownerUserId IS NULL), name COLLATE NOCASE ASC
        """
    )
    suspend fun getForUser(userId: String): List<FoodPresetEntity>

    @Query("SELECT * FROM food_presets WHERE localId = :localId LIMIT 1")
    suspend fun getByLocalId(localId: String): FoodPresetEntity?

    @Query("SELECT COUNT(*) FROM food_presets WHERE ownerUserId IS NULL")
    suspend fun countBuiltIns(): Int

    @Query("SELECT * FROM food_presets WHERE isSynced = 0 AND isPendingDelete = 0 AND ownerUserId IS NOT NULL")
    suspend fun getUnsynced(): List<FoodPresetEntity>

    @Query("SELECT * FROM food_presets WHERE isPendingDelete = 1")
    suspend fun getPendingDeletes(): List<FoodPresetEntity>

    @Query("UPDATE food_presets SET remoteId = :remoteId, isSynced = 1 WHERE localId = :localId")
    suspend fun markSynced(localId: String, remoteId: Int?)

    @Query("UPDATE food_presets SET isPendingDelete = 1, isSynced = 0 WHERE localId = :localId")
    suspend fun markPendingDelete(localId: String)

    @Query("DELETE FROM food_presets WHERE localId = :localId")
    suspend fun deleteByLocalId(localId: String)

    /**
     * Clears synced rows owned by this user before re-inserting the server copy.
     * Unsynced rows are left alone so nothing created offline is ever lost.
     */
    @Query(
        """
        DELETE FROM food_presets
        WHERE ownerUserId = :userId AND isSynced = 1 AND isPendingDelete = 0
        """
    )
    suspend fun clearSyncedForUser(userId: String)

    /**
     * Swaps this user's synced rows for the server's copy in one transaction.
     *
     * Doing the delete and the insert as two writes made Room invalidate `food_presets` twice,
     * so anything observing the list saw it empty and refill on every refresh. One transaction
     * is one invalidation, so the list only ever moves from the old state to the new one.
     */
    @Transaction
    suspend fun replaceSyncedForUser(userId: String, presets: List<FoodPresetEntity>) {
        clearSyncedForUser(userId)
        upsertAll(presets)
    }

    /**
     * Every food this user created, for account deletion.
     *
     * Matching on `ownerUserId = :userId` leaves the built-ins alone, since those carry a null
     * owner and are shared by everybody.
     */
    @Query("DELETE FROM food_presets WHERE ownerUserId = :userId")
    suspend fun deleteAllForUser(userId: String)
}
