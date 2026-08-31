package com.xxx.carelorie.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface UserProfileDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateProfile(profile: UserProfile)

    @Query("SELECT * FROM user_profiles WHERE userId = :userId LIMIT 1")
    suspend fun getProfileByUserId(userId: String): UserProfile?

    @Query("SELECT * FROM user_profiles WHERE isSynced = 0")
    suspend fun getUnsynced(): List<UserProfile>

    @Query("UPDATE user_profiles SET isSynced = 1 WHERE userId = :userId")
    suspend fun markSynced(userId: String)

    @Query("DELETE FROM user_profiles WHERE userId = :userId")
    suspend fun deleteProfile(userId: String)
}
