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
    val weightAdvice: String? = null
)
