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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import java.util.UUID

/**
 * Outcome of a sync attempt.
 *
 * [FAILED] exists because everything used to collapse into [OFFLINE]: a missing column, a
 * permissions error or a decode failure all reported as "you have no connection", which is why
 * the food log showed its offline banner while the device was on wifi.
 */
enum class SyncResult { SUCCESS, OFFLINE, FAILED }

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
    private val connectivity: ConnectivityChecker = AlwaysOnlineChecker(),
    /** Outlives any screen, so a queued upload is not cancelled by navigating away. */
    private val syncScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
) {

    /**
     * Serialises every upload.
     *
     * Without this, two overlapping refreshes both read the same unsynced rows and both insert
     * them — and the dashboard alone triggers two refreshes per load while the food log triggers
     * its own. That is what was silently duplicating entries on the server: one banana logged,
     * three bananas stored.
     */
    private val pushMutex = Mutex()

    /**
     * The same protection for the food library.
     *
     * `refreshPresets` and `savePreset` both push, and a save while a refresh is in flight had
     * them both read the same unsynced row and both insert it — the duplicate-entry bug [pushMutex]
     * fixes for the diary, but for custom foods. Kept separate so a slow preset upload cannot hold
     * up a diary entry.
     */
    private val presetPushMutex = Mutex()

    // ---------------------------------------------------------------- reads (always local)

    fun observeLogsForDate(userId: String, date: LocalDate): Flow<List<RemoteFoodLog>> =
        foodLogDao.observeForDate(userId, date.toString()).map { list -> list.map { it.toRemote() } }

    /** Every day with at least one entry, across the user's whole history. */
    suspend fun getAllLoggedDates(userId: String): Set<LocalDate> =
        foodLogDao.getAllLoggedDates(userId)
            .mapNotNull { runCatching { LocalDate.parse(it) }.getOrNull() }
            .toSet()

    fun observeLoggedDates(userId: String): Flow<Set<LocalDate>> =
        foodLogDao.observeLoggedDates(userId).map { dates ->
            dates.mapNotNull { runCatching { LocalDate.parse(it) }.getOrNull() }.toSet()
        }

    suspend fun getMonthlyLogs(userId: String, yearMonth: YearMonth): List<RemoteFoodLog> {
        val start = yearMonth.atDay(1)
        val end = yearMonth.atEndOfMonth()
        refresh(userId, start, end)
        // Bounded at both ends: reading open-ended from the first of the month returned every
        // later day too, so a caller asking for January was handed January onwards.
        return foodLogDao.getBetween(userId, start.toString(), end.toString()).map { it.toRemote() }
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
        // Queued, not awaited. This used to be a blocking round trip per food, and it pushed
        // *every* unsynced row each time — so logging three items meant three sequential
        // uploads of a growing list, which is the pause people saw after tapping Log.
        requestPush()
    }

    /** Fire-and-forget upload. Safe to call as often as you like; the mutex collapses bursts. */
    private fun requestPush() {
        syncScope.launch { runCatching { pushUnsynced() } }
    }

    /**
     * Empties the outbound queue: diary entries and custom foods written while offline, plus any
     * deletes that never reached the server.
     *
     * Called at app start so the queue drains as soon as there is a connection, rather than
     * waiting for the user to happen to open a screen that syncs. It does not pull anything —
     * screens do that when they load — and every step is allowed to fail, because being offline
     * is the normal case for the rows it is trying to send.
     */
    suspend fun flushOutbox() {
        if (!connectivity.isOnline()) return
        runCatching { pushUnsynced() }
        runCatching { flushPendingDeletes() }
        runCatching { pushUnsyncedPresets() }
        runCatching { flushPendingPresetDeletes() }
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
     * Pulls server state for a date range into Room, after flushing anything queued locally.
     * Safe to call often; failures leave the cache untouched.
     *
     * [end] defaults to today. The range is closed at both ends on purpose: the local clear-out
     * below and the re-insert that follows it must cover exactly the same days. When the clear-out
     * was open-ended, refreshing a past month deleted every synced entry from that month up to
     * today and put only that month back — so paging the Goal calendar backwards emptied the
     * dashboard.
     */
    suspend fun refresh(userId: String, start: LocalDate, end: LocalDate? = null): SyncResult {
        if (!connectivity.isOnline()) return SyncResult.OFFLINE
        pushUnsynced()
        val justDeleted = flushPendingDeletes()

        val fetchStart = start.toString()
        val fetchEnd = (end ?: LocalDate.now()).toString()

        val remote = try {
            supabaseRepository.fetchFoodLogsRange(userId, fetchStart)
        } catch (e: Exception) {
            e.printStackTrace()
            // Ask the platform rather than assuming. A schema error, a permissions error or a
            // decode failure are all server-side problems and must not claim the user is offline.
            return if (connectivity.isOnline()) SyncResult.FAILED else SyncResult.OFFLINE
        }

        // Supabase fetchFoodLogsRange filters on gte(createdAt) only, so trim the top end here to
        // the range we actually asked for.
        val filteredByDate = remote.filter { it.createdAt.take(10) <= fetchEnd }

        // Read the rows we are about to replace BEFORE clearing them. Quantity and the nutrition
        // breakdown live only on the device, and this map is the only thing that carries them
        // across a sync — reading it after the delete left it empty, which silently reset every
        // synced entry to one serving with no detail.
        val byRemoteId = foodLogDao.getBetween(userId, fetchStart, fetchEnd)
            .mapNotNull { entry -> entry.remoteId?.let { it to entry } }
            .toMap()

        val pendingDeletes = foodLogDao.getPendingDeletes()
        val pendingDeleteIds = (pendingDeletes.mapNotNull { it.remoteId } + justDeleted).toSet()

        // Filter out items that are currently pending delete locally,
        // to prevent them from being resurrected by the server copy.
        val filteredRemote = filteredByDate.filter { it.id !in pendingDeleteIds }

        // If a pending delete ID is NO LONGER in the server response AND it falls within the
        // range we just fetched, it means the deletion has succeeded on the server.
        val remoteIdsFromServer = filteredByDate.mapNotNull { it.id }.toSet()

        pendingDeletes.forEach { localEntry ->
            val rId = localEntry.remoteId
            val date = localEntry.logDate
            if (rId != null && rId !in remoteIdsFromServer && date >= fetchStart && date <= fetchEnd) {
                foodLogDao.deleteByLocalId(localEntry.localId)
            }
        }

        // Match on remoteId so the local row keeps its id — and its quantity and nutrition
        // detail, which the server does not store and would otherwise be wiped on every sync.
        val entities = filteredRemote.map { remoteLog ->
            val existing = remoteLog.id?.let { byRemoteId[it] }
            remoteLog.toEntity(
                localId = existing?.localId ?: UUID.randomUUID().toString(),
                isSynced = true,
                preserve = existing
            )
        }

        // Clear and re-insert as one transaction, so the diary never briefly renders without the
        // rows that are about to come straight back. [byRemoteId] above was already read, which is
        // what the clear must not run ahead of.
        foodLogDao.replaceSyncedBetween(userId, fetchStart, fetchEnd, entities)
        return SyncResult.SUCCESS
    }

    suspend fun refreshRange(userId: String, start: LocalDate, end: LocalDate): SyncResult {
        return refresh(userId, start, end)
    }

    /**
     * Uploads everything not yet on the server.
     *
     * Held under [pushMutex] so concurrent callers queue instead of racing. Each row is also
     * claimed by writing its remote id back the moment the insert returns, so a second pass that
     * starts after this one finishes sees it as synced rather than inserting it again.
     */
    private suspend fun pushUnsynced() = pushMutex.withLock {
        val pending = try {
            foodLogDao.getUnsynced()
        } catch (e: Exception) {
            return@withLock
        }
        for (entry in pending) {
            // Re-read inside the loop: an earlier iteration, or a push that ran while this one
            // was waiting on the mutex, may already have uploaded this row.
            val current = foodLogDao.getByLocalId(entry.localId) ?: continue
            if (current.isSynced) continue

            if (current.remoteId == null) {
                val remote = supabaseRepository.addFoodLog(current.toRemote().copy(id = null))
                if (remote?.id != null) {
                    foodLogDao.markSynced(current.localId, remote.id)
                } else {
                    // The insert failed. Stop rather than working through the rest and
                    // re-attempting the whole queue on the next refresh — if the server is
                    // rejecting one row it will reject the next, and retrying in a loop is how
                    // duplicates got created when a write half-succeeded.
                    break
                }
            } else if (supabaseRepository.updateFoodLog(current.toRemote())) {
                // Already on the server — an edit, not a new entry.
                foodLogDao.markSynced(current.localId, current.remoteId)
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
        if (!connectivity.isOnline()) return SyncResult.OFFLINE
        seedBuiltInPresetsIfNeeded()
        pushUnsyncedPresets()
        flushPendingPresetDeletes()

        val remote = try {
            supabaseRepository.fetchUserFoodPresets(userId)
        } catch (e: Exception) {
            e.printStackTrace()
            return if (connectivity.isOnline()) SyncResult.FAILED else SyncResult.OFFLINE
        }

        // Preserve local ids for rows we already know about, so the UI doesn't lose its place.
        val existingByRemoteId = foodPresetDao.getForUser(userId)
            .mapNotNull { entity -> entity.remoteId?.let { it to entity.localId } }
            .toMap()

        // Anything still queued for deletion must not be resurrected by the server copy.
        val pendingDeleteIds = foodPresetDao.getPendingDeletes().mapNotNull { it.remoteId }.toSet()

        // One transaction, so the library does not blink empty on every refresh.
        foodPresetDao.replaceSyncedForUser(
            userId,
            remote
                .filter { it.id !in pendingDeleteIds }
                .map { it.toPresetEntity(localId = existingByRemoteId[it.id] ?: UUID.randomUUID().toString()) }
        )
        return SyncResult.SUCCESS
    }

    private suspend fun pushUnsyncedPresets() = presetPushMutex.withLock {
        val pending = try {
            foodPresetDao.getUnsynced()
        } catch (e: Exception) {
            return@withLock
        }
        for (entry in pending) {
            // Re-read inside the loop: a push that ran while this one was waiting on the mutex
            // may already have uploaded this row, and inserting it again is how duplicate custom
            // foods appeared on the server.
            val current = foodPresetDao.getByLocalId(entry.localId) ?: continue
            if (current.isSynced) continue

            if (current.remoteId == null) {
                val stored = supabaseRepository.insertFoodPreset(current.toRemote().copy(id = null))
                if (stored != null) {
                    foodPresetDao.markSynced(current.localId, stored.id)
                } else {
                    // The insert failed. Stop rather than working through the rest — if the server
                    // is rejecting one row it will reject the next, and retrying the whole queue
                    // is what turns a half-succeeded write into duplicates.
                    break
                }
            } else if (supabaseRepository.updateFoodPreset(current.toRemote())) {
                foodPresetDao.markSynced(current.localId, current.remoteId)
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

    // ---------------------------------------------------------------- account removal

    /**
     * Erases every diary entry and custom food belonging to [userId], locally and on the server.
     *
     * Deleting an account used to leave all of it behind on both. The server calls go first, and
     * each is allowed to fail: a device with no connection must still be able to clear its own
     * copy rather than refusing to delete the account at all. The built-in dishes are shared and
     * carry a null owner, so they are untouched.
     */
    suspend fun deleteAllDataForUser(userId: String) {
        runCatching { supabaseRepository.deleteFoodLogsForUser(userId) }
        runCatching { supabaseRepository.deleteFoodPresetsForUser(userId) }
        foodLogDao.deleteAllForUser(userId)
        foodPresetDao.deleteAllForUser(userId)
    }
}
