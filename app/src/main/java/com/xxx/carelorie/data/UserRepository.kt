package com.xxx.carelorie.data

class UserRepository(
    private val userDao: UserDao,
    private val userProfileDao: UserProfileDao
) {
    suspend fun registerUser(user: User): Result<Int> {
        return try {
            val id = userDao.insertUser(user)
            Result.success(id.toInt())
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getUserByEmail(email: String): User? {
        return userDao.getUserByEmail(email)
    }

    suspend fun saveProfile(profile: UserProfile) {
        userProfileDao.insertOrUpdateProfile(profile)
    }

    suspend fun getProfile(userId: Int): UserProfile? {
        return userProfileDao.getProfileByUserId(userId)
    }
}
