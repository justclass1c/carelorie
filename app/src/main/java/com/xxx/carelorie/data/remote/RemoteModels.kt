package com.xxx.carelorie.data.remote

import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient


@Serializable
data class RemoteFoodLog(
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val id: Int? = null,
    val userId: String = "",
    val mealType: String = "",
    val foodName: String = "",
    val calories: Int = 0,
    val protein: Float = 0f,
    val carbs: Float = 0f,
    val fat: Float = 0f,
    val createdAt: String = "",
    /**
     * Local Room key. Marked @Transient so it is never sent to Supabase — that table has no
     * such column. It lets the UI address an entry that has not been given a server id yet.
     */
    @Transient val localId: String = "",

    // Servings and the nutrition breakdown.
    //
    // These used to be @Transient because food_logs had no columns for them, which meant they
    // only ever existed on the phone that logged the entry: a second device got the diary back
    // with every row reading one serving and no breakdown. 002_sync_meals_and_nutrition.sql adds
    // the columns; this build requires it to have been run.
    //
    // Nullable because rows written before those columns existed come back as null, and a
    // non-null Float with a default would fail to decode on an explicit null rather than falling
    // back to it.
    val quantity: Float? = null,
    val brand: String? = null,
    val servingDescription: String? = null,
    val fiberGrams: Float? = null,
    val sugarGrams: Float? = null,
    val saturatedFatGrams: Float? = null,
    val sodiumMilligrams: Float? = null,
    val nutritionSource: String? = null
) {
    val hasNutritionDetail: Boolean
        get() = listOfNotNull(fiberGrams, sugarGrams, saturatedFatGrams, sodiumMilligrams).isNotEmpty()

    /**
     * Servings, as a number the UI can just use.
     *
     * [quantity] is null on entries written before `food_logs` had the column. One serving is what
     * the app displayed for those anyway, so that is what they read as — the rule lives here
     * rather than as a `?: 1f` at each of the places that needs it.
     */
    val servings: Float get() = quantity ?: 1f
}

@Serializable
data class RemoteFoodPreset(
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val id: Int? = null,
    /** null marks a built-in preset shared by every user. */
    val userId: String? = null,
    val name: String = "",
    val brand: String? = null,
    /** Free text, e.g. "1 plate (350 g)". Macros above are per one of these. */
    val servingDescription: String? = null,
    val calories: Int = 0,
    val protein: Float = 0f,
    val carbs: Float = 0f,
    val fat: Float = 0f,
    val imageUrl: String? = null
)

@Serializable
data class RemoteUser(
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val userId: String? = null,
    val email: String,
    val password: String,
    val recoveryKey: String = ""
)

@Serializable
data class RemoteUserProfile(
    val userId: String,
    val name: String = "",
    val birthday: String? = null,
    val gender: String = "",
    val height: String = "",
    val liftingExperience: String = "",
    val weight: Float? = null,
    val weightAdvice: String? = null,
    // Onboarding answers. Every one is nullable so a skipped setup round-trips cleanly, and so
    // adding them here could never break an insert for a user who answered nothing.
    val everWeighedOver95: String? = null,
    val weightTrend: String? = null,
    val bodyFatBand: String? = null,
    val exerciseFrequency: String? = null,
    val activityLevel: String? = null,
    val cardioExperience: String? = null,
    val goal: String? = null,
    val targetWeight: Float? = null,
    val dietType: String? = null,
    val trainingType: String? = null,
    val calorieDistribution: String? = null,
    val proteinPreference: String? = null,
    val estimatedTdee: Int? = null,
    val onboardingCompletedAt: String? = null,
    val theme: String = "system",
    val calorieLimit: Int = 2000,
    val proteinLimit: Float = 120f,
    val carbsLimit: Float = 200f,
    val fatLimit: Float = 65f
)

/**
 * A saved meal on the server.
 *
 * Keyed by the device-generated [localId] rather than a serial, so pushing one is a single upsert
 * and the same meal never lands twice. Items live in [RemoteMealPresetItem] and are replaced
 * wholesale, matching how the local DAO treats them — a meal owns its foods and they are never
 * edited alone.
 */
@Serializable
data class RemoteMealPreset(
    val localId: String,
    val ownerUserId: String,
    val name: String = "",
    val mealType: String = "",
    val createdAt: String = ""
)

@Serializable
data class RemoteMealPresetItem(
    val localId: String,
    val mealPresetId: String,
    val foodName: String = "",
    val calories: Int = 0,
    val protein: Float = 0f,
    val carbs: Float = 0f,
    val fat: Float = 0f,
    val quantity: Float = 1f,
    val sourcePresetId: String? = null
)

@Serializable
data class RemoteWeightRecord(
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val id: Long? = null,
    val userId: String,
    val weight: Float,
    val date: String
)

// (No patch DTOs needed: password and recovery-key changes upsert the full RemoteUser row.)
