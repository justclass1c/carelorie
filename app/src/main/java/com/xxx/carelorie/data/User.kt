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
     * ISO date the account was created, for the profile's "Member since" line.
     *
     * Null for accounts that existed before this column did — the profile screen falls back to
     * the earliest day they logged anything, which for those users is the better answer anyway.
     */
    val createdAt: String? = null,
    /**
     * Hashed one-time recovery key. The raw key is revealed to the user only once (on the
     * profile page); only this hash is stored, so it can verify a password reset but can never
     * be shown again. Empty = not generated yet.
     */
    val recoveryKey: String = ""
)
