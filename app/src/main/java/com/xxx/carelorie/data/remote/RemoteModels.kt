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
    val id: Int? = null,
    val userId: Int = -1,
    val mealType: String = "",
    val foodName: String = "",
    val calories: Int = 0,
    val protein: Float = 0f,
    val carbs: Float = 0f,
    val fat: Float = 0f,
    val createdAt: String = ""
)

@Serializable
data class RemoteFoodPreset(
    val id: Int? = null,
    val userId: Int? = null,
    val name: String = "",
    val calories: Int = 0,
    val protein: Float = 0f,
    val carbs: Float = 0f,
    val fat: Float = 0f,
    val imageUrl: String? = null
)
