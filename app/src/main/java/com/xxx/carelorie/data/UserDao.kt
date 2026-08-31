package com.xxx.carelorie.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface UserDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUser(user: User)

    /**
     * COLLATE NOCASE so an account stored as `Foo@Bar.com` is found by `foo@bar.com`.
     *
     * New rows are written lowercased, but accounts created before that was true keep whatever
     * casing was typed, and those users still have to be able to sign in.
     */
    @Query("SELECT * FROM users WHERE email = :email COLLATE NOCASE LIMIT 1")
    suspend fun getUserByEmail(email: String): User?

    @Query("SELECT * FROM users WHERE userId = :userId LIMIT 1")
    suspend fun getUserById(userId: String): User?

    @Query("DELETE FROM users WHERE userId = :userId")
    suspend fun deleteUser(userId: String)

    @Query("UPDATE users SET password = :passwordHash WHERE email = :email COLLATE NOCASE")
    suspend fun updatePasswordByEmail(email: String, passwordHash: String)

    @Query("UPDATE users SET recoveryKey = :recoveryKeyHash WHERE userId = :userId")
    suspend fun updateRecoveryKey(userId: String, recoveryKeyHash: String)
}
