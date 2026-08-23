package com.xxx.carelorie.data.nutrition

import com.xxx.carelorie.data.remote.RemoteFoodPreset

/**
 * The detailed nutrition panel shown on the Review Foods screen.
 *
 * Every field is nullable on purpose. Real nutrition databases are patchy — a product may
 * list fibre but not potassium — and showing "0 g" for a value nobody measured is worse than
 * showing nothing. The UI hides what is null rather than inventing a zero.
 *
 * Amounts are per serving, matching the calories/macros on the parent food.
 */
data class NutritionDetail(
    val servingDescription: String? = null,
    val fiberGrams: Float? = null,
    val sugarGrams: Float? = null,
    val saturatedFatGrams: Float? = null,
    val sodiumMilligrams: Float? = null,
    val cholesterolMilligrams: Float? = null,
    val potassiumMilligrams: Float? = null,
    val brand: String? = null,
    /** Where the numbers came from, shown to the user so estimates aren't mistaken for facts. */
    val source: NutritionSource = NutritionSource.APP_PRESET
) {
    val hasAnyDetail: Boolean
        get() = listOfNotNull(
            fiberGrams, sugarGrams, saturatedFatGrams,
            sodiumMilligrams, cholesterolMilligrams, potassiumMilligrams
        ).isNotEmpty()
}

enum class NutritionSource(val label: String, val isEstimate: Boolean) {
    APP_PRESET("Carelorie preset", false),
    OPEN_FOOD_FACTS("Open Food Facts", false),
    BARCODE("Scanned barcode", false),
    AI_ESTIMATE("AI estimate", true),
    USER_ENTERED("Entered by you", false)
}

/**
 * A food the user has picked but not yet logged.
 *
 * Selection, quantity and the nutrition panel all live here, so Review Foods can show totals
 * and adjust portions before anything is written to the log.
 */
data class FoodCandidate(
    val preset: RemoteFoodPreset,
    val detail: NutritionDetail? = null,
    val quantity: Float = 1f
) {
    val calories: Int get() = (preset.calories * quantity).toInt()
    val protein: Float get() = preset.protein * quantity
    val carbs: Float get() = preset.carbs * quantity
    val fat: Float get() = preset.fat * quantity

    /** The preset scaled by quantity, ready to hand to the food log. */
    fun toLoggablePreset(): RemoteFoodPreset {
        if (quantity == 1f) return preset
        val suffix = if (quantity % 1f == 0f) "x${quantity.toInt()}" else "x$quantity"
        return preset.copy(
            name = "${preset.name} ($suffix)",
            calories = calories,
            protein = protein,
            carbs = carbs,
            fat = fat
        )
    }
}
