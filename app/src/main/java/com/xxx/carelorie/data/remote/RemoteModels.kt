package com.xxx.carelorie.data.remote

import kotlinx.serialization.Serializable

@Serializable
data class RemoteMacroIntake(
    val userId: Int,
    val date: String, // ISO 8601 string
    val protein: Float,
    val carbs: Float,
    val fat: Float
)

@Serializable
data class RemoteFoodLog(
    val userId: Int,
    val mealType: String, // e.g., "Breakfast"
    val foodName: String,
    val calories: Int,
    val protein: Float,
    val carbs: Float,
    val fat: Float,
    val createdAt: String
)
