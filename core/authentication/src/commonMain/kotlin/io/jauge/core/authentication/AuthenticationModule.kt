package io.jauge.core.authentication

import io.jauge.core.domain.repository.AuthenticationRepository
import org.koin.dsl.module

/**
 * Koin module of `core:authentication`: the [AuthenticationRepository] of the app. Supabase Auth when the project is
 * configured, [SimulatedAuthenticationRepository] otherwise.
 */
val authenticationModule = module {
    single<AuthenticationRepository> { SimulatedAuthenticationRepository() }
}
