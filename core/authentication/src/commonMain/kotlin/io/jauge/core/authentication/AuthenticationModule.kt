package io.jauge.core.authentication

import io.jauge.core.domain.repository.AuthenticationRepository
import io.jauge.core.network.AccessTokenProvider
import io.jauge.core.network.NoAccessTokenProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.koin.dsl.module

/**
 * Koin module of `core:authentication`: the Supabase client, the [AuthenticationRepository] (Supabase Auth when the
 * project is configured, [SimulatedAuthenticationRepository] otherwise) and the [AccessTokenProvider] of the API.
 * Load it after `networkModule`: its [AccessTokenProvider] replaces the one without token.
 */
val authenticationModule = module {
    single { SupabaseClientProvider(get()) }
    single<AuthenticationRepository> {
        val client = get<SupabaseClientProvider>().client
        if (client != null) {
            SupabaseAuthenticationRepository(client, CoroutineScope(SupervisorJob() + Dispatchers.Default))
        } else {
            SimulatedAuthenticationRepository()
        }
    }
    single<AccessTokenProvider> {
        get<SupabaseClientProvider>().client?.let { SupabaseAccessTokenProvider(it) } ?: NoAccessTokenProvider
    }
}
