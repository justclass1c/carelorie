package com.xxx.carelorie.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "user_profiles")
data class UserProfile(
    @PrimaryKey val userId: String,
    val name: String = "",
    val birthday: String = "",
    val gender: String = "",
    val height: String = "",
    val liftingExperience: String = "",
    val weight: Float? = null,
    val theme: String = "system",
    val calorieLimit: Int = 2000,
    val proteinLimit: Float = 120f,
    val carbsLimit: Float = 200f,
    val fatLimit: Float = 65f
) {
    fun toNutritionTargets(): NutritionTargets = NutritionTargets(
        calories = calorieLimit,
        proteinGrams = proteinLimit,
        carbsGrams = carbsLimit,
        fatGrams = fatLimit
    )
}
