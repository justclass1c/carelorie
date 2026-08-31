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

    suspend fun fetchUserByEmail(email: String): RemoteUser? = withContext(Dispatchers.IO) {
        try {
            supabase.postgrest["users"]
                .select {
                    filter {
                        eq("email", email)
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
}
