package com.xxx.carelorie.data.remote

import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.serializer.KotlinXSerializer
import kotlinx.serialization.json.Json

object SupabaseConfig {
    const val URL = "https://rtbdfctagghngwqnazfa.supabase.co"
    const val ANON_KEY = "sb_publishable_3GhV5-PYUUBZ5QyHyrzxSw_F0OjUIba"
}

val supabase = createSupabaseClient(
    supabaseUrl = SupabaseConfig.URL,
    supabaseKey = SupabaseConfig.ANON_KEY
) {
    install(Postgrest)
    defaultSerializer = KotlinXSerializer(Json {
        encodeDefaults = true
        ignoreUnknownKeys = true
    })
}
