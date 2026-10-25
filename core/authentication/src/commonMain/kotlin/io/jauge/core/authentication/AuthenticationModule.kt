package io.jauge.core.authentication

import io.jauge.core.common.configuration.AppEnvironment
import io.jauge.core.domain.repository.AuthenticationRepository
import io.jauge.core.domain.service.AppleAuthorization
import io.jauge.core.domain.service.NoAppleAuthorization
import io.jauge.core.network.AccessTokenProvider
import io.jauge.core.network.NoAccessTokenProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.koin.dsl.module

/**
 * Koin module of `core:authentication`: the encrypted [SecureStore], the Supabase client, the
 * [AuthenticationRepository] (Supabase Auth when the project is configured; otherwise
 * [SimulatedAuthenticationRepository] in debug builds and [UnavailableAuthenticationRepository] in release builds) and
 * the [AccessTokenProvider] of the API, and the [AppleAuthorization] asked again when the account is deleted (native
 * Apple sheet on iOS, none on Android and without Supabase). Load it after `networkModule`: its
 * [AccessTokenProvider] replaces the one without token.
 */
val authenticationModule = module {
    includes(platformSecureStoreModule())
    single { SupabaseClientProvider(get()) { get<SecureStore>() } }
    single<AuthenticationRepository> {
        val client = get<SupabaseClientProvider>().client
        when {
            client != null -> SupabaseAuthenticationRepository(client, CoroutineScope(SupervisorJob() + Dispatchers.Default))
            get<AppEnvironment>().canUseSimulatedServices -> SimulatedAuthenticationRepository()
            else -> UnavailableAuthenticationRepository()
        }
    }
    single<AppleAuthorization> {
        if (get<SupabaseClientProvider>().client != null) platformAppleAuthorization() else NoAppleAuthorization
    }
    single<AccessTokenProvider> {
        get<SupabaseClientProvider>().client?.let { SupabaseAccessTokenProvider(it) } ?: NoAccessTokenProvider
    }
}

/** Sign in with Apple code for the account deletion: the native Apple sheet on iOS, none on Android. */
internal expect fun platformAppleAuthorization(): AppleAuthorization
