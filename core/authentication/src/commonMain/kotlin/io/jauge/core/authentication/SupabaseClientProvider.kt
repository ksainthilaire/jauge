package io.jauge.core.authentication

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.FlowType
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.logging.LogLevel
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.realtime.Realtime
import io.jauge.core.common.configuration.AppEnvironment

/**
 * Builds the single Supabase client of the app, with the project URL and the **publishable** key only (never a secret
 * or service role key). Null when Supabase is not configured: the app then uses the simulated implementations.
 *
 * Plugins: Auth (PKCE flow, deep link `io.jauge.app://login-callback`), Postgrest (devices, notifications) and
 * Realtime (calm alerts while the app is open). The session and the PKCE code verifier are kept in the encrypted
 * [SecureStore] of the platform (Android Keystore, iOS Keychain), and the logs of supabase-kt are off in release builds
 * so that no token or personal data reaches the system log.
 *
 * @param environment app environment holding the Supabase URL and publishable key.
 * @param secureStore encrypted store of the session, created only when the client is built.
 */
class SupabaseClientProvider(
    private val environment: AppEnvironment,
    private val secureStore: () -> SecureStore,
) {

    /** The client, created on first use; null when Supabase is not configured. */
    val client: SupabaseClient? by lazy {
        if (!environment.isSupabaseConfigured) {
            null
        } else {
            val store = secureStore()
            createSupabaseClient(supabaseUrl = environment.supabaseUrl, supabaseKey = environment.supabasePublishableKey) {
                defaultLogLevel = if (environment.isDebugBuild) LogLevel.INFO else LogLevel.NONE
                install(Auth) {
                    sessionManager = SecureSessionManager(store)
                    codeVerifierCache = SecureCodeVerifierCache(store)
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
