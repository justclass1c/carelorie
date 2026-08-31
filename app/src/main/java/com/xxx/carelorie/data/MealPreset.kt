package com.xxx.carelorie.data

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation
import java.util.UUID

/**
 * A named group of foods the user eats together — "Post-gym shake", "Usual breakfast".
 *
 * Saved from a meal card on the dashboard, then logged again in one tap. Local only: there is no
 * `meal_presets` table in Supabase, and inventing one client-side would fail every insert. Sync
 * can come later without changing anything here.
 */
@Entity(tableName = "meal_presets")
data class MealPresetEntity(
    @PrimaryKey val localId: String = UUID.randomUUID().toString(),
    val ownerUserId: String,
    val name: String,
    /** The meal it was saved from, used as the default when logging it again. */
    val mealType: String,
    /** ISO timestamp, so the list can show newest first. */
    val createdAt: String
)

/**
 * One food inside a saved meal.
 *
 * Holds its own copy of the nutrition rather than pointing at a food preset, so editing or
 * deleting a food in the library cannot silently change what a saved meal logs.
 */
@Entity(
    tableName = "meal_preset_items",
    indices = [Index(value = ["mealPresetId"])]
)
data class MealPresetItemEntity(
    @PrimaryKey val localId: String = UUID.randomUUID().toString(),
    val mealPresetId: String,
    val foodName: String,
    /** Totals for [quantity] servings, matching how FoodLogEntity stores them. */
    val calories: Int,
    val protein: Float,
    val carbs: Float,
    val fat: Float,
    val quantity: Float = 1f,
    val sourcePresetId: String? = null
)

/** A saved meal and everything in it. */
data class MealPresetWithItems(
    @Embedded val meal: MealPresetEntity,
    @Relation(parentColumn = "localId", entityColumn = "mealPresetId")
    val items: List<MealPresetItemEntity>
) {
    val totalCalories: Int get() = items.sumOf { it.calories }
    val totalProtein: Float get() = items.sumOf { it.protein.toDouble() }.toFloat()
    val totalCarbs: Float get() = items.sumOf { it.carbs.toDouble() }.toFloat()
    val totalFat: Float get() = items.sumOf { it.fat.toDouble() }.toFloat()
}
