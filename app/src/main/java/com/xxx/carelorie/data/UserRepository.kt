package com.xxx.carelorie.data

import com.xxx.carelorie.data.remote.RemoteUser
import com.xxx.carelorie.data.remote.RemoteUserProfile
import com.xxx.carelorie.data.remote.SupabaseRepository

class UserRepository(
    private val userDao: UserDao,
    private val userProfileDao: UserProfileDao,
    private val weightDao: WeightDao,
    private val sessionManager: SessionManager,
    private val supabaseRepository: SupabaseRepository
) {
    fun saveSession(userId: Int) = sessionManager.saveUserId(userId)
    fun getSessionUserId(): Int = sessionManager.getUserId()
    fun clearSession() = sessionManager.clearSession()
    fun hasSession(): Boolean = getSessionUserId() != -1

    suspend fun registerUser(user: User): Result<Int> {
        return try {
            // 1. Sync to Supabase first to get a global unique ID
            val remoteUser = RemoteUser(email = user.email, password = user.password)
            val registeredRemote = supabaseRepository.insertUser(remoteUser)
                ?: return Result.failure(Exception("Supabase registration failed"))
            
            val globalUserId = registeredRemote.userId ?: return Result.failure(Exception("No userId returned"))
            
            // 2. Save locally with the same ID
            val localUser = user.copy(userId = globalUserId)
            userDao.insertUser(localUser)
            
            Result.success(globalUserId)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getUserByEmail(email: String): User? {
        // 1. Try local
        val local = userDao.getUserByEmail(email)
        if (local != null) return local
        
        // 2. Try remote (Sync from other device)
        val remote = supabaseRepository.fetchUserByEmail(email)
        if (remote != null && remote.userId != null) {
            val user = User(userId = remote.userId, email = remote.email, password = remote.password)
            userDao.insertUser(user)
            
            // Also sync profile if it exists
            val remoteProfile = supabaseRepository.fetchProfile(remote.userId)
            if (remoteProfile != null) {
                val profile = UserProfile(
                    userId = remoteProfile.userId,
                    name = remoteProfile.name,
                    birthday = fromDbDate(remoteProfile.birthday),
                    gender = remoteProfile.gender,
                    height = remoteProfile.height,
                    liftingExperience = remoteProfile.liftingExperience
                )
                userProfileDao.insertOrUpdateProfile(profile)
            }
            
            return user
        }
        
        return null
    }

    suspend fun saveProfile(profile: UserProfile) {
        // 1. Save locally
        userProfileDao.insertOrUpdateProfile(profile)
        
        // 2. Sync to remote
        val remote = RemoteUserProfile(
            userId = profile.userId,
            name = profile.name,
            birthday = toDbDate(profile.birthday),
            gender = profile.gender,
            height = profile.height,
            liftingExperience = profile.liftingExperience
        )
        supabaseRepository.upsertProfile(remote)
    }

    suspend fun getProfile(userId: Int): UserProfile? {
        // 1. Try local
        val local = userProfileDao.getProfileByUserId(userId)
        if (local != null) return local
        
        // 2. Try remote
        val remote = supabaseRepository.fetchProfile(userId)
        if (remote != null) {
            val profile = UserProfile(
                userId = remote.userId,
                name = remote.name,
                birthday = fromDbDate(remote.birthday),
                gender = remote.gender,
                height = remote.height,
                liftingExperience = remote.liftingExperience
            )
            userProfileDao.insertOrUpdateProfile(profile)
            return profile
        }
        
        return null
    }

    /** Converts dd/mm/yyyy to YYYY-MM-DD for Supabase 'date' type */
    private fun toDbDate(uiDate: String): String? {
        if (uiDate.isEmpty()) return null
        return try {
            val parts = uiDate.split("/")
            if (parts.size == 3) {
                "${parts[2]}-${parts[1]}-${parts[0]}"
            } else null
        } catch (e: Exception) {
            null
        }
    }

    /** Converts YYYY-MM-DD from Supabase to dd/mm/yyyy for UI */
    private fun fromDbDate(dbDate: String?): String {
        if (dbDate.isNullOrEmpty()) return ""
        return try {
            val parts = dbDate.split("-")
            if (parts.size == 3) {
                "${parts[2]}/${parts[1]}/${parts[0]}"
            } else ""
        } catch (e: Exception) {
            ""
        }
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
