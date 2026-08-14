package com.xxx.carelorie.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "user_profiles")
data class UserProfile(
    @PrimaryKey val userId: Int,
    val name: String = "",
    val birthday: String = "",
    val gender: String = "",
    val height: String = "",
    val liftingExperience: String = ""
)
