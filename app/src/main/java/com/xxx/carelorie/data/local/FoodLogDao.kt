package com.xxx.carelorie.data.local

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface FoodLogDao {

    @Upsert
    suspend fun upsert(entry: FoodLogEntity)

    @Upsert
    suspend fun upsertAll(entries: List<FoodLogEntity>)

    @Query(
        """
        SELECT * FROM food_log_entries
        WHERE userId = :userId AND logDate = :date AND isPendingDelete = 0
        ORDER BY loggedAt ASC
        """
    )
    fun observeForDate(userId: String, date: String): Flow<List<FoodLogEntity>>

    @Query(
        """
        SELECT * FROM food_log_entries
        WHERE userId = :userId AND logDate BETWEEN :start AND :end AND isPendingDelete = 0
        ORDER BY loggedAt ASC
        """
    )
    fun observeBetween(userId: String, start: String, end: String): Flow<List<FoodLogEntity>>

    /** Distinct days that have at least one entry — drives the calendar dots. */
    @Query(
        """
        SELECT DISTINCT logDate FROM food_log_entries
        WHERE userId = :userId AND isPendingDelete = 0
        ORDER BY logDate ASC
        """
    )
    fun observeLoggedDates(userId: String): Flow<List<String>>

    @Query(
        """
        SELECT * FROM food_log_entries
        WHERE userId = :userId AND logDate >= :from AND isPendingDelete = 0
        ORDER BY loggedAt ASC
        """
    )
    suspend fun getFrom(userId: String, from: String): List<FoodLogEntity>

    @Query("SELECT * FROM food_log_entries WHERE localId = :localId LIMIT 1")
    suspend fun getByLocalId(localId: String): FoodLogEntity?

    @Query("SELECT * FROM food_log_entries WHERE isSynced = 0 AND isPendingDelete = 0")
    suspend fun getUnsynced(): List<FoodLogEntity>

    @Query("SELECT * FROM food_log_entries WHERE isPendingDelete = 1")
    suspend fun getPendingDeletes(): List<FoodLogEntity>

    @Query("SELECT remoteId FROM food_log_entries WHERE isPendingDelete = 1 AND remoteId IS NOT NULL")
    suspend fun getPendingDeleteRemoteIds(): List<Int>

    @Query("UPDATE food_log_entries SET remoteId = :remoteId, isSynced = 1 WHERE localId = :localId")
    suspend fun markSynced(localId: String, remoteId: Int?)

    @Query("UPDATE food_log_entries SET isPendingDelete = 1 WHERE localId = :localId")
    suspend fun markPendingDelete(localId: String)

    @Query("DELETE FROM food_log_entries WHERE localId = :localId")
    suspend fun deleteByLocalId(localId: String)

    /**
     * Clears synced rows in a range before re-inserting the server copy.
     * Unsynced rows are left alone so nothing written offline is ever lost.
     */
    @Query(
        """
        DELETE FROM food_log_entries
        WHERE userId = :userId AND logDate >= :from AND isSynced = 1 AND isPendingDelete = 0
        """
    )
    suspend fun clearSyncedFrom(userId: String, from: String)
}
