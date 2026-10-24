package io.jauge.core.authentication

import io.jauge.core.domain.model.AuthenticationProvider
import io.jauge.core.domain.model.AuthenticationState
import io.jauge.core.domain.model.Outcome
import io.jauge.core.domain.model.SignInMethod
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class SimulatedAuthenticationRepositoryTest {

    @Test
    fun providerSignInAndSignOut() = runTest {
        val repository = SimulatedAuthenticationRepository(latencyMilliseconds = 0)
        assertEquals(AuthenticationState.SignedOut, repository.state.value)
        repository.signInWith(AuthenticationProvider.APPLE)
        val signedIn = assertIs<AuthenticationState.SignedIn>(repository.state.value)
        assertEquals(SignInMethod.APPLE, signedIn.user.signInMethod)
        repository.signOut()
        assertEquals(AuthenticationState.SignedOut, repository.state.value)
    }

    @Test
    fun magicLinkSignsInOnlyWithTheCallbackLink() = runTest {
        val repository = SimulatedAuthenticationRepository(latencyMilliseconds = 0)
        repository.sendMagicLink("ines@exemple.fr")
        assertEquals(Outcome.Success(false), repository.completeSignIn("https://jauge.io/a/42"))
        assertEquals(AuthenticationState.SignedOut, repository.state.value)
        assertEquals(Outcome.Success(true), repository.completeSignIn("${AuthenticationLinks.REDIRECT_URL}#access_token=x"))
        val signedIn = assertIs<AuthenticationState.SignedIn>(repository.state.value)
        assertEquals("ines@exemple.fr", signedIn.user.email)
        assertEquals(SignInMethod.EMAIL, signedIn.user.signInMethod)
    }
}
