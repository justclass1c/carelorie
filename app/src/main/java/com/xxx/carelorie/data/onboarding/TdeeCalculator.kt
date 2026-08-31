package com.xxx.carelorie.data.onboarding

import com.xxx.carelorie.data.NutritionTargets
import com.xxx.carelorie.data.UserProfile
import java.time.LocalDate
import java.time.Period
import java.time.format.DateTimeFormatter

/**
 * Turns onboarding answers into daily targets.
 *
 * Mifflin-St Jeor for resting expenditure, an activity multiplier for total expenditure, then a
 * goal adjustment. Protein comes from the user's stated preference, fat from their diet type, and
 * carbohydrate takes whatever calories are left — which is why carbs are never asked about
 * directly.
 *
 * Everything here degrades: [estimate] returns null the moment a required answer is missing, and
 * callers fall back to [NutritionTargets.DEFAULT]. That is what makes onboarding skippable.
 */
object TdeeCalculator {

    /** Enough to compute a resting rate at all. */
    private fun canEstimate(p: UserProfile) =
        p.heightCm != null && p.weight != null && p.ageYears != null && p.gender.isNotBlank()

    val UserProfile.heightCm: Float?
        get() = height.trim().toFloatOrNull()?.takeIf { it in 80f..250f }

    val UserProfile.ageYears: Int?
        get() {
            val text = birthday.trim()
            if (text.isEmpty()) return null
            // The profile stores dd/MM/yyyy; Supabase hands back yyyy-MM-dd. Accept both.
            val parsed = listOf("dd/MM/yyyy", "yyyy-MM-dd").firstNotNullOfOrNull { pattern ->
                runCatching { LocalDate.parse(text, DateTimeFormatter.ofPattern(pattern)) }.getOrNull()
            } ?: return null
            val years = Period.between(parsed, LocalDate.now()).years
            return years.takeIf { it in 10..120 }
        }

    /**
     * Resting metabolic rate, kcal/day.
     *
     * Mifflin-St Jeor. The constant differs by sex; anything other than an explicit "Female"
     * uses the male constant, which is the conventional default for this equation.
     */
    private fun bmr(weightKg: Float, heightCm: Float, ageYears: Int, gender: String): Float {
        val base = 10f * weightKg + 6.25f * heightCm - 5f * ageYears
        return if (gender.equals("Female", ignoreCase = true)) base - 161f else base + 5f
    }

    /**
     * Activity multiplier.
     *
     * Blends the two questions the prototype asks — training sessions per week and daily step
     * count — because either alone under-describes someone. A desk worker who lifts five times a
     * week and a courier who never trains both land mid-range, which is right.
     */
    private fun activityFactor(exerciseFrequency: String?, activityLevel: String?): Float {
        val fromTraining = when (exerciseFrequency) {
            "0" -> 1.20f
            "1-3" -> 1.375f
            "4-6" -> 1.55f
            "7+" -> 1.725f
            else -> 1.375f
        }
        val fromSteps = when (activityLevel) {
            "sedentary" -> 1.20f
            "moderate" -> 1.45f
            "very" -> 1.70f
            else -> 1.375f
        }
        return (fromTraining + fromSteps) / 2f
    }

    /** Total daily energy expenditure, kcal. Null when the inputs are not there yet. */
    fun estimateTdee(profile: UserProfile): Int? {
        if (!canEstimate(profile)) return null
        val resting = bmr(
            weightKg = profile.weight!!,
            heightCm = profile.heightCm!!,
            ageYears = profile.ageYears!!,
            gender = profile.gender
        )
        val total = resting * activityFactor(profile.exerciseFrequency, profile.activityLevel)
        return total.toInt()
    }

    /**
     * Daily calorie target after the goal adjustment.
     *
     * A 15% deficit and a 10% surplus are the usual conservative defaults — aggressive enough to
     * move the trend line, gentle enough that the targets stay reachable.
     */
    private fun goalAdjusted(tdee: Int, goal: String?): Int = when (goal) {
        "lose" -> (tdee * 0.85f).toInt()
        "gain" -> (tdee * 1.10f).toInt()
        else -> tdee
    }

    /** Grams of protein per kilogram of bodyweight. */
    private fun proteinPerKg(preference: String?): Float = when (preference) {
        "low" -> 1.2f
        "high" -> 2.0f
        "extra-high" -> 2.4f
        else -> 1.6f
    }

    /** Share of total calories that comes from fat. */
    private fun fatShare(dietType: String?): Float = when (dietType) {
        "low-fat" -> 0.20f
        "low-carb" -> 0.40f
        "keto" -> 0.70f
        else -> 0.30f
    }

    /**
     * Full daily targets, or null when onboarding has not supplied enough to compute them.
     *
     * Callers should fall back to [NutritionTargets.DEFAULT] on null rather than showing nothing —
     * a skipped onboarding still has to produce a usable dashboard.
     */
    fun estimate(profile: UserProfile): NutritionTargets? {
        val tdee = estimateTdee(profile) ?: return null
        val weightKg = profile.weight ?: return null

        val calories = goalAdjusted(tdee, profile.goal)

        val proteinGrams = weightKg * proteinPerKg(profile.proteinPreference)
        val fatGrams = (calories * fatShare(profile.dietType)) / 9f

        // Carbohydrate is the remainder. Protein and fat can in principle over-spend the budget
        // on a keto cut, so floor it rather than emitting a negative target.
        val remaining = calories - (proteinGrams * 4f) - (fatGrams * 9f)
        val carbsGrams = (remaining / 4f).coerceAtLeast(0f)

        return NutritionTargets(
            calories = calories,
            proteinGrams = proteinGrams.roundToWhole(),
            carbsGrams = carbsGrams.roundToWhole(),
            fatGrams = fatGrams.roundToWhole()
        )
    }

    private fun Float.roundToWhole(): Float = kotlin.math.round(this)
}
