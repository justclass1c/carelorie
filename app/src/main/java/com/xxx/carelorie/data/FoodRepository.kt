package com.xxx.carelorie.data

import com.xxx.carelorie.data.local.FoodLogDao
import com.xxx.carelorie.data.local.FoodLogEntity
import com.xxx.carelorie.data.local.toEntity
import com.xxx.carelorie.data.local.toRemote
import com.xxx.carelorie.data.remote.RemoteFoodLog
import com.xxx.carelorie.data.remote.RemoteFoodPreset
import com.xxx.carelorie.data.remote.SupabaseRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import java.util.UUID

/** Outcome of a sync attempt, so the UI can tell "nothing new" from "no connection". */
enum class SyncResult { SUCCESS, OFFLINE }

/**
 * Offline-first food log storage.
 *
 * Room is the single source of truth the UI reads from. Supabase is a mirror that is pushed to
 * when a connection exists. A log written on a plane appears immediately and uploads later.
 */
class FoodRepository(
    private val supabaseRepository: SupabaseRepository,
    private val foodLogDao: FoodLogDao,
) {

    // ---------------------------------------------------------------- reads (always local)

    fun observeLogsForDate(userId: String, date: LocalDate): Flow<List<RemoteFoodLog>> =
        foodLogDao.observeForDate(userId, date.toString()).map { list -> list.map { it.toRemote() } }

    fun observeLogsBetween(userId: String, start: LocalDate, end: LocalDate): Flow<List<RemoteFoodLog>> =
        foodLogDao.observeBetween(userId, start.toString(), end.toString())
            .map { list -> list.map { it.toRemote() } }

    fun observeLoggedDates(userId: String): Flow<Set<LocalDate>> =
        foodLogDao.observeLoggedDates(userId).map { dates ->
            dates.mapNotNull { runCatching { LocalDate.parse(it) }.getOrNull() }.toSet()
        }

    suspend fun getDailyLogs(userId: String, date: String): List<RemoteFoodLog> {
        refresh(userId, LocalDate.parse(date), LocalDate.parse(date))
        return foodLogDao.getFrom(userId, date)
            .filter { it.logDate == date }
            .map { it.toRemote() }
    }

    suspend fun getMonthlyLogs(userId: String, yearMonth: YearMonth): List<RemoteFoodLog> {
        val start = yearMonth.atDay(1)
        val end = yearMonth.atEndOfMonth()
        refresh(userId, start, end)
        return foodLogDao.getFrom(userId, start.toString()).map { it.toRemote() }
    }

    suspend fun getWeeklyLogs(userId: String): List<RemoteFoodLog> {
        val start = LocalDate.now().minusDays(7)
        val end = LocalDate.now()
        refresh(userId, start, end)
        return foodLogDao.getFrom(userId, start.toString()).map { it.toRemote() }
    }

    // ---------------------------------------------------------------- writes (local first)

    suspend fun logFood(userId: String, mealType: String, food: RemoteFoodPreset) {
        val now = LocalDateTime.now().toString()
        val entity = FoodLogEntity(
            localId = UUID.randomUUID().toString(),
            remoteId = null,
            userId = userId,
            mealType = mealType,
            foodName = food.name,
            calories = food.calories,
            protein = food.protein,
            carbs = food.carbs,
            fat = food.fat,
            loggedAt = now,
            logDate = now.take(10),
            isSynced = false
        )
        foodLogDao.upsert(entity)
        pushUnsynced()
    }

    /** Removes an entry locally straight away, then tries to remove the server copy. */
    suspend fun deleteLog(log: RemoteFoodLog): Boolean {
        val localId = log.localId
        val remoteId = log.id

        if (remoteId == null) {
            // Never reached the server, so local removal is the whole job.
            if (localId.isNotBlank()) foodLogDao.deleteByLocalId(localId)
            return true
        }

        if (localId.isNotBlank()) foodLogDao.markPendingDelete(localId)

        // We return true immediately so the UI remains responsive. 
        // The background sync (flushPendingDeletes) will handle the server removal.
        // We do NOT deleteByLocalId here anymore; we wait for the server to confirm 
        // the deletion by omitting it from the next refresh response.
        return true
    }

    // ---------------------------------------------------------------- sync

    /**
     * Pulls server state for a specific range into Room, after flushing anything queued locally.
     * Safe to call often; failures leave the cache untouched.
     */
    /**
     * Pulls server state for a specific range into Room, after flushing anything queued locally.
     * Safe to call often; failures leave the cache untouched.
     */
    suspend fun refresh(userId: String, start: LocalDate, end: LocalDate? = null): SyncResult {
        pushUnsynced()
        val justDeleted = flushPendingDeletes()

        val fetchStart = start.toString()
        val remote = try {
            supabaseRepository.fetchFoodLogsRange(userId, fetchStart)
        } catch (e: Exception) {
            e.printStackTrace()
            return SyncResult.OFFLINE
        }

        // If we have an end date, filter the remote results to only include what we asked for.
        // Supabase fetchFoodLogsRange uses gte(createdAt), so it might return more than we need.
        val filteredByDate = if (end != null) {
            val endStr = end.toString()
            remote.filter { it.createdAt.take(10) <= endStr }
        } else {
            remote
        }

        foodLogDao.clearSyncedFrom(userId, fetchStart)
        
        val pendingDeletes = foodLogDao.getPendingDeletes()
        val pendingDeleteIds = (pendingDeletes.mapNotNull { it.remoteId } + justDeleted).toSet()
        
        // Filter out items that are currently pending delete locally, 
        // to prevent them from being resurrected by the server copy.
        val filteredRemote = filteredByDate.filter { it.id !in pendingDeleteIds }
        
        // If a pending delete ID is NO LONGER in the server response AND it falls within the 
        // range we just fetched, it means the deletion has succeeded on the server.
        val remoteIdsFromServer = filteredByDate.mapNotNull { it.id }.toSet()
        val endStr = end?.toString() ?: "9999-12-31"
        
        pendingDeletes.forEach { localEntry ->
            val rId = localEntry.remoteId
            val date = localEntry.logDate
            if (rId != null && rId !in remoteIdsFromServer && date >= fetchStart && date <= endStr) {
                foodLogDao.deleteByLocalId(localEntry.localId)
            }
        }

        // Before inserting, try to match remoteId to existing local entries to preserve localId
        val existingEntries = foodLogDao.getFrom(userId, fetchStart)
        val remoteIdToLocalId = existingEntries.mapNotNull { e -> 
            e.remoteId?.let { it to e.localId } 
        }.toMap()

        val entities = filteredRemote.map { remoteLog ->
            val existingLocalId = remoteLog.id?.let { remoteIdToLocalId[it] }
            remoteLog.toEntity(
                localId = existingLocalId ?: UUID.randomUUID().toString(),
                isSynced = true
            )
        }

        foodLogDao.upsertAll(entities)
        return SyncResult.SUCCESS
    }

    suspend fun refreshRange(userId: String, start: LocalDate, end: LocalDate): SyncResult {
        return refresh(userId, start, end)
    }

    private suspend fun pushUnsynced() {
        val pending = try {
            foodLogDao.getUnsynced()
        } catch (e: Exception) {
            return
        }
        for (entry in pending) {
            val remote = supabaseRepository.addFoodLog(entry.toRemote().copy(id = null))
            if (remote != null) {
                foodLogDao.markSynced(entry.localId, remote.id)
            }
        }
    }

    private suspend fun flushPendingDeletes(): Set<Int> {
        val attemptedRemoteIds = mutableSetOf<Int>()
        val pending = try {
            foodLogDao.getPendingDeletes()
        } catch (e: Exception) {
            return emptySet()
        }
        for (entry in pending) {
            val remoteId = entry.remoteId
            if (remoteId == null) {
                // Never reached the server — safe to remove locally immediately.
                foodLogDao.deleteByLocalId(entry.localId)
            } else if (supabaseRepository.deleteFoodLog(remoteId)) {
                // Track that we attempted the server delete, but do NOT remove the local row yet.
                // RLS or other silent failures can make the delete appear successful while the
                // record remains. The next refresh will confirm the row is gone from the server
                // before cleaning it up locally.
                attemptedRemoteIds.add(remoteId)
            }
        }
        return attemptedRemoteIds
    }


    // ---------------------------------------------------------------- food lookup



    // ---------------------------------------------------------------- presets

    suspend fun getFoodPresets(userId: String): List<RemoteFoodPreset> {
        return try {
            val presets = supabaseRepository.fetchFoodPresets(userId)
            // If the user hasn't seen the defaults yet, or they were lost, merge them.
            // A simple isEmpty() check is insufficient if the user has added one custom preset.
            if (presets.none { it.userId == null }) {
                val defaults = DefaultFoodPresets.ALL
                supabaseRepository.seedFoodPresets(defaults)
                presets + defaults
            } else {
                presets
            }
        } catch (e: Exception) {
            e.printStackTrace()
            DefaultFoodPresets.ALL
        }
    }
}
