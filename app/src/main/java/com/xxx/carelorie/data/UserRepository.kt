package com.xxx.carelorie.data

import com.xxx.carelorie.data.remote.RemoteUser
import com.xxx.carelorie.data.remote.RemoteUserProfile
import com.xxx.carelorie.data.remote.RemoteWeightRecord
import com.xxx.carelorie.data.remote.SupabaseRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate

class UserRepository(
    private val userDao: UserDao,
    private val userProfileDao: UserProfileDao,
    private val weightDao: WeightDao,
    private val sessionManager: SessionManager,
    private val supabaseRepository: SupabaseRepository,
    private val connectivity: ConnectivityChecker = AlwaysOnlineChecker()
) {
    fun clearSession() = sessionManager.clearSession()

    /**
     * The one form of an email address this app stores or looks up by.
     *
     * Addresses are case-insensitive in practice, and a trailing space from keyboard autocomplete
     * is not part of anybody's address. Normalising in one place is what stops "register as
     * Foo@Bar.com, fail to log in as foo@bar.com".
     */
    private fun normaliseEmail(email: String): String = email.trim().lowercase()

    /**
     * Creates an account.
     *
     * Local first, on purpose. [User.userId] is a UUID generated on the device, so Supabase is a
     * mirror of the account rather than the source of its identity — the previous version round
     * tripped that UUID through the server and treated a failed round trip as a failed
     * registration, which meant no connection meant no account at all.
     *
     * The push is best effort. An account created offline reaches Supabase on the next successful
     * sign-in (see [getUserByEmail]).
     */
    suspend fun registerUser(user: User): Result<String> {
        return try {
            val stored = user.copy(
                email = normaliseEmail(user.email),
                password = hashPassword(user.password),
                // Stamped here rather than defaulted on the entity, so re-inserting a user
                // fetched from Supabase during sign-in cannot reset their join date.
                createdAt = user.createdAt ?: LocalDate.now().toString()
            )
            userDao.insertUser(stored)
            pushUser(stored)
            Result.success(stored.userId)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Verifies credentials.
     *
     * Comparison lives here rather than in the ViewModel so the stored hash never travels further
     * into the app than it has to.
     */
    suspend fun authenticate(email: String, password: String): User? {
        val user = getUserByEmail(email) ?: return null
        val matches = withContext(Dispatchers.Default) {
            PasswordHasher.verify(password, user.password)
        }
        return if (matches) user else null
    }

    suspend fun getUserByEmail(email: String): User? {
        val normalised = normaliseEmail(email)

        // 1. Try remote first to get latest credentials and sync profile
        val remote = try {
            supabaseRepository.fetchUserByEmail(normalised)
        } catch (e: Exception) {
            null
        }

        if (remote != null && remote.userId != null) {
            val user = User(
                userId = remote.userId,
                // Store the canonical form, so an account created before emails were normalised
                // is healed the first time its owner signs in.
                email = normaliseEmail(remote.email),
                password = remote.password,
                recoveryKey = remote.recoveryKey
            )
            userDao.insertUser(user)
            syncProfileWithRemote(remote.userId)
            return user
        }

        // 2. Fall back to local. Reaching here means either no connection, or an account that was
        //    created offline and has never been pushed — so retry the push, which is what lets a
        //    registration made on a dead network heal itself.
        val local = userDao.getUserByEmail(normalised) ?: return null
        pushUser(local)
        return local
    }

    /** Best-effort mirror of a local account to Supabase. Failure is expected and ignored. */
    private suspend fun pushUser(user: User) {
        runCatching {
            supabaseRepository.upsertUser(user.toRemoteUser())
        }
    }

    private fun User.toRemoteUser() = RemoteUser(
        userId = userId,
        email = email,
        password = password,
        recoveryKey = recoveryKey
    )

    private suspend fun hashPassword(raw: String): String = withContext(Dispatchers.Default) {
        PasswordHasher.hash(raw)
    }

    suspend fun syncProfileWithRemote(userId: String) {
        try {
            val remote = supabaseRepository.fetchProfile(userId)
            if (remote != null) {
                val local = userProfileDao.getProfileByUserId(userId)
                // A local profile with un-pushed changes is newer than whatever the server has.
                if (local?.isSynced == false) return

                val profile = UserProfile(
                    userId = remote.userId,
                    name = remote.name,
                    birthday = fromDbDate(remote.birthday),
                    gender = remote.gender,
                    height = remote.height,
                    liftingExperience = remote.liftingExperience,
                    weight = remote.weight,
                    weightAdvice = remote.weightAdvice,
                    everWeighedOver95 = remote.everWeighedOver95,
                    weightTrend = remote.weightTrend,
                    bodyFatBand = remote.bodyFatBand,
                    exerciseFrequency = remote.exerciseFrequency,
                    activityLevel = remote.activityLevel,
                    cardioExperience = remote.cardioExperience,
                    goal = remote.goal,
                    targetWeight = remote.targetWeight,
                    dietType = remote.dietType,
                    trainingType = remote.trainingType,
                    calorieDistribution = remote.calorieDistribution,
                    proteinPreference = remote.proteinPreference,
                    estimatedTdee = remote.estimatedTdee,
                    onboardingCompletedAt = remote.onboardingCompletedAt,
                    theme = remote.theme,
                    calorieLimit = remote.calorieLimit,
                    proteinLimit = remote.proteinLimit,
                    carbsLimit = remote.carbsLimit,
                    fatLimit = remote.fatLimit,
                    isSynced = true
                )
                userProfileDao.insertOrUpdateProfile(profile)
            }
        } catch (e: Exception) {
            // Silently fail if offline, we'll use local cache
        }
    }

    suspend fun saveProfile(profile: UserProfile) {
        val existing = userProfileDao.getProfileByUserId(profile.userId)

        persistProfile(profile.copy(isSynced = false))

        // If the weight changed here (profile page), record it in the weight table
        // so the graph on the goal page stays up to date no matter where it was edited.
        val newWeight = profile.weight
        if (newWeight != null && newWeight != existing?.weight) {
            saveWeight(profile.userId, newWeight, LocalDate.now().toString())
        }
    }

    private suspend fun persistProfile(profile: UserProfile) {
        // Save locally first; the remote call is best-effort.
        userProfileDao.insertOrUpdateProfile(profile)
        if (!connectivity.isOnline()) return
        try {
            supabaseRepository.upsertProfile(toRemoteProfile(profile))
            userProfileDao.markSynced(profile.userId)
        } catch (e: Exception) {
            // Leave unsynced; [flushOutbox] will retry once the connection is back.
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
        weightAdvice = profile.weightAdvice,
        everWeighedOver95 = profile.everWeighedOver95,
        weightTrend = profile.weightTrend,
        bodyFatBand = profile.bodyFatBand,
        exerciseFrequency = profile.exerciseFrequency,
        activityLevel = profile.activityLevel,
        cardioExperience = profile.cardioExperience,
        goal = profile.goal,
        targetWeight = profile.targetWeight,
        dietType = profile.dietType,
        trainingType = profile.trainingType,
        calorieDistribution = profile.calorieDistribution,
        proteinPreference = profile.proteinPreference,
        estimatedTdee = profile.estimatedTdee,
        onboardingCompletedAt = profile.onboardingCompletedAt,
        theme = profile.theme,
        calorieLimit = profile.calorieLimit,
        proteinLimit = profile.proteinLimit,
        carbsLimit = profile.carbsLimit,
        fatLimit = profile.fatLimit
    )

    suspend fun updateTheme(userId: String, theme: String) {
        val profile = getProfile(userId) ?: return
        persistProfile(profile.copy(theme = theme, isSynced = false))
    }

    /**
     * Verifies [key] against the account's hashed recovery key. The key is stored hashed, so
     * this runs the same PBKDF2 comparison as login. Returns false when no key is set.
     */
    suspend fun verifyRecoveryKey(email: String, key: String): Boolean {
        val user = getUserByEmail(email) ?: return false
        if (user.recoveryKey.isBlank()) return false
        return withContext(Dispatchers.Default) {
            PasswordHasher.verify(key.trim(), user.recoveryKey)
        }
    }

    /**
     * Generates a new one-time recovery key for [userId], stores only its hash (locally and on
     * Supabase), and returns the raw key so the UI can reveal it exactly once. Replaces any
     * previous key.
     */
    suspend fun generateRecoveryKey(userId: String): String? {
        val user = userDao.getUserById(userId) ?: return null
        val raw = generateRawRecoveryKey()
        val hashed = hashPassword(raw)
        userDao.updateRecoveryKey(userId, hashed)
        supabaseRepository.upsertUser(user.toRemoteUser().copy(recoveryKey = hashed))
        return raw
    }

    /** True when the account already has a recovery key (hash) stored. */
    suspend fun hasRecoveryKey(userId: String): Boolean =
        userDao.getUserById(userId)?.recoveryKey?.isNotBlank() == true

    suspend fun getUserById(userId: String): User? = userDao.getUserById(userId)

    private fun generateRawRecoveryKey(): String {
        val alphabet = "ABCDEFGHJKMNPQRSTUVWXYZ23456789"
        val random = java.security.SecureRandom()
        return buildString {
            repeat(4) { group ->
                if (group > 0) append('-')
                repeat(5) {
                    append(alphabet[random.nextInt(alphabet.length)])
                }
            }
        }
    }

    /**
     * Sets a new password after a verified reset. Hashes the raw value and updates both the local
     * Room `users` row and the Supabase `users` mirror.
     *
     * Spending the recovery key is part of the reset, not an extra. It is documented as one-time,
     * and leaving it in place let anyone who had seen it once reset the password again at will.
     * Clearing rather than rotating means the next profile visit mints a fresh one and reveals it,
     * which is the flow the user already knows.
     */
    suspend fun resetPassword(email: String, newPassword: String): Boolean {
        val normalised = normaliseEmail(email)
        val user = userDao.getUserByEmail(normalised) ?: return false
        val hashed = hashPassword(newPassword)
        userDao.updatePasswordByEmail(normalised, hashed)
        userDao.updateRecoveryKey(user.userId, "")
        supabaseRepository.upsertUser(
            user.toRemoteUser().copy(email = normalised, password = hashed, recoveryKey = "")
        )
        return true
    }

    /**
     * Removes the account itself: credentials, profile and weight history.
     *
     * The user's food data is owned by [FoodRepository] and [MealPresetRepository], so deleting an
     * account means calling their removals too — see `ProfileViewModel.deleteAccount`, which
     * sequences all three. This used to be the whole of "delete account", which left every diary
     * entry, custom food and saved meal behind on the device and on the server.
     */
    suspend fun deleteAccount(userId: String) {
        // Local cleanup
        userDao.deleteUser(userId)
        userProfileDao.deleteProfile(userId)
        weightDao.deleteWeightRecords(userId)
        // Remote cleanup
        supabaseRepository.deleteProfile(userId)
        supabaseRepository.deleteUser(userId)
    }

    /** ISO creation date, or null for accounts that predate the column. */
    suspend fun getAccountCreated(userId: String): String? =
        try { userDao.getUserById(userId)?.createdAt } catch (e: Exception) { null }

    suspend fun getProfile(userId: String): UserProfile? {
        val local = userProfileDao.getProfileByUserId(userId)
        if (local?.isSynced != false) {
            // Only pull remote when local is clean; otherwise we would overwrite offline edits.
            syncProfileWithRemote(userId)
        }
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
        //    creating a new one. Mark unsynced so [flushOutbox] can push it later.
        val existing = weightDao.getWeightForDay(userId, date)
        val record = WeightRecord(
            id = existing?.id ?: 0,
            userId = userId,
            date = date,
            weight = weight,
            isSynced = false
        )
        val rowId = weightDao.insertOrUpdateWeight(record)
        val savedId = if (rowId == -1L) record.id else rowId.toInt()

        // 2. Try to upsert to the remote `weight` table (keyed by userId + date).
        if (connectivity.isOnline()) {
            try {
                supabaseRepository.saveWeightRecord(RemoteWeightRecord(userId = userId, weight = weight, date = date))
                weightDao.markSynced(savedId)
            } catch (e: Exception) {
                // Leave unsynced; the outbox will retry once the connection is back.
            }
        }

        // 3. Keep the profile's "current" weight in sync so the profile page always shows
        //    the latest updated weight.
        updateProfileWeight(userId, weight)
    }

    private suspend fun updateProfileWeight(userId: String, weight: Float) {
        val profile = userProfileDao.getProfileByUserId(userId)
        if (profile != null && profile.weight != weight) {
            persistProfile(profile.copy(weight = weight, isSynced = false))
        }
    }

    suspend fun getWeightHistory(userId: String): List<WeightRecord> {
        // Sync remote records into the local cache first so the graph reflects weight
        // entries made on any screen or device. Never overwrite local unsynced changes,
        // because the user's latest edit is the source of truth until it reaches the server.
        try {
            val remote = supabaseRepository.fetchWeightRecords(userId)
            for (r in remote) {
                val existing = weightDao.getWeightForDay(userId, r.date)
                if (existing?.isSynced == false) continue
                weightDao.insertOrUpdateWeight(
                    WeightRecord(
                        id = existing?.id ?: 0,
                        userId = userId,
                        date = r.date,
                        weight = r.weight,
                        isSynced = true
                    )
                )
            }
        } catch (e: Exception) {
            // Offline: fall back to local cache
        }
        return weightDao.getAllWeightRecords(userId)
    }

    /**
     * Drains the weight and profile outbox. Called at app start so offline edits reach Supabase
     * as soon as a connection is available, without waiting for the screen that made them to
     * reopen.
     */
    suspend fun flushOutbox() {
        if (!connectivity.isOnline()) return
        pushUnsyncedWeight()
        pushUnsyncedProfile()
    }

    private suspend fun pushUnsyncedWeight() {
        val pending = try { weightDao.getUnsynced() } catch (e: Exception) { return }
        for (record in pending) {
            try {
                supabaseRepository.saveWeightRecord(
                    RemoteWeightRecord(userId = record.userId, weight = record.weight, date = record.date)
                )
                weightDao.markSynced(record.id)
            } catch (e: Exception) {
                // Stop on the first failure; the rest will retry next time.
                break
            }
        }
    }

    private suspend fun pushUnsyncedProfile() {
        val pending = try { userProfileDao.getUnsynced() } catch (e: Exception) { return }
        for (profile in pending) {
            try {
                supabaseRepository.upsertProfile(toRemoteProfile(profile))
                userProfileDao.markSynced(profile.userId)
            } catch (e: Exception) {
                break
            }
        }
    }
}
