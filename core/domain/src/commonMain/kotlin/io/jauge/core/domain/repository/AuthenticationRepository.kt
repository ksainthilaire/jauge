package io.jauge.core.domain.repository

import io.jauge.core.domain.model.AuthenticationProvider
import io.jauge.core.domain.model.AuthenticationState
import io.jauge.core.domain.model.Outcome
import kotlinx.coroutines.flow.StateFlow

/**
 * Authentication of the user: Apple, Google and email link (magic link). Implemented with Supabase Auth in
 * `core:authentication`; a simulated implementation is used when Supabase is not configured.
 *
 * Signing in with a provider or with an email link finishes outside the app (browser, mail app): the result comes
 * back through [completeSignIn] (deep link) and is published in [state]. Implementations never throw.
 */
interface AuthenticationRepository {
    /** Current authentication state, updated on sign in, sign out, refresh and expiry. */
    val state: StateFlow<AuthenticationState>

    /**
     * Starts signing in with Apple or Google. The provider page opens; [state] becomes
     * [AuthenticationState.SignedIn] when the user comes back.
     *
     * @param provider the provider chosen on the account screen.
     */
    suspend fun signInWith(provider: AuthenticationProvider): Outcome<Unit>

    /**
     * Sends a sign in link to an email address. The account is created on first use.
     *
     * @param email address typed by the user, already trimmed.
     */
    suspend fun sendMagicLink(email: String): Outcome<Unit>

    /**
     * Finishes a sign in from the deep link opened by the browser or the mail app
     * (`io.jauge.app://login-callback...`).
     *
     * @param url the full deep link.
     * @return true when the link was a sign in link and was handled.
     */
    suspend fun completeSignIn(url: String): Outcome<Boolean>

    /** Signs out on this device. */
    suspend fun signOut(): Outcome<Unit>
}
