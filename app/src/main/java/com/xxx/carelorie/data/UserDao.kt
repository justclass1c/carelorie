package com.xxx.carelorie.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface UserDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUser(user: User)

    @Query("SELECT * FROM users WHERE email = :email LIMIT 1")
    suspend fun getUserByEmail(email: String): User?

    @Query("SELECT * FROM users WHERE userId = :userId LIMIT 1")
    suspend fun getUserById(userId: String): User?

    @Query("DELETE FROM users WHERE userId = :userId")
    suspend fun deleteUser(userId: String)

    @Query("UPDATE users SET password = :passwordHash WHERE email = :email")
    suspend fun updatePasswordByEmail(email: String, passwordHash: String)

    @Query("UPDATE users SET recoveryKey = :recoveryKeyHash WHERE userId = :userId")
    suspend fun updateRecoveryKey(userId: String, recoveryKeyHash: String)
}
