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
        Index(value = ["remoteId"], unique = true)
    ]
)
data class FoodLogEntity(
    @PrimaryKey val localId: String = UUID.randomUUID().toString(),
    val remoteId: Int? = null,
    val userId: String,
    val mealType: String,
    val foodName: String,
    /**
     * Totals for the whole entry — [quantity] servings' worth, not one serving.
     *
     * Stored as totals because that is what every sum in the app needs and what the Supabase
     * `food_logs` table already holds. One serving's worth is total / [quantity].
     */
    val calories: Int,
    val protein: Float,
    val carbs: Float,
    val fat: Float,
    /**
     * How many servings this entry is.
     *
     * Previously the multiplier was folded into [foodName] ("Nasi Lemak (x2)") and thrown away,
     * which is why an entry could not be edited afterwards — there was nothing left to change.
     */
    val quantity: Float = 1f,
    /** [FoodPresetEntity.localId] this came from, when it came from the library. */
    val sourcePresetId: String? = null,

    // --- nutrition detail, kept per serving as the source reported it.
    // Local only: the Supabase food_logs table has no columns for these, and adding them to
    // RemoteFoodLog would break every insert until a migration was run. FoodRepository.refresh
    // carries them across a sync so they are not lost.
    val brand: String? = null,
    val servingDescription: String? = null,
    val fiberGrams: Float? = null,
    val sugarGrams: Float? = null,
    val saturatedFatGrams: Float? = null,
    val sodiumMilligrams: Float? = null,
    /** [NutritionSource] name, so an AI estimate stays labelled as one in the diary. */
    val nutritionSource: String? = null,

    /** Full ISO timestamp, e.g. 2026-08-23T08:14:05 */
    val loggedAt: String,
    /** Just the date part (YYYY-MM-DD) — indexed so day and month queries stay fast. */
    val logDate: String,
    val isSynced: Boolean = false,
    /** Deleted locally, but the server copy still needs removing on the next sync. */
    val isPendingDelete: Boolean = false
) {

    val hasNutritionDetail: Boolean
        get() = listOfNotNull(fiberGrams, sugarGrams, saturatedFatGrams, sodiumMilligrams).isNotEmpty()
}

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
    localId = localId,
    quantity = quantity,
    brand = brand,
    servingDescription = servingDescription,
    fiberGrams = fiberGrams,
    sugarGrams = sugarGrams,
    saturatedFatGrams = saturatedFatGrams,
    sodiumMilligrams = sodiumMilligrams,
    nutritionSource = nutritionSource
)

/**
 * @param preserve the local row this remote entry replaces, if we already had one.
 *
 * Servings and the nutrition breakdown are now columns on `food_logs`, so the server's copy is
 * preferred. [preserve] is the fallback for rows that reached Supabase before those columns
 * existed and still come back null — on the device that logged them the values are right there,
 * and there is no reason to drop them just because the server has not caught up.
 *
 * [sourcePresetId] has no server column and stays device-only: it points at a row in this phone's
 * food library, so it would be meaningless on another device anyway.
 */
fun RemoteFoodLog.toEntity(
    localId: String = UUID.randomUUID().toString(),
    isSynced: Boolean = true,
    preserve: FoodLogEntity? = null
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
        quantity = quantity ?: preserve?.quantity ?: 1f,
        sourcePresetId = preserve?.sourcePresetId,
        brand = brand ?: preserve?.brand,
        servingDescription = servingDescription ?: preserve?.servingDescription,
        fiberGrams = fiberGrams ?: preserve?.fiberGrams,
        sugarGrams = sugarGrams ?: preserve?.sugarGrams,
        saturatedFatGrams = saturatedFatGrams ?: preserve?.saturatedFatGrams,
        sodiumMilligrams = sodiumMilligrams ?: preserve?.sodiumMilligrams,
        nutritionSource = nutritionSource ?: preserve?.nutritionSource,
        loggedAt = timestamp,
        logDate = timestamp.take(10),
        isSynced = isSynced
    )
}
