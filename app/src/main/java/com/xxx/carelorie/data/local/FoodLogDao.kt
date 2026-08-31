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

    /** Distinct days that have at least one entry — drives the calendar dots. */
    @Query(
        """
        SELECT DISTINCT logDate FROM food_log_entries
        WHERE userId = :userId AND isPendingDelete = 0
        ORDER BY logDate ASC
        """
    )
    fun observeLoggedDates(userId: String): Flow<List<String>>

    /**
     * Every day this user has logged anything, unbounded.
     *
     * The streak used to be derived from whichever month the dashboard happened to be showing,
     * which capped it at the day of the month and made it change as you paged the calendar.
     * Streaks need the whole history, so they get their own query.
     */
    @Query("SELECT DISTINCT logDate FROM food_log_entries WHERE userId = :userId AND isPendingDelete = 0")
    suspend fun getAllLoggedDates(userId: String): List<String>

    /**
     * Entries in a closed date range.
     *
     * Both ends are inclusive and both are required. An open-ended version of this used to exist,
     * and pairing it with a bounded re-insert is what let a month refresh return — and delete —
     * days outside the month it was asked about. See [clearSyncedBetween].
     */
    @Query(
        """
        SELECT * FROM food_log_entries
        WHERE userId = :userId AND logDate >= :from AND logDate <= :to AND isPendingDelete = 0
        ORDER BY loggedAt ASC
        """
    )
    suspend fun getBetween(userId: String, from: String, to: String): List<FoodLogEntity>

    @Query("SELECT * FROM food_log_entries WHERE localId = :localId LIMIT 1")
    suspend fun getByLocalId(localId: String): FoodLogEntity?

    @Query("SELECT * FROM food_log_entries WHERE isSynced = 0 AND isPendingDelete = 0")
    suspend fun getUnsynced(): List<FoodLogEntity>

    @Query("SELECT * FROM food_log_entries WHERE isPendingDelete = 1")
    suspend fun getPendingDeletes(): List<FoodLogEntity>

    @Query("UPDATE food_log_entries SET remoteId = :remoteId, isSynced = 1 WHERE localId = :localId")
    suspend fun markSynced(localId: String, remoteId: Int?)

    @Query("UPDATE food_log_entries SET isPendingDelete = 1 WHERE localId = :localId")
    suspend fun markPendingDelete(localId: String)

    @Query("DELETE FROM food_log_entries WHERE localId = :localId")
    suspend fun deleteByLocalId(localId: String)

    /**
     * Clears synced rows in a range before re-inserting the server copy.
     * Unsynced rows are left alone so nothing written offline is ever lost.
     *
     * [to] is not optional, and must be the same upper bound the caller re-inserts up to. When the
     * delete was open-ended, refreshing a past month wiped every synced entry from that month up
     * to today and only put that one month back.
     */
    @Query(
        """
        DELETE FROM food_log_entries
        WHERE userId = :userId AND logDate >= :from AND logDate <= :to
          AND isSynced = 1 AND isPendingDelete = 0
        """
    )
    suspend fun clearSyncedBetween(userId: String, from: String, to: String)

    /** Every entry belonging to a user, for account deletion. */
    @Query("DELETE FROM food_log_entries WHERE userId = :userId")
    suspend fun deleteAllForUser(userId: String)
}
