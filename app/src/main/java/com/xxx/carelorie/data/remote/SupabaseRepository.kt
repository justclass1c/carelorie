package com.xxx.carelorie.data.remote

import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class SupabaseRepository {
    
    suspend fun saveDailyMacros(intake: RemoteMacroIntake) = withContext(Dispatchers.IO) {
        supabase.postgrest["macros"].insert(intake)
    }

    suspend fun fetchWeeklyMacros(userId: Int): List<RemoteMacroIntake> = withContext(Dispatchers.IO) {
        supabase.postgrest["macros"]
            .select {
                filter {
                    eq("userId", userId)
                }
            }
            .decodeList<RemoteMacroIntake>()
    }

    suspend fun addFoodLog(entry: RemoteFoodLog) = withContext(Dispatchers.IO) {
        supabase.postgrest["food_logs"].insert(entry)
    }
}
