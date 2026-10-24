package io.jauge.core.authentication

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.jauge.core.network.AccessTokenProvider

/**
 * Sends the Supabase access token of the signed in user to the Jauge API (`Authorization: Bearer`). The token is
 * refreshed by supabase-kt before it expires.
 *
 * @param client the Supabase client.
 */
class SupabaseAccessTokenProvider(private val client: SupabaseClient) : AccessTokenProvider {
    override suspend fun accessToken(): String? = client.auth.currentAccessTokenOrNull()
}
