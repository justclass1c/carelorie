package com.xxx.carelorie.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(
    tableName = "users",
    indices = [Index(value = ["email"], unique = true)]
)
data class User(
    @PrimaryKey val userId: String = UUID.randomUUID().toString(),
    val email: String,
    val password: String
)
