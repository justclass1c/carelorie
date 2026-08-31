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
    val password: String,
    /**
     * Hashed one-time recovery key. The raw key is revealed to the user only once (on the
     * profile page); only this hash is stored, so it can verify a password reset but can never
     * be shown again. Empty = not generated yet.
     */
    val recoveryKey: String = ""
)
