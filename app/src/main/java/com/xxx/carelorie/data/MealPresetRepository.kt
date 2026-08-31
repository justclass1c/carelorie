package com.xxx.carelorie.data

import com.xxx.carelorie.data.remote.RemoteFoodLog
import com.xxx.carelorie.data.remote.RemoteFoodPreset
import com.xxx.carelorie.data.remote.RemoteMealPreset
import com.xxx.carelorie.data.remote.RemoteMealPresetItem
import com.xxx.carelorie.data.remote.SupabaseRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID

/**
 * Saved meals: create one from what is already logged, then log the whole thing again later.
 *
 * Offline-first, the same shape as [FoodRepository]. Room is what the UI reads; Supabase is the
 * mirror that lets the library follow a user to a new phone. Saved meals used to be device-only,
 * so signing in elsewhere lost every one of them.
 */
class MealPresetRepository(
    private val mealPresetDao: MealPresetDao,
    private val foodRepository: FoodRepository,
    private val supabaseRepository: SupabaseRepository,
    private val connectivity: ConnectivityChecker = AlwaysOnlineChecker()
) {

    /** Serialises uploads, so two overlapping refreshes cannot both push the same meal. */
    private val pushMutex = Mutex()

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
                quantity = log.servings
            )
        }
        mealPresetDao.upsert(meal, items)
        push()
        return mealId
    }

    suspend fun rename(meal: MealPresetWithItems, name: String) {
        // isSynced = false routes the row back through the push, so the new name reaches the
        // server instead of living only on this device.
        mealPresetDao.upsert(meal.meal.copy(name = name.trim(), isSynced = false), meal.items)
        push()
    }

    /**
     * Removes a saved meal.
     *
     * Hidden locally at once, then cleared from the server. A meal that never reached Supabase is
     * simply dropped; anything else is queued so a delete made offline is not forgotten.
     */
    suspend fun delete(localId: String) {
        val existing = mealPresetDao.getByLocalId(localId) ?: return
        if (!existing.isSynced) {
            mealPresetDao.delete(localId)
            return
        }
        mealPresetDao.markPendingDelete(localId)
        flushPendingDeletes(existing.ownerUserId)
    }

    /** Every saved meal belonging to a user, for account deletion. */
    suspend fun deleteAllForUser(userId: String) {
        runCatching { supabaseRepository.deleteMealPresetsForUser(userId) }
        mealPresetDao.deleteAllForUser(userId)
    }

    // ---------------------------------------------------------------- sync

    /**
     * Pulls the user's saved meals from Supabase into Room, after flushing anything queued here.
     *
     * A failed request leaves the cache alone, so a dropped connection cannot empty the library.
     */
    suspend fun refresh(userId: String): SyncResult {
        if (!connectivity.isOnline()) return SyncResult.OFFLINE
        push()
        flushPendingDeletes(userId)

        val remoteMeals = try {
            supabaseRepository.fetchMealPresets(userId)
        } catch (e: Exception) {
            return if (connectivity.isOnline()) SyncResult.FAILED else SyncResult.OFFLINE
        }

        val remoteItems = try {
            supabaseRepository.fetchMealPresetItems(remoteMeals.map { it.localId })
        } catch (e: Exception) {
            return if (connectivity.isOnline()) SyncResult.FAILED else SyncResult.OFFLINE
        }

        // Anything still queued for deletion must not be resurrected by the server's copy.
        val pendingDeleteIds = mealPresetDao.getPendingDeletes(userId).map { it.localId }.toSet()
        val itemsByMeal = remoteItems.groupBy { it.mealPresetId }

        mealPresetDao.clearSyncedForUser(userId)

        for (remote in remoteMeals) {
            if (remote.localId in pendingDeleteIds) continue
            // A meal held here but not yet pushed is the newer copy: leave it alone rather than
            // letting the server overwrite an edit that has not been uploaded.
            if (mealPresetDao.getByLocalId(remote.localId)?.isSynced == false) continue

            mealPresetDao.upsert(
                MealPresetEntity(
                    localId = remote.localId,
                    ownerUserId = remote.ownerUserId,
                    name = remote.name,
                    mealType = remote.mealType,
                    createdAt = remote.createdAt,
                    isSynced = true
                ),
                (itemsByMeal[remote.localId] ?: emptyList()).map { item ->
                    MealPresetItemEntity(
                        localId = item.localId,
                        mealPresetId = item.mealPresetId,
                        foodName = item.foodName,
                        calories = item.calories,
                        protein = item.protein,
                        carbs = item.carbs,
                        fat = item.fat,
                        quantity = item.quantity,
                        sourcePresetId = item.sourcePresetId
                    )
                }
            )
        }
        return SyncResult.SUCCESS
    }

    /**
     * Uploads every saved meal not yet on the server.
     *
     * Under [pushMutex] and re-reading each row, so overlapping callers queue instead of both
     * uploading the same meal.
     */
    private suspend fun push() = pushMutex.withLock {
        val pending = try {
            mealPresetDao.getUnsyncedAcrossUsers()
        } catch (e: Exception) {
            return@withLock
        }
        for (entry in pending) {
            val current = mealPresetDao.getByLocalId(entry.meal.localId) ?: continue
            if (current.isSynced || current.isPendingDelete) continue

            val uploaded = supabaseRepository.upsertMealPreset(
                meal = RemoteMealPreset(
                    localId = current.localId,
                    ownerUserId = current.ownerUserId,
                    name = current.name,
                    mealType = current.mealType,
                    createdAt = current.createdAt
                ),
                items = entry.items.map { item ->
                    RemoteMealPresetItem(
                        localId = item.localId,
                        mealPresetId = item.mealPresetId,
                        foodName = item.foodName,
                        calories = item.calories,
                        protein = item.protein,
                        carbs = item.carbs,
                        fat = item.fat,
                        quantity = item.quantity,
                        sourcePresetId = item.sourcePresetId
                    )
                }
            )
            // Stop on the first failure rather than working through the rest — if the server is
            // rejecting one meal it will reject the next, and the queue is retried anyway.
            if (!uploaded) break
            mealPresetDao.markSynced(current.localId)
        }
    }

    private suspend fun flushPendingDeletes(userId: String) {
        val pending = try {
            mealPresetDao.getPendingDeletes(userId)
        } catch (e: Exception) {
            return
        }
        for (entry in pending) {
            if (supabaseRepository.deleteMealPreset(entry.localId)) {
                mealPresetDao.delete(entry.localId)
            }
        }
    }

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
