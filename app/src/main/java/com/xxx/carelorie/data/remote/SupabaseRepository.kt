package com.xxx.carelorie.data.remote

import android.util.Log
import io.github.jan.supabase.postgrest.exception.PostgrestRestException
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.filter.FilterOperator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class SupabaseRepository {

    suspend fun addFoodLog(entry: RemoteFoodLog): RemoteFoodLog? = withContext(Dispatchers.IO) {
        try {
            val response = supabase.postgrest["food_logs"]
                .insert(entry) {
                    select()
                }
            response.decodeSingle<RemoteFoodLog>()
        } catch (e: PostgrestRestException) {
            Log.e("SupabaseRepository", "Postgrest error adding food log: ${e.description} (Code: ${e.code})", e)
            null
        } catch (e: Exception) {
            Log.e("SupabaseRepository", "Error adding food log", e)
            null
        }
    }

    /** Pushes an edit to an entry that already exists on the server. */
    suspend fun updateFoodLog(entry: RemoteFoodLog): Boolean = withContext(Dispatchers.IO) {
        val id = entry.id ?: return@withContext false
        try {
            supabase.postgrest["food_logs"].update(entry) {
                filter { eq("id", id) }
            }
            true
        } catch (e: PostgrestRestException) {
            Log.e("SupabaseRepository", "Postgrest error updating food log: ${e.description} (Code: ${e.code})", e)
            false
        } catch (e: Exception) {
            Log.e("SupabaseRepository", "Error updating food log", e)
            false
        }
    }

    suspend fun deleteFoodLog(logId: Int): Boolean = withContext(Dispatchers.IO) {
        try {
            supabase.postgrest["food_logs"].delete {
                filter { eq("id", logId) }
            }
            true
        } catch (e: PostgrestRestException) {
            Log.e("SupabaseRepository", "Postgrest error deleting food log: ${e.description} (Code: ${e.code})", e)
            false
        } catch (e: Exception) {
            Log.e("SupabaseRepository", "Error deleting food log", e)
            false
        }
    }

    suspend fun fetchFoodLogsRange(userId: String, startDate: String): List<RemoteFoodLog> = withContext(Dispatchers.IO) {
        supabase.postgrest["food_logs"]
            .select {
                filter {
                    eq("userId", userId)
                    gte("createdAt", startDate)
                }
            }
            .decodeList<RemoteFoodLog>()
    }

    /**
     * Only the presets this user created. The built-in dishes are seeded into Room rather than
     * fetched, so they are available offline and no client writes to a table shared by everyone.
     */
    suspend fun fetchUserFoodPresets(userId: String): List<RemoteFoodPreset> = withContext(Dispatchers.IO) {
        supabase.postgrest["food_presets"]
            .select {
                filter { eq("userId", userId) }
            }
            .decodeList<RemoteFoodPreset>()
    }

    /** Creates a user's own preset and returns the stored row, so its id can be cached locally. */
    suspend fun insertFoodPreset(preset: RemoteFoodPreset): RemoteFoodPreset? = withContext(Dispatchers.IO) {
        try {
            supabase.postgrest["food_presets"]
                .insert(preset) { select() }
                .decodeSingle<RemoteFoodPreset>()
        } catch (e: PostgrestRestException) {
            Log.e("SupabaseRepository", "Postgrest error inserting food preset: ${e.description} (Code: ${e.code})", e)
            null
        } catch (e: Exception) {
            Log.e("SupabaseRepository", "Error inserting food preset", e)
            null
        }
    }

    suspend fun updateFoodPreset(preset: RemoteFoodPreset): Boolean = withContext(Dispatchers.IO) {
        val id = preset.id ?: return@withContext false
        try {
            supabase.postgrest["food_presets"].update(preset) {
                filter { eq("id", id) }
            }
            true
        } catch (e: PostgrestRestException) {
            Log.e("SupabaseRepository", "Postgrest error updating food preset: ${e.description} (Code: ${e.code})", e)
            false
        } catch (e: Exception) {
            Log.e("SupabaseRepository", "Error updating food preset", e)
            false
        }
    }

    suspend fun deleteFoodPreset(presetId: Int): Boolean = withContext(Dispatchers.IO) {
        try {
            supabase.postgrest["food_presets"].delete {
                filter { eq("id", presetId) }
            }
            true
        } catch (e: PostgrestRestException) {
            Log.e("SupabaseRepository", "Postgrest error deleting food preset: ${e.description} (Code: ${e.code})", e)
            false
        } catch (e: Exception) {
            Log.e("SupabaseRepository", "Error deleting food preset", e)
            false
        }
    }

    // --- Saved meals ---

    /**
     * Pushes a saved meal and its foods.
     *
     * The meal goes first so the items' foreign key has something to point at, and the old items
     * are cleared before the new ones land — a rename that drops a food has to remove it, not just
     * fail to mention it. Returns false on any failure so the row stays queued rather than being
     * marked synced when it is not.
     */
    suspend fun upsertMealPreset(
        meal: RemoteMealPreset,
        items: List<RemoteMealPresetItem>
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            supabase.postgrest["meal_presets"].upsert(meal) { onConflict = "localId" }
            supabase.postgrest["meal_preset_items"].delete {
                filter { eq("mealPresetId", meal.localId) }
            }
            if (items.isNotEmpty()) {
                supabase.postgrest["meal_preset_items"].upsert(items) { onConflict = "localId" }
            }
            true
        } catch (e: PostgrestRestException) {
            Log.e("SupabaseRepository", "Postgrest error upserting meal preset: ${e.description} (Code: ${e.code})", e)
            false
        } catch (e: Exception) {
            Log.e("SupabaseRepository", "Error upserting meal preset", e)
            false
        }
    }

    /** The items go with the meal: `meal_preset_items` cascades on delete. */
    suspend fun deleteMealPreset(localId: String): Boolean = withContext(Dispatchers.IO) {
        try {
            supabase.postgrest["meal_presets"].delete {
                filter { eq("localId", localId) }
            }
            true
        } catch (e: PostgrestRestException) {
            Log.e("SupabaseRepository", "Postgrest error deleting meal preset: ${e.description} (Code: ${e.code})", e)
            false
        } catch (e: Exception) {
            Log.e("SupabaseRepository", "Error deleting meal preset", e)
            false
        }
    }

    suspend fun fetchMealPresets(userId: String): List<RemoteMealPreset> = withContext(Dispatchers.IO) {
        supabase.postgrest["meal_presets"]
            .select { filter { eq("ownerUserId", userId) } }
            .decodeList<RemoteMealPreset>()
    }

    /**
     * The foods for [mealIds] in one request.
     *
     * Empty in, empty out — an `isIn` on an empty list is a request that can only return nothing.
     */
    suspend fun fetchMealPresetItems(mealIds: List<String>): List<RemoteMealPresetItem> =
        withContext(Dispatchers.IO) {
            if (mealIds.isEmpty()) return@withContext emptyList()
            supabase.postgrest["meal_preset_items"]
                .select { filter { isIn("mealPresetId", mealIds) } }
                .decodeList<RemoteMealPresetItem>()
        }

    /** Every saved meal belonging to a user. Part of deleting an account. */
    suspend fun deleteMealPresetsForUser(userId: String): Boolean = withContext(Dispatchers.IO) {
        try {
            supabase.postgrest["meal_presets"].delete {
                filter { eq("ownerUserId", userId) }
            }
            true
        } catch (e: Exception) {
            Log.e("SupabaseRepository", "Error deleting meal presets for user", e)
            false
        }
    }

    // --- User & Profile Sync ---

    suspend fun insertUser(user: RemoteUser): RemoteUser? = withContext(Dispatchers.IO) {
        try {
            val response = supabase.postgrest["users"]
                .insert(user) {
                    select()
                }
            response.decodeSingle<RemoteUser>()
        } catch (e: PostgrestRestException) {
            Log.e("SupabaseRepository", "Postgrest error inserting user: ${e.description} (Code: ${e.code})", e)
            null
        } catch (e: Exception) {
            Log.e("SupabaseRepository", "Error inserting user", e)
            null
        }
    }

    /**
     * Looks an account up by email, ignoring case.
     *
     * `eq` is case-sensitive in Postgres, so an account registered as `Foo@Bar.com` was invisible
     * to someone typing `foo@bar.com` on another device. `ilike` matches either way; the pattern
     * is escaped first because `%` and `_` are wildcards to it and underscores are ordinary
     * characters in an email address.
     */
    suspend fun fetchUserByEmail(email: String): RemoteUser? = withContext(Dispatchers.IO) {
        try {
            supabase.postgrest["users"]
                .select {
                    filter {
                        ilike("email", escapeLikePattern(email))
                    }
                }
                .decodeSingleOrNull<RemoteUser>()
        } catch (e: PostgrestRestException) {
            Log.e("SupabaseRepository", "Postgrest error fetching user by email: ${e.description} (Code: ${e.code})", e)
            null
        } catch (e: Exception) {
            Log.e("SupabaseRepository", "Error fetching user by email", e)
            null
        }
    }

    suspend fun upsertProfile(profile: RemoteUserProfile) = withContext(Dispatchers.IO) {
        try {
            supabase.postgrest["profiles"].upsert(profile)
        } catch (e: PostgrestRestException) {
            Log.e("SupabaseRepository", "Postgrest error upserting profile: ${e.description} (Code: ${e.code})", e)
        } catch (e: Exception) {
            Log.e("SupabaseRepository", "Error upserting profile", e)
        }
    }

    suspend fun deleteProfile(userId: String): Boolean = withContext(Dispatchers.IO) {
        try {
            supabase.postgrest["profiles"].delete {
                filter { eq("userId", userId) }
            }
            true
        } catch (e: Exception) {
            Log.e("SupabaseRepository", "Error deleting profile", e)
            false
        }
    }

    suspend fun deleteUser(userId: String): Boolean = withContext(Dispatchers.IO) {
        try {
            supabase.postgrest["users"].delete {
                filter { eq("userId", userId) }
            }
            true
        } catch (e: Exception) {
            Log.e("SupabaseRepository", "Error deleting user", e)
            false
        }
    }

    /** Removes every diary entry for a user. Part of deleting an account. */
    suspend fun deleteFoodLogsForUser(userId: String): Boolean = withContext(Dispatchers.IO) {
        try {
            supabase.postgrest["food_logs"].delete {
                filter { eq("userId", userId) }
            }
            true
        } catch (e: Exception) {
            Log.e("SupabaseRepository", "Error deleting food logs for user", e)
            false
        }
    }

    /** Removes every custom food a user created. Built-ins carry a null userId and are untouched. */
    suspend fun deleteFoodPresetsForUser(userId: String): Boolean = withContext(Dispatchers.IO) {
        try {
            supabase.postgrest["food_presets"].delete {
                filter { eq("userId", userId) }
            }
            true
        } catch (e: Exception) {
            Log.e("SupabaseRepository", "Error deleting food presets for user", e)
            false
        }
    }

    /**
     * Escapes the LIKE wildcards in a literal value so `ilike` matches it exactly.
     *
     * Backslash first, or it would escape the escapes added after it.
     */
    private fun escapeLikePattern(value: String): String = value
        .replace("\\", "\\\\")
        .replace("%", "\\%")
        .replace("_", "\\_")

    suspend fun fetchProfile(userId: String): RemoteUserProfile? = withContext(Dispatchers.IO) {
        try {
            supabase.postgrest["profiles"]
                .select {
                    filter {
                        eq("userId", userId)
                    }
                }
                .decodeSingleOrNull<RemoteUserProfile>()
        } catch (e: PostgrestRestException) {
            Log.e("SupabaseRepository", "Postgrest error fetching profile: ${e.description} (Code: ${e.code})", e)
            null
        } catch (e: Exception) {
            Log.e("SupabaseRepository", "Error fetching profile", e)
            null
        }
    }

    suspend fun saveWeightRecord(record: RemoteWeightRecord) = withContext(Dispatchers.IO) {
        try {
            // Upsert on (userId, date) so updating weight multiple times in the same day
            // modifies the existing row instead of creating duplicates.
            supabase.postgrest["weight"].upsert(record) {
                onConflict = "userId,date"
            }
        } catch (e: Exception) {
            Log.e("SupabaseRepository", "Error saving weight record", e)
        }
    }

    suspend fun fetchWeightRecords(userId: String): List<RemoteWeightRecord> = withContext(Dispatchers.IO) {
        try {
            supabase.postgrest["weight"]
                .select {
                    filter { eq("userId", userId) }
                }
                .decodeList<RemoteWeightRecord>()
        } catch (e: Exception) {
            Log.e("SupabaseRepository", "Error fetching weight records", e)
            emptyList()
        }
    }

    // --- Password reset & recovery key ---

    /**
     * Upserts a whole user row, keyed by `userId`. Changing a password or recovery key goes
     * through here rather than `.update()` because the `users` table's anon policies permit
     * insert (and therefore upsert) but not update; `.update()` was silently failing.
     */
    suspend fun upsertUser(user: RemoteUser) = withContext(Dispatchers.IO) {
        try {
            supabase.postgrest["users"].upsert(user) {
                onConflict = "userId"
            }
        } catch (e: PostgrestRestException) {
            Log.e("SupabaseRepository", "Postgrest error upserting user: ${e.description} (Code: ${e.code})", e)
        } catch (e: Exception) {
            Log.e("SupabaseRepository", "Error upserting user", e)
        }
    }
}
