package com.xxx.carelorie.data

import com.xxx.carelorie.data.remote.RemoteFoodPreset

/**
 * Seed data used the first time the app runs, or as a fallback when the presets table
 * cannot be reached.
 *
 * Weighted towards Malaysian dishes: the target users are local, and a tracker that already
 * knows nasi lemak takes far fewer taps than one where every meal has to be entered by hand.
 * Values are typical single-serving figures from Malaysian nutrient composition references
 * and are approximations, as all portion-based estimates are.
 */
object DefaultFoodPresets {

    val ALL: List<RemoteFoodPreset> = listOf(
        // --- Malaysian mains
        RemoteFoodPreset(name = "Nasi Lemak (with sambal & egg)", calories = 644, protein = 17f, carbs = 81f, fat = 27f),
        RemoteFoodPreset(name = "Nasi Goreng Kampung", calories = 637, protein = 20f, carbs = 84f, fat = 24f),
        RemoteFoodPreset(name = "Chicken Rice", calories = 607, protein = 30f, carbs = 75f, fat = 20f),
        RemoteFoodPreset(name = "Char Kuey Teow", calories = 742, protein = 23f, carbs = 76f, fat = 38f),
        RemoteFoodPreset(name = "Mee Goreng Mamak", calories = 660, protein = 19f, carbs = 82f, fat = 28f),
        RemoteFoodPreset(name = "Roti Canai (plain)", calories = 301, protein = 6f, carbs = 40f, fat = 13f),
        RemoteFoodPreset(name = "Nasi Kandar (chicken)", calories = 720, protein = 33f, carbs = 82f, fat = 29f),
        RemoteFoodPreset(name = "Laksa (curry)", calories = 578, protein = 21f, carbs = 60f, fat = 28f),
        RemoteFoodPreset(name = "Wantan Mee (dry)", calories = 512, protein = 24f, carbs = 66f, fat = 17f),
        RemoteFoodPreset(name = "Bak Kut Teh", calories = 420, protein = 34f, carbs = 12f, fat = 26f),
        RemoteFoodPreset(name = "Satay Chicken (5 sticks)", calories = 225, protein = 24f, carbs = 8f, fat = 11f),
        RemoteFoodPreset(name = "Roti Telur", calories = 400, protein = 12f, carbs = 44f, fat = 19f),
        RemoteFoodPreset(name = "Nasi Ayam Penyet", calories = 690, protein = 34f, carbs = 78f, fat = 26f),
        RemoteFoodPreset(name = "Curry Puff (1 piece)", calories = 130, protein = 3f, carbs = 15f, fat = 7f),

        // --- Drinks
        RemoteFoodPreset(name = "Teh Tarik", calories = 180, protein = 4f, carbs = 26f, fat = 6f),
        RemoteFoodPreset(name = "Kopi O Kosong", calories = 5, protein = 0.2f, carbs = 1f, fat = 0f),
        RemoteFoodPreset(name = "Milo Ais", calories = 220, protein = 6f, carbs = 36f, fat = 6f),
        RemoteFoodPreset(name = "Sirap Bandung", calories = 195, protein = 4f, carbs = 35f, fat = 5f),

        // --- Staples and whole foods
        RemoteFoodPreset(name = "White Rice (1 bowl)", calories = 200, protein = 4f, carbs = 45f, fat = 0.5f),
        RemoteFoodPreset(name = "Brown Rice (1 bowl)", calories = 216, protein = 5f, carbs = 45f, fat = 1.8f),
        RemoteFoodPreset(name = "Chicken Breast (100g)", calories = 165, protein = 31f, carbs = 0f, fat = 3.6f),
        RemoteFoodPreset(name = "Egg (1 large)", calories = 70, protein = 6f, carbs = 0.6f, fat = 5f),
        RemoteFoodPreset(name = "Omelette (2 eggs)", calories = 190, protein = 13f, carbs = 1f, fat = 15f),
        RemoteFoodPreset(name = "Tofu (100g)", calories = 76, protein = 8f, carbs = 1.9f, fat = 4.8f),
        RemoteFoodPreset(name = "Tempeh (100g)", calories = 192, protein = 20f, carbs = 8f, fat = 11f),
        RemoteFoodPreset(name = "Broccoli (100g)", calories = 55, protein = 3.7f, carbs = 11f, fat = 0.6f),
        RemoteFoodPreset(name = "Banana (1 medium)", calories = 105, protein = 1.3f, carbs = 27f, fat = 0.4f),
        RemoteFoodPreset(name = "Orange Juice (250ml)", calories = 110, protein = 2f, carbs = 26f, fat = 0.5f)
    )
}
