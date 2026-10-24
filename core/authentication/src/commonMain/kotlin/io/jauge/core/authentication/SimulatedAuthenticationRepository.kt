package io.jauge.core.authentication

import io.jauge.core.domain.model.AuthenticatedUser
import io.jauge.core.domain.model.AuthenticationProvider
import io.jauge.core.domain.model.AuthenticationState
import io.jauge.core.domain.model.Outcome
import io.jauge.core.domain.model.SignInMethod
import io.jauge.core.domain.repository.AuthenticationRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * In memory authentication used when Supabase is not configured: every sign in succeeds at once with the user of
 * the mockups, so that the account screens can be built and tested without a backend. Nothing leaves the phone.
 *
 * @param latencyMilliseconds simulated delay of each call.
 */
class SimulatedAuthenticationRepository(
    private val latencyMilliseconds: Long = 500,
) : AuthenticationRepository {

    private val mutableState = MutableStateFlow<AuthenticationState>(AuthenticationState.SignedOut)

    override val state: StateFlow<AuthenticationState> = mutableState.asStateFlow()

    override suspend fun signInWith(provider: AuthenticationProvider): Outcome<Unit> {
        delay(latencyMilliseconds)
        val method = when (provider) {
            AuthenticationProvider.APPLE -> SignInMethod.APPLE
            AuthenticationProvider.GOOGLE -> SignInMethod.GOOGLE
        }
        mutableState.value = AuthenticationState.SignedIn(sampleUser(method, SAMPLE_EMAIL))
        return Outcome.Success(Unit)
    }

    override suspend fun sendMagicLink(email: String): Outcome<Unit> {
        delay(latencyMilliseconds)
        pendingEmail = email
        return Outcome.Success(Unit)
    }

    override suspend fun completeSignIn(url: String): Outcome<Boolean> {
        if (!AuthenticationLinks.isSignInLink(url)) return Outcome.Success(false)
        delay(latencyMilliseconds)
        mutableState.value = AuthenticationState.SignedIn(sampleUser(SignInMethod.EMAIL, pendingEmail ?: SAMPLE_EMAIL))
        return Outcome.Success(true)
    }

    override suspend fun signOut(): Outcome<Unit> {
        delay(latencyMilliseconds)
        mutableState.value = AuthenticationState.SignedOut
        return Outcome.Success(Unit)
    }

    /** Email of the last magic link request, used when the simulated link is opened. */
    private var pendingEmail: String? = null

    private fun sampleUser(method: SignInMethod, email: String) = AuthenticatedUser(
        id = SAMPLE_USER_ID,
        email = email,
        displayName = SAMPLE_DISPLAY_NAME,
        signInMethod = method,
    )

    private companion object {
        const val SAMPLE_USER_ID = "00000000-0000-4000-8000-000000000001"
        const val SAMPLE_DISPLAY_NAME = "Inès Bertrand"
        const val SAMPLE_EMAIL = "ines.bertrand@exemple.fr"
    }
}
