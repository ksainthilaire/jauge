package io.jauge.core.authentication

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.FlowType
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.realtime.Realtime
import io.jauge.core.common.configuration.AppEnvironment

/**
 * Builds the single Supabase client of the app, with the project URL and the **publishable** key only (never a secret
 * or service role key). Null when Supabase is not configured: the app then uses the simulated implementations.
 *
 * Plugins: Auth (PKCE flow, deep link `io.jauge.app://login-callback`), Postgrest (devices, notifications) and
 * Realtime (calm alerts while the app is open).
 *
 * @param environment app environment holding the Supabase URL and publishable key.
 */
class SupabaseClientProvider(private val environment: AppEnvironment) {

    /** The client, created on first use; null when Supabase is not configured. */
    val client: SupabaseClient? by lazy {
        if (!environment.isSupabaseConfigured) {
            null
        } else {
            createSupabaseClient(supabaseUrl = environment.supabaseUrl, supabaseKey = environment.supabasePublishableKey) {
                install(Auth) {
                    flowType = FlowType.PKCE
                    scheme = AuthenticationLinks.SCHEME
                    host = AuthenticationLinks.HOST
                }
                install(Postgrest)
                install(Realtime)
            }
        }
    }
}
