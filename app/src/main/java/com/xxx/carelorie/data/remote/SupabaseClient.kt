package com.xxx.carelorie.data.remote

import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest

object SupabaseConfig {
    const val URL = "https://rtbdfctagghngwqnazfa.supabase.co"
    const val ANON_KEY = "sb_publishable_3GhV5-PYUUBZ5QyHyrzxSw_F0OjUIba"
}

val supabase = createSupabaseClient(
    supabaseUrl = SupabaseConfig.URL,
    supabaseKey = SupabaseConfig.ANON_KEY
) {
    install(Postgrest)
}
