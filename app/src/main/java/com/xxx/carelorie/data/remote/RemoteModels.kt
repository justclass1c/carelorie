package com.xxx.carelorie.data.remote

import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

@Serializable
data class RemoteMacroIntake(
    val userId: String,
    val date: String, // ISO 8601 string
    val protein: Float,
    val carbs: Float,
    val fat: Float
)

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

    // Device-only, for the same reason as localId: food_logs has no columns for them. They let
    // the diary show servings and a nutrition breakdown without a database migration.
    @Transient val quantity: Float = 1f,
    @Transient val brand: String? = null,
    @Transient val servingDescription: String? = null,
    @Transient val fiberGrams: Float? = null,
    @Transient val sugarGrams: Float? = null,
    @Transient val saturatedFatGrams: Float? = null,
    @Transient val sodiumMilligrams: Float? = null,
    @Transient val nutritionSource: String? = null
) {
    val hasNutritionDetail: Boolean
        get() = listOfNotNull(fiberGrams, sugarGrams, saturatedFatGrams, sodiumMilligrams).isNotEmpty()
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
    val password: String
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
    val theme: String = "system",
    val calorieLimit: Int = 2000,
    val proteinLimit: Float = 120f,
    val carbsLimit: Float = 200f,
    val fatLimit: Float = 65f
)

@Serializable
data class RemoteWeightRecord(
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val id: Long? = null,
    val userId: String,
    val weight: Float,
    val date: String
)
