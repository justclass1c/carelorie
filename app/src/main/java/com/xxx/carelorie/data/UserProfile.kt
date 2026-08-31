package com.xxx.carelorie.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Everything the app knows about a user.
 *
 * The block below [fatLimit] is filled in by the onboarding flow. Every one of those fields is
 * nullable on purpose: onboarding is skippable, so a user can reach the dashboard having answered
 * none of them, and can come back and answer them later from the profile screen. Anything that
 * reads them has to cope with null rather than assume the flow ran.
 */
@Entity(tableName = "user_profiles")
data class UserProfile(
    @PrimaryKey val userId: String,
    val name: String = "",
    val birthday: String = "",
    val gender: String = "",
    val height: String = "",
    val liftingExperience: String = "",
    val weight: Float? = null,
    val weightAdvice: String? = null,
    val theme: String = "system",
    val calorieLimit: Int = 2000,
    val proteinLimit: Float = 120f,
    val carbsLimit: Float = 200f,
    val fatLimit: Float = 65f,

    // ---------------------------------------------------------------- onboarding answers
    /** "yes" / "no" / "unsure" — past weight history affects current metabolism. */
    val everWeighedOver95: String? = null,
    /** [WeightTrend] id. */
    val weightTrend: String? = null,
    /** [BodyFatBand] id. */
    val bodyFatBand: String? = null,
    /** [ExerciseFrequency] id. */
    val exerciseFrequency: String? = null,
    /** [ActivityLevel] id. */
    val activityLevel: String? = null,
    /** [ExperienceLevel] id — the lifting equivalent lives in [liftingExperience]. */
    val cardioExperience: String? = null,
    /** [Goal] id. */
    val goal: String? = null,
    val targetWeight: Float? = null,
    /** [DietType] id. */
    val dietType: String? = null,
    /** [TrainingType] id. */
    val trainingType: String? = null,
    /** [CalorieDistribution] id. */
    val calorieDistribution: String? = null,
    /** [ProteinPreference] id. */
    val proteinPreference: String? = null,
    /** Cached Mifflin-St Jeor result, so the summary screen and the AI agree on one number. */
    val estimatedTdee: Int? = null,
    /** ISO timestamp. Null while the flow has never been finished — which is a valid state. */
    val onboardingCompletedAt: String? = null,
    val isSynced: Boolean = false
) {
    fun toNutritionTargets(): NutritionTargets = NutritionTargets(
        calories = calorieLimit,
        proteinGrams = proteinLimit,
        carbsGrams = carbsLimit,
        fatGrams = fatLimit
    )

    /** True once the user has been all the way through onboarding at least once. */
    val hasCompletedOnboarding: Boolean get() = onboardingCompletedAt != null

    /**
     * How much of the plan is filled in, 0f..1f.
     *
     * Drives the "finish setting up" prompt on the profile screen, so a user who skipped can see
     * what answering the rest would buy them.
     */
    val onboardingProgress: Float
        get() {
            val answers = listOf(
                gender.ifBlank { null },
                birthday.ifBlank { null },
                height.ifBlank { null },
                weight?.toString(),
                everWeighedOver95, weightTrend, bodyFatBand,
                exerciseFrequency, activityLevel,
                liftingExperience.ifBlank { null }, cardioExperience,
                goal, dietType, trainingType, calorieDistribution, proteinPreference
            )
            return answers.count { it != null }.toFloat() / answers.size
        }
}
