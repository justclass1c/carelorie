package com.xxx.carelorie.data.onboarding

/**
 * One answer a user can pick.
 *
 * [id] is what gets stored on the profile and sent to the AI, so it must stay stable even if
 * [label] is reworded. [detail] is the small grey line under the label in the prototype
 * ("5000 - 15000 steps", "Lifting for the past year or less").
 */
data class Choice(
    val id: String,
    val label: String,
    val detail: String? = null
)

/**
 * The fixed answer sets, one object per question.
 *
 * These are the single source of truth for both the onboarding flow and the profile editor, so
 * the two can never drift into offering different options for the same field.
 */
object Choices {

    val Gender = listOf(
        Choice("Male", "Male"),
        Choice("Female", "Female")
    )

    val EverWeighedOver95 = listOf(
        Choice("yes", "Yes"),
        Choice("no", "No"),
        Choice("unsure", "Not sure")
    )

    val WeightTrend = listOf(
        Choice("losing", "I have been losing weight"),
        Choice("gaining", "I have been gaining weight"),
        Choice("stable", "I have been weight stable"),
        Choice("unsure", "Not sure")
    )

    /** Nine bands, matching the 3x3 grid in the prototype. */
    val BodyFat = listOf(
        Choice("3-4", "3-4%"), Choice("5-9", "5-9%"), Choice("10-14", "10-14%"),
        Choice("15-19", "15-19%"), Choice("20-24", "20-24%"), Choice("25-29", "25-29%"),
        Choice("30-34", "30-34%"), Choice("35-39", "35-39%"), Choice("40+", "40%+")
    )

    val ExerciseFrequency = listOf(
        Choice("0", "0 sessions / week"),
        Choice("1-3", "1-3 sessions / week"),
        Choice("4-6", "4-6 sessions / week"),
        Choice("7+", "7+ sessions / week")
    )

    val ActivityLevel = listOf(
        Choice("sedentary", "Mostly sedentary", "Under 5,000 steps"),
        Choice("moderate", "Moderately active", "5,000 - 15,000 steps"),
        Choice("very", "Very active", "Over 15,000 steps")
    )

    /** Shared by the lifting and cardio questions — they offer the same four bands. */
    val Experience = listOf(
        Choice("none", "None"),
        Choice("beginner", "Beginner", "A year or less"),
        Choice("intermediate", "Intermediate", "More than 1 year, under 4"),
        Choice("advanced", "Advanced", "More than 4 years")
    )

    val Goal = listOf(
        Choice("lose", "Lose weight"),
        Choice("maintain", "Maintain weight"),
        Choice("gain", "Gain weight")
    )

    val DietType = listOf(
        Choice("balanced", "Balanced"),
        Choice("low-fat", "Low fat"),
        Choice("low-carb", "Low carb"),
        Choice("keto", "Keto")
    )

    val TrainingType = listOf(
        Choice("none", "None or relaxed activity"),
        Choice("lifting", "Lifting"),
        Choice("cardio", "Cardio"),
        Choice("both", "Cardio and lifting")
    )

    val CalorieDistribution = listOf(
        Choice("even", "Distribute evenly", "The same target every day"),
        Choice("shift", "Shift calories", "More on training days, fewer on rest days")
    )

    val ProteinPreference = listOf(
        Choice("low", "Low", "1.2 g per kg"),
        Choice("moderate", "Moderate", "1.6 g per kg"),
        Choice("high", "High", "2.0 g per kg"),
        Choice("extra-high", "Extra high", "2.4 g per kg")
    )

    /** Human-readable label for a stored id, for the profile screen and the AI prompts. */
    fun labelFor(options: List<Choice>, id: String?): String? =
        id?.let { stored -> options.firstOrNull { it.id == stored }?.label }
}
