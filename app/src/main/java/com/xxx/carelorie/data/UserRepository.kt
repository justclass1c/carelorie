package com.xxx.carelorie.data

import com.xxx.carelorie.data.remote.RemoteUser
import com.xxx.carelorie.data.remote.RemoteUserProfile
import com.xxx.carelorie.data.remote.RemoteWeightRecord
import com.xxx.carelorie.data.remote.SupabaseRepository
import java.time.LocalDate

class UserRepository(
    private val userDao: UserDao,
    private val userProfileDao: UserProfileDao,
    private val weightDao: WeightDao,
    private val sessionManager: SessionManager,
    private val supabaseRepository: SupabaseRepository
) {
    fun saveSession(userId: String) = sessionManager.saveUserId(userId)
    fun getSessionUserId(): String = sessionManager.getUserId()
    fun clearSession() = sessionManager.clearSession()
    fun hasSession(): Boolean = getSessionUserId().isNotEmpty()

    suspend fun registerUser(user: User): Result<String> {
        return try {
            // 1. Sync to Supabase first to get a global unique ID
            val remoteUser = RemoteUser(
                userId = user.userId,
                email = user.email,
                password = user.password
            )
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
        // 1. Try remote first to get latest credentials and sync profile
        val remote = try {
            supabaseRepository.fetchUserByEmail(email)
        } catch (e: Exception) {
            null
        }

        if (remote != null && remote.userId != null) {
            val user = User(userId = remote.userId, email = remote.email, password = remote.password)
            userDao.insertUser(user)
            syncProfileWithRemote(remote.userId)
            return user
        }
        
        // 2. Fallback to local
        return userDao.getUserByEmail(email)
    }

    suspend fun syncProfileWithRemote(userId: String) {
        try {
            val remote = supabaseRepository.fetchProfile(userId)
            if (remote != null) {
                val profile = UserProfile(
                    userId = remote.userId,
                    name = remote.name,
                    birthday = fromDbDate(remote.birthday),
                    gender = remote.gender,
                    height = remote.height,
                    liftingExperience = remote.liftingExperience,
                    weight = remote.weight,
                    weightAdvice = remote.weightAdvice
                )
                userProfileDao.insertOrUpdateProfile(profile)
            }
        } catch (e: Exception) {
            // Silently fail if offline, we'll use local cache
        }
    }

    suspend fun saveProfile(profile: UserProfile) {
        val existing = userProfileDao.getProfileByUserId(profile.userId)

        // 1. Save locally
        userProfileDao.insertOrUpdateProfile(profile)

        // 2. Sync to remote
        supabaseRepository.upsertProfile(toRemoteProfile(profile))

        // 3. If the weight changed here (profile page), record it in the weight table
        //    so the graph on the goal page stays up to date no matter where it was edited.
        val newWeight = profile.weight
        if (newWeight != null && newWeight != existing?.weight) {
            saveWeight(profile.userId, newWeight, LocalDate.now().toString())
        }
    }

    private fun toRemoteProfile(profile: UserProfile): RemoteUserProfile = RemoteUserProfile(
        userId = profile.userId,
        name = profile.name,
        birthday = toDbDate(profile.birthday),
        gender = profile.gender,
        height = profile.height,
        liftingExperience = profile.liftingExperience,
        weight = profile.weight,
        weightAdvice = profile.weightAdvice
    )

    suspend fun getProfile(userId: String): UserProfile? {
        // Sync with remote first if possible to ensure we have latest data
        syncProfileWithRemote(userId)
        val profile = userProfileDao.getProfileByUserId(userId) ?: return null

        // Display the weight from the most recent date in the weight history.
        val latestWeight = weightDao.getAllWeightRecords(userId)
            .maxByOrNull { it.date }
            ?.weight
        return if (latestWeight != null) profile.copy(weight = latestWeight) else profile
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

    suspend fun saveWeight(userId: String, weight: Float, date: String) {
        // 1. Upsert locally — same-day updates replace the existing record instead of
        //    creating a new one.
        val existing = weightDao.getWeightForDay(userId, date)
        val record = WeightRecord(
            id = existing?.id ?: 0,
            userId = userId,
            date = date,
            weight = weight
        )
        weightDao.insertOrUpdateWeight(record)

        // 2. Upsert to the remote `weight` table (keyed by userId + date).
        supabaseRepository.saveWeightRecord(RemoteWeightRecord(userId = userId, weight = weight, date = date))

        // 3. Keep the profile's "current" weight in sync so the profile page always shows
        //    the latest updated weight.
        updateProfileWeight(userId, weight)
    }

    private suspend fun updateProfileWeight(userId: String, weight: Float) {
        val profile = getProfile(userId)
        if (profile != null && profile.weight != weight) {
            val updated = profile.copy(weight = weight)
            userProfileDao.insertOrUpdateProfile(updated)
            supabaseRepository.upsertProfile(toRemoteProfile(updated))
        }
    }

    suspend fun getWeightHistory(userId: String): List<WeightRecord> {
        // Sync remote records into the local cache first so the graph reflects weight
        // entries made on any screen or device.
        try {
            val remote = supabaseRepository.fetchWeightRecords(userId)
            for (r in remote) {
                val existing = weightDao.getWeightForDay(userId, r.date)
                weightDao.insertOrUpdateWeight(
                    WeightRecord(
                        id = existing?.id ?: 0,
                        userId = userId,
                        date = r.date,
                        weight = r.weight
                    )
                )
            }
        } catch (e: Exception) {
            // Offline: fall back to local cache
        }
        return weightDao.getAllWeightRecords(userId)
    }
}
