package io.jauge.core.authentication

import io.jauge.core.domain.error.JaugeError
import io.jauge.core.domain.model.AuthenticationProvider
import io.jauge.core.domain.model.AuthenticationState
import io.jauge.core.domain.model.Outcome
import io.jauge.core.domain.repository.AuthenticationRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * [AuthenticationRepository] of a release build without Supabase configuration: nobody can sign in (the simulated
 * sign in of debug builds is never shipped), every attempt gives a friendly "service unavailable" error.
 */
class UnavailableAuthenticationRepository : AuthenticationRepository {

    override val state: StateFlow<AuthenticationState> = MutableStateFlow(AuthenticationState.SignedOut).asStateFlow()

    override suspend fun signInWith(provider: AuthenticationProvider): Outcome<Unit> = Outcome.Failure(JaugeError.ServerUnavailable)

    override suspend fun sendMagicLink(email: String): Outcome<Unit> = Outcome.Failure(JaugeError.ServerUnavailable)

    override suspend fun completeSignIn(url: String): Outcome<Boolean> = Outcome.Success(false)

    override suspend fun signOut(): Outcome<Unit> = Outcome.Success(Unit)
}
