package com.xxx.carelorie.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.xxx.carelorie.data.remote.RemoteFoodPreset
import java.util.UUID

/**
 * A food the user can pick when logging — either one of the app's built-in dishes or one they
 * created themselves.
 *
 * Stored offline-first for the same reason food logs are: the list has to be usable with no
 * connection, and a food created on a plane must survive until it can be pushed. The sync
 * bookkeeping ([remoteId], [isSynced], [isPendingDelete]) mirrors [FoodLogEntity] exactly.
 *
 * [ownerUserId] is null for the built-in presets. Those are shared rows, so they are read-only
 * in the UI — editing one produces a personal copy instead. Without that rule one user's delete
 * would remove a dish for everybody, and the seed would just put it back.
 */
@Entity(
    tableName = "food_presets",
    indices = [
        Index(value = ["ownerUserId"]),
        Index(value = ["remoteId"], unique = true)
    ]
)
data class FoodPresetEntity(
    @PrimaryKey val localId: String = UUID.randomUUID().toString(),
    val remoteId: Int? = null,
    /** null = built-in preset shared by every user, and read-only. */
    val ownerUserId: String? = null,
    val name: String,
    val brand: String? = null,
    val servingDescription: String? = null,
    val calories: Int,
    val protein: Float,
    val carbs: Float,
    val fat: Float,
    val isSynced: Boolean = false,
    /** Deleted locally, but the server copy still needs removing on the next sync. */
    val isPendingDelete: Boolean = false
) {
    val isBuiltIn: Boolean get() = ownerUserId == null
}

fun FoodPresetEntity.toRemote(): RemoteFoodPreset = RemoteFoodPreset(
    id = remoteId,
    userId = ownerUserId,
    name = name,
    brand = brand,
    servingDescription = servingDescription,
    calories = calories,
    protein = protein,
    carbs = carbs,
    fat = fat
)

fun RemoteFoodPreset.toPresetEntity(
    localId: String = UUID.randomUUID().toString(),
    isSynced: Boolean = true
): FoodPresetEntity = FoodPresetEntity(
    localId = localId,
    remoteId = id,
    ownerUserId = userId,
    name = name,
    brand = brand,
    servingDescription = servingDescription,
    calories = calories,
    protein = protein,
    carbs = carbs,
    fat = fat,
    isSynced = isSynced
)
