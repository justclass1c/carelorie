package com.xxx.carelorie.data

class UserRepository(
    private val userDao: UserDao,
    private val userProfileDao: UserProfileDao,
    private val weightDao: WeightDao
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

    suspend fun saveWeight(userId: Int, weight: Float, date: String) {
        val existing = weightDao.getWeightForDay(userId, date)
        val record = WeightRecord(
            id = existing?.id ?: 0,
            userId = userId,
            date = date,
            weight = weight
        )
        weightDao.insertOrUpdateWeight(record)
    }

    suspend fun getWeightHistory(userId: Int): List<WeightRecord> {
        return weightDao.getAllWeightRecords(userId)
    }
}
