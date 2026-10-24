package io.jauge.core.authentication

import io.jauge.core.domain.error.JaugeError
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AuthenticationCallbackTest {

    @Test
    fun codeIsReadFromTheQuery() {
        assertEquals(
            AuthenticationCallback.Code("4f2a-91"),
            AuthenticationCallback.parse("io.jauge.app://login-callback?code=4f2a-91"),
        )
    }

    @Test
    fun expiredLinksAreRecognized() {
        assertEquals(
            AuthenticationCallback.Failure(JaugeError.SignInLinkExpired),
            AuthenticationCallback.parse(
                "io.jauge.app://login-callback#error=access_denied&error_code=otp_expired&error_description=Email+link+is+invalid+or+has+expired",
            ),
        )
    }

    @Test
    fun otherErrorsAndEmptyLinks() {
        assertEquals(
            AuthenticationCallback.Failure(JaugeError.SignInFailed),
            AuthenticationCallback.parse("io.jauge.app://login-callback?error=server_error&error_code=unexpected_failure"),
        )
        assertEquals(
            AuthenticationCallback.Failure(JaugeError.SignInCancelled),
            AuthenticationCallback.parse("io.jauge.app://login-callback?error=access_denied"),
        )
        assertEquals(AuthenticationCallback.Unknown, AuthenticationCallback.parse("io.jauge.app://login-callback"))
    }

    @Test
    fun onlyTheCallbackIsASignInLink() {
        assertTrue(AuthenticationLinks.isSignInLink("io.jauge.app://login-callback?code=1"))
        assertFalse(AuthenticationLinks.isSignInLink("https://jauge.io/a/42"))
        assertFalse(AuthenticationLinks.isSignInLink("io.jauge.app://login-callback.evil.example?code=1"))
        assertFalse(AuthenticationLinks.isSignInLink("io.jauge.app://login-callback@evil.example?code=1"))
        assertTrue(AuthenticationLinks.isSignInLink("io.jauge.app://login-callback#access_token=1"))
    }
}
