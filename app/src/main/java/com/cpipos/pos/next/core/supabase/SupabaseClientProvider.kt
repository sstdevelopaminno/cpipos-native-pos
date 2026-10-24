package com.cpipos.pos.next.core.supabase

import com.cpipos.pos.next.BuildConfig
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest

object SupabaseClientProvider {
    val isConfigured: Boolean
        get() = BuildConfig.SUPABASE_URL.isNotBlank() &&
            BuildConfig.SUPABASE_PUBLISHABLE_KEY.isNotBlank()

    fun create(): SupabaseClient {
        check(isConfigured) {
            "Supabase is not configured. Add CPIPOS_SUPABASE_URL and " +
                "CPIPOS_SUPABASE_PUBLISHABLE_KEY to local.properties."
        }

        return createSupabaseClient(
            supabaseUrl = BuildConfig.SUPABASE_URL,
            supabaseKey = BuildConfig.SUPABASE_PUBLISHABLE_KEY
        ) {
            install(Auth)
            install(Postgrest)
        }
    }
}
