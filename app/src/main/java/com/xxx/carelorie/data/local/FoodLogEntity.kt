package com.xxx.carelorie.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.xxx.carelorie.data.remote.RemoteFoodLog
import java.util.UUID

/**
 * A logged food item as stored on the device.
 *
 * The app is offline-first: every entry is written here first and pushed to Supabase after.
 * That is what lets the Food Log show history with no connection.
 *
 * [localId] is the stable key. [remoteId] is filled in once the row reaches Supabase, and is
 * what a later delete uses. An entry with `isSynced = false` has not reached the server yet.
 */
@Entity(
    tableName = "food_log_entries",
    indices = [
        Index(value = ["userId", "logDate"]),
        Index(value = ["remoteId"])
    ]
)
data class FoodLogEntity(
    @PrimaryKey val localId: String = UUID.randomUUID().toString(),
    val remoteId: Int? = null,
    val userId: Int,
    val mealType: String,
    val foodName: String,
    val calories: Int,
    val protein: Float,
    val carbs: Float,
    val fat: Float,
    /** Full ISO timestamp, e.g. 2026-08-23T08:14:05 */
    val loggedAt: String,
    /** Just the date part (YYYY-MM-DD) — indexed so day and month queries stay fast. */
    val logDate: String,
    val isSynced: Boolean = false,
    /** Deleted locally, but the server copy still needs removing on the next sync. */
    val isPendingDelete: Boolean = false
)

fun FoodLogEntity.toRemote(): RemoteFoodLog = RemoteFoodLog(
    id = remoteId,
    userId = userId,
    mealType = mealType,
    foodName = foodName,
    calories = calories,
    protein = protein,
    carbs = carbs,
    fat = fat,
    createdAt = loggedAt,
    localId = localId
)

fun RemoteFoodLog.toEntity(
    localId: String = UUID.randomUUID().toString(),
    isSynced: Boolean = true
): FoodLogEntity {
    val timestamp = createdAt.ifBlank { java.time.LocalDateTime.now().toString() }
    return FoodLogEntity(
        localId = this.localId.ifBlank { localId },
        remoteId = id,
        userId = userId,
        mealType = mealType,
        foodName = foodName,
        calories = calories,
        protein = protein,
        carbs = carbs,
        fat = fat,
        loggedAt = timestamp,
        logDate = timestamp.take(10),
        isSynced = isSynced
    )
}
