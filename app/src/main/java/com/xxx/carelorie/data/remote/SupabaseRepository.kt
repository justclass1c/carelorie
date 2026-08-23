package com.xxx.carelorie.data.remote

import android.util.Log
import io.github.jan.supabase.postgrest.exception.PostgrestRestException
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.filter.FilterOperator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class SupabaseRepository {
    
    suspend fun saveDailyMacros(intake: RemoteMacroIntake) = withContext(Dispatchers.IO) {
        try {
            supabase.postgrest["macros"].insert(intake)
        } catch (e: PostgrestRestException) {
            Log.e("SupabaseRepository", "Postgrest error saving daily macros: ${e.description} (Code: ${e.code})", e)
        } catch (e: Exception) {
            Log.e("SupabaseRepository", "Error saving daily macros", e)
        }
    }

    suspend fun fetchWeeklyMacros(userId: Int): List<RemoteMacroIntake> = withContext(Dispatchers.IO) {
        try {
            supabase.postgrest["macros"]
                .select {
                    filter {
                        eq("userId", userId)
                    }
                }
                .decodeList<RemoteMacroIntake>()
        } catch (e: PostgrestRestException) {
            Log.e("SupabaseRepository", "Postgrest error fetching weekly macros: ${e.description} (Code: ${e.code})", e)
            emptyList()
        } catch (e: Exception) {
            Log.e("SupabaseRepository", "Error fetching weekly macros", e)
            emptyList()
        }
    }

    suspend fun addFoodLog(entry: RemoteFoodLog): Boolean = withContext(Dispatchers.IO) {
        try {
            supabase.postgrest["food_logs"].insert(entry)
            true
        } catch (e: PostgrestRestException) {
            Log.e("SupabaseRepository", "Postgrest error adding food log: ${e.description} (Code: ${e.code})", e)
            false
        } catch (e: Exception) {
            Log.e("SupabaseRepository", "Error adding food log", e)
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

    suspend fun fetchFoodLogs(userId: Int, date: String): List<RemoteFoodLog> = withContext(Dispatchers.IO) {
        try {
            supabase.postgrest["food_logs"]
                .select {
                    filter {
                        eq("userId", userId)
                        filter("createdAt", FilterOperator.ILIKE, "$date%")
                    }
                }
                .decodeList<RemoteFoodLog>()
        } catch (e: PostgrestRestException) {
            Log.e("SupabaseRepository", "Postgrest error fetching food logs: ${e.description} (Code: ${e.code})", e)
            emptyList()
        } catch (e: Exception) {
            Log.e("SupabaseRepository", "Error fetching food logs", e)
            emptyList()
        }
    }

    suspend fun fetchFoodLogsRange(userId: Int, startDate: String): List<RemoteFoodLog> = withContext(Dispatchers.IO) {
        try {
            supabase.postgrest["food_logs"]
                .select {
                    filter {
                        eq("userId", userId)
                        gte("createdAt", startDate)
                    }
                }
                .decodeList<RemoteFoodLog>()
        } catch (e: PostgrestRestException) {
            Log.e("SupabaseRepository", "Postgrest error fetching food logs range: ${e.description} (Code: ${e.code})", e)
            emptyList()
        } catch (e: Exception) {
            Log.e("SupabaseRepository", "Error fetching food logs range", e)
            emptyList()
        }
    }

    suspend fun fetchFoodPresets(userId: Int): List<RemoteFoodPreset> = withContext(Dispatchers.IO) {
        try {
            // Fetch user presets and system presets separately to avoid complex null-filter syntax issues
            val userPresets = try {
                supabase.postgrest["food_presets"]
                    .select {
                        filter {
                            eq("userId", userId)
                        }
                    }
                    .decodeList<RemoteFoodPreset>()
            } catch (e: Exception) {
                Log.e("SupabaseRepository", "Error fetching user presets", e)
                emptyList()
            }
                
            val systemPresets = try {
                supabase.postgrest["food_presets"]
                    .select {
                        filter {
                            filter("userId", FilterOperator.IS, "null")
                        }
                    }
                    .decodeList<RemoteFoodPreset>()
            } catch (e: Exception) {
                Log.e("SupabaseRepository", "Error fetching system presets", e)
                emptyList()
            }
                
            userPresets + systemPresets
        } catch (e: Exception) {
            Log.e("SupabaseRepository", "Critical error in fetchFoodPresets", e)
            emptyList()
        }
    }

    suspend fun seedFoodPresets(presets: List<RemoteFoodPreset>) = withContext(Dispatchers.IO) {
        try {
            supabase.postgrest["food_presets"].insert(presets)
        } catch (e: PostgrestRestException) {
            Log.e("SupabaseRepository", "Postgrest error seeding food presets: ${e.description} (Code: ${e.code})", e)
        } catch (e: Exception) {
            Log.e("SupabaseRepository", "Error seeding food presets", e)
        }
    }
}
