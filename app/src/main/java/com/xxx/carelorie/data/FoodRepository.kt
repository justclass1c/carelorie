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

    fun observeLogsForDate(userId: Int, date: LocalDate): Flow<List<RemoteFoodLog>> =
        foodLogDao.observeForDate(userId, date.toString()).map { list -> list.map { it.toRemote() } }

    fun observeLogsBetween(userId: Int, start: LocalDate, end: LocalDate): Flow<List<RemoteFoodLog>> =
        foodLogDao.observeBetween(userId, start.toString(), end.toString())
            .map { list -> list.map { it.toRemote() } }

    fun observeLoggedDates(userId: Int): Flow<Set<LocalDate>> =
        foodLogDao.observeLoggedDates(userId).map { dates ->
            dates.mapNotNull { runCatching { LocalDate.parse(it) }.getOrNull() }.toSet()
        }

    suspend fun getDailyLogs(userId: Int, date: String): List<RemoteFoodLog> {
        refresh(userId, LocalDate.parse(date))
        return foodLogDao.getFrom(userId, date)
            .filter { it.logDate == date }
            .map { it.toRemote() }
    }

    suspend fun getMonthlyLogs(userId: Int, yearMonth: YearMonth): List<RemoteFoodLog> {
        val start = yearMonth.atDay(1)
        refresh(userId, start)
        return foodLogDao.getFrom(userId, start.toString()).map { it.toRemote() }
    }

    suspend fun getWeeklyLogs(userId: Int): List<RemoteFoodLog> {
        val start = LocalDate.now().minusDays(7)
        refresh(userId, start)
        return foodLogDao.getFrom(userId, start.toString()).map { it.toRemote() }
    }

    // ---------------------------------------------------------------- writes (local first)

    suspend fun logFood(userId: Int, mealType: String, food: RemoteFoodPreset) {
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

        return if (supabaseRepository.deleteFoodLog(remoteId)) {
            if (localId.isNotBlank()) foodLogDao.deleteByLocalId(localId)
            true
        } else {
            // Stays hidden locally and is retried on the next sync.
            false
        }
    }

    // ---------------------------------------------------------------- sync

    /**
     * Pulls server state from [from] onwards into Room, after flushing anything queued locally.
     * Safe to call often; failures leave the cache untouched.
     */
    suspend fun refresh(userId: Int, from: LocalDate): SyncResult {
        pushUnsynced()
        flushPendingDeletes()

        val remote = try {
            supabaseRepository.fetchFoodLogsRange(userId, from.toString())
        } catch (e: Exception) {
            e.printStackTrace()
            return SyncResult.OFFLINE
        }

        if (remote.isEmpty()) {
            // Cannot distinguish "no rows" from "request failed", so keep what we have
            // rather than wiping a good cache.
            val cached = foodLogDao.getFrom(userId, from.toString())
            if (cached.any { it.isSynced }) return SyncResult.OFFLINE
        }

        foodLogDao.clearSyncedFrom(userId, from.toString())
        foodLogDao.upsertAll(remote.map { it.toEntity(isSynced = true) })
        return SyncResult.SUCCESS
    }

    private suspend fun pushUnsynced() {
        val pending = try {
            foodLogDao.getUnsynced()
        } catch (e: Exception) {
            return
        }
        for (entry in pending) {
            val ok = supabaseRepository.addFoodLog(entry.toRemote().copy(id = null))
            if (ok) {
                foodLogDao.markSynced(entry.localId, null)
            }
        }
    }

    private suspend fun flushPendingDeletes() {
        val pending = try {
            foodLogDao.getPendingDeletes()
        } catch (e: Exception) {
            return
        }
        for (entry in pending) {
            val remoteId = entry.remoteId
            if (remoteId == null) {
                foodLogDao.deleteByLocalId(entry.localId)
            } else if (supabaseRepository.deleteFoodLog(remoteId)) {
                foodLogDao.deleteByLocalId(entry.localId)
            }
        }
    }


    // ---------------------------------------------------------------- food lookup



    // ---------------------------------------------------------------- presets

    suspend fun getFoodPresets(userId: Int): List<RemoteFoodPreset> {
        return try {
            val presets = supabaseRepository.fetchFoodPresets(userId)
            if (presets.isEmpty()) {
                val defaults = DefaultFoodPresets.ALL
                supabaseRepository.seedFoodPresets(defaults)
                defaults
            } else {
                presets
            }
        } catch (e: Exception) {
            e.printStackTrace()
            DefaultFoodPresets.ALL
        }
    }
}
