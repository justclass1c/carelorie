package com.xxx.carelorie.data

import com.xxx.carelorie.data.local.FoodLogDao
import com.xxx.carelorie.data.local.FoodLogEntity
import com.xxx.carelorie.data.local.FoodPresetDao
import com.xxx.carelorie.data.local.FoodPresetEntity
import com.xxx.carelorie.data.local.toEntity
import com.xxx.carelorie.data.local.toPresetEntity
import com.xxx.carelorie.data.local.toRemote
import com.xxx.carelorie.data.remote.RemoteFoodLog
import com.xxx.carelorie.data.nutrition.NutritionDetail
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
    private val foodPresetDao: FoodPresetDao,
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

    /**
     * Writes a food into the diary.
     *
     * [date] defaults to today but is a parameter, so the food log's date navigator can add to the
     * day you are looking at. Before this, every entry was stamped with `now()`, which meant a
     * missed meal could never be entered afterwards.
     *
     * [food] carries the totals for [quantity] servings; the quantity is stored alongside so the
     * entry can be edited later, and the name stays clean instead of having "(x2)" glued on.
     */
    suspend fun logFood(
        userId: String,
        mealType: String,
        food: RemoteFoodPreset,
        quantity: Float = 1f,
        date: LocalDate = LocalDate.now(),
        detail: NutritionDetail? = null,
        sourcePresetId: String? = null
    ) {
        // Keep the clock time when logging today, so entries stay in the order they were eaten.
        // Backdated entries land at midday, which sorts them sensibly among that day's meals.
        val timestamp = if (date == LocalDate.now()) {
            LocalDateTime.now()
        } else {
            date.atTime(12, 0)
        }.toString()

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
            quantity = quantity,
            sourcePresetId = sourcePresetId,
            brand = detail?.brand ?: food.brand,
            servingDescription = detail?.servingDescription ?: food.servingDescription,
            fiberGrams = detail?.fiberGrams,
            sugarGrams = detail?.sugarGrams,
            saturatedFatGrams = detail?.saturatedFatGrams,
            sodiumMilligrams = detail?.sodiumMilligrams,
            nutritionSource = detail?.source?.name,
            loggedAt = timestamp,
            logDate = timestamp.take(10),
            isSynced = false
        )
        foodLogDao.upsert(entity)
        pushUnsynced()
    }

    /**
     * Changes the servings and/or the meal of an entry already in the diary.
     *
     * Macros are rescaled from the stored total rather than re-fetched, so this works offline and
     * needs nothing from the source food. Marking the row unsynced routes it back through
     * [pushUnsynced], which updates the server copy rather than inserting a duplicate.
     */
    suspend fun updateLog(
        localId: String,
        quantity: Float,
        mealType: String
    ): Result<Unit> {
        val existing = foodLogDao.getByLocalId(localId)
            ?: return Result.failure(IllegalArgumentException("That entry no longer exists"))

        val safeQuantity = quantity.coerceIn(0.25f, 20f)
        val perServing = if (existing.quantity > 0f) existing.quantity else 1f
        val factor = safeQuantity / perServing

        foodLogDao.upsert(
            existing.copy(
                quantity = safeQuantity,
                mealType = mealType,
                calories = (existing.calories * factor).toInt(),
                protein = existing.protein * factor,
                carbs = existing.carbs * factor,
                fat = existing.fat * factor,
                isSynced = false
            )
        )
        pushUnsynced()
        return Result.success(Unit)
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

        // Match on remoteId so the local row keeps its id — and its quantity and nutrition
        // detail, which the server does not store and would otherwise be wiped on every sync.
        val existingEntries = foodLogDao.getFrom(userId, fetchStart)
        val byRemoteId = existingEntries.mapNotNull { e -> e.remoteId?.let { it to e } }.toMap()

        val entities = filteredRemote.map { remoteLog ->
            val existing = remoteLog.id?.let { byRemoteId[it] }
            remoteLog.toEntity(
                localId = existing?.localId ?: UUID.randomUUID().toString(),
                isSynced = true,
                preserve = existing
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
            if (entry.remoteId == null) {
                val remote = supabaseRepository.addFoodLog(entry.toRemote().copy(id = null))
                if (remote != null) foodLogDao.markSynced(entry.localId, remote.id)
            } else if (supabaseRepository.updateFoodLog(entry.toRemote())) {
                // Already on the server — an edit, not a new entry.
                foodLogDao.markSynced(entry.localId, entry.remoteId)
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

    /**
     * The user's own foods plus the built-in dishes, straight from Room.
     *
     * Reading locally is what lets the food list — and therefore logging — keep working with no
     * connection, and it means a food saved in the editor shows up in search immediately.
     */
    fun observePresets(userId: String): Flow<List<FoodPresetEntity>> =
        foodPresetDao.observeForUser(userId)

    suspend fun getPreset(localId: String): FoodPresetEntity? = foodPresetDao.getByLocalId(localId)

    /**
     * Puts the built-in dishes in Room the first time they are needed.
     *
     * Previously every client upserted these 28 rows into the shared Supabase table on startup,
     * which raced between devices and made the built-ins unavailable offline. They are static
     * app content, so they belong on the device.
     */
    suspend fun seedBuiltInPresetsIfNeeded() {
        if (foodPresetDao.countBuiltIns() > 0) return
        foodPresetDao.upsertAll(
            DefaultFoodPresets.ALL.map { preset ->
                FoodPresetEntity(
                    localId = "builtin:${preset.name}",
                    remoteId = null,
                    ownerUserId = null,
                    name = preset.name,
                    brand = null,
                    servingDescription = preset.servingDescription,
                    calories = preset.calories,
                    protein = preset.protein,
                    carbs = preset.carbs,
                    fat = preset.fat,
                    isSynced = true
                )
            }
        )
    }

    /**
     * Creates or updates one of the user's own foods.
     *
     * Written to Room first and pushed after, so the editor's Save works offline. Passing a
     * [localId] that belongs to a built-in is refused — those are shared rows; the caller should
     * copy instead (see [copyPresetForUser]).
     */
    suspend fun savePreset(
        userId: String,
        localId: String?,
        name: String,
        brand: String?,
        servingDescription: String?,
        calories: Int,
        protein: Float,
        carbs: Float,
        fat: Float
    ): Result<String> {
        val existing = localId?.let { foodPresetDao.getByLocalId(it) }
        if (existing != null && existing.isBuiltIn) {
            return Result.failure(IllegalArgumentException("Built-in presets cannot be edited"))
        }

        val entity = (existing ?: FoodPresetEntity(
            ownerUserId = userId,
            name = name,
            calories = calories,
            protein = protein,
            carbs = carbs,
            fat = fat
        )).copy(
            ownerUserId = userId,
            name = name,
            brand = brand,
            servingDescription = servingDescription,
            calories = calories,
            protein = protein,
            carbs = carbs,
            fat = fat,
            isSynced = false
        )

        foodPresetDao.upsert(entity)
        pushUnsyncedPresets()
        return Result.success(entity.localId)
    }

    /** Turns a built-in dish into an editable food the user owns. */
    suspend fun copyPresetForUser(userId: String, source: FoodPresetEntity): Result<String> =
        savePreset(
            userId = userId,
            localId = null,
            name = source.name,
            brand = source.brand,
            servingDescription = source.servingDescription,
            calories = source.calories,
            protein = source.protein,
            carbs = source.carbs,
            fat = source.fat
        )

    /** Removes one of the user's foods locally at once, then clears the server copy. */
    suspend fun deletePreset(preset: FoodPresetEntity): Boolean {
        if (preset.isBuiltIn) return false

        if (preset.remoteId == null) {
            // Never reached the server, so local removal is the whole job.
            foodPresetDao.deleteByLocalId(preset.localId)
            return true
        }

        foodPresetDao.markPendingDelete(preset.localId)
        return flushPendingPresetDeletes()
    }

    /**
     * Pulls the user's presets from Supabase into Room after flushing anything queued locally.
     * A failed request leaves the cache untouched, so a dropped connection cannot blank the list.
     */
    suspend fun refreshPresets(userId: String): SyncResult {
        seedBuiltInPresetsIfNeeded()
        pushUnsyncedPresets()
        flushPendingPresetDeletes()

        val remote = try {
            supabaseRepository.fetchUserFoodPresets(userId)
        } catch (e: Exception) {
            e.printStackTrace()
            return SyncResult.OFFLINE
        }

        // Preserve local ids for rows we already know about, so the UI doesn't lose its place.
        val existingByRemoteId = foodPresetDao.getForUser(userId)
            .mapNotNull { entity -> entity.remoteId?.let { it to entity.localId } }
            .toMap()

        // Anything still queued for deletion must not be resurrected by the server copy.
        val pendingDeleteIds = foodPresetDao.getPendingDeletes().mapNotNull { it.remoteId }.toSet()

        foodPresetDao.clearSyncedForUser(userId)
        foodPresetDao.upsertAll(
            remote
                .filter { it.id !in pendingDeleteIds }
                .map { it.toPresetEntity(localId = existingByRemoteId[it.id] ?: UUID.randomUUID().toString()) }
        )
        return SyncResult.SUCCESS
    }

    private suspend fun pushUnsyncedPresets() {
        val pending = try {
            foodPresetDao.getUnsynced()
        } catch (e: Exception) {
            return
        }
        for (entry in pending) {
            if (entry.remoteId == null) {
                val stored = supabaseRepository.insertFoodPreset(entry.toRemote().copy(id = null))
                if (stored != null) foodPresetDao.markSynced(entry.localId, stored.id)
            } else if (supabaseRepository.updateFoodPreset(entry.toRemote())) {
                foodPresetDao.markSynced(entry.localId, entry.remoteId)
            }
        }
    }

    /** @return true if every queued delete reached the server. */
    private suspend fun flushPendingPresetDeletes(): Boolean {
        val pending = try {
            foodPresetDao.getPendingDeletes()
        } catch (e: Exception) {
            return false
        }
        var allCleared = true
        for (entry in pending) {
            val remoteId = entry.remoteId
            if (remoteId == null || supabaseRepository.deleteFoodPreset(remoteId)) {
                foodPresetDao.deleteByLocalId(entry.localId)
            } else {
                allCleared = false
            }
        }
        return allCleared
    }
}
