package io.jauge.core.authentication

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.Apple
import io.github.jan.supabase.auth.providers.Google
import io.github.jan.supabase.auth.providers.builtin.OTP
import io.github.jan.supabase.auth.status.SessionStatus
import io.github.jan.supabase.auth.user.UserInfo
import io.jauge.core.domain.error.JaugeError
import io.jauge.core.domain.model.AuthenticatedUser
import io.jauge.core.domain.model.AuthenticationProvider
import io.jauge.core.domain.model.AuthenticationState
import io.jauge.core.domain.model.Outcome
import io.jauge.core.domain.model.SignInMethod
import io.jauge.core.domain.repository.AuthenticationRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/**
 * [AuthenticationRepository] backed by Supabase Auth (supabase-kt). Apple and Google open the provider page in the
 * browser; the email link is a Supabase magic link. Both come back through the deep link
 * `io.jauge.app://login-callback`, handled by [completeSignIn] with the PKCE flow (the code is exchanged for a session
 * on this device). The session is stored and refreshed by supabase-kt.
 *
 * The email link request carries the language of the device in the user metadata (`locale`, see [EmailLanguage]) so
 * that the email template writes in French or English. Supabase only saves this metadata when the account is created,
 * so after each sign in the language of the account is brought up to date (best effort) for the next emails.
 *
 * @param client the Supabase client.
 * @param scope long lived scope used to share the authentication state.
 * @param languageTag language tag of the device, read at each request (the device language may change).
 */
class SupabaseAuthenticationRepository(
    private val client: SupabaseClient,
    scope: CoroutineScope,
    private val languageTag: () -> String? = ::platformLanguageTag,
) : AuthenticationRepository {

    override val state: StateFlow<AuthenticationState> = client.auth.sessionStatus
        .map { status -> status.toAuthenticationState() }
        .stateIn(scope, SharingStarted.Eagerly, AuthenticationState.Loading)

    override suspend fun signInWith(provider: AuthenticationProvider): Outcome<Unit> =
        supabaseOutcome(fallback = JaugeError.SignInFailed) {
            when (provider) {
                AuthenticationProvider.APPLE -> client.auth.signInWith(Apple, redirectUrl = AuthenticationLinks.REDIRECT_URL)
                AuthenticationProvider.GOOGLE -> client.auth.signInWith(Google, redirectUrl = AuthenticationLinks.REDIRECT_URL)
            }
        }

    override suspend fun sendMagicLink(email: String): Outcome<Unit> = supabaseOutcome(fallback = JaugeError.SignInFailed) {
        client.auth.signInWith(OTP, redirectUrl = AuthenticationLinks.REDIRECT_URL) {
            this.email = email
            createUser = true
            data = EmailLanguage.userData(emailLanguage())
        }
    }

    override suspend fun completeSignIn(url: String): Outcome<Boolean> {
        if (!AuthenticationLinks.isSignInLink(url)) return Outcome.Success(false)
        return when (val callback = AuthenticationCallback.parse(url)) {
            is AuthenticationCallback.Code -> supabaseOutcome(fallback = JaugeError.SignInLinkExpired) {
                client.auth.exchangeCodeForSession(callback.code)
                synchronizeEmailLanguage()
                true
            }
            is AuthenticationCallback.Failure -> Outcome.Failure(callback.error)
            AuthenticationCallback.Unknown -> Outcome.Failure(JaugeError.SignInFailed)
        }
    }

    override suspend fun signOut(): Outcome<Unit> = supabaseOutcome { client.auth.signOut() }

    private fun emailLanguage(): String = EmailLanguage.fromLanguageTag(languageTag())

    /**
     * Saves the language of the device in the metadata of the signed in user when it differs, so that the next sign in
     * emails use it. A failure is ignored: the sign in itself has succeeded.
     */
    private suspend fun synchronizeEmailLanguage() {
        val language = emailLanguage()
        val user = client.auth.currentUserOrNull() ?: return
        val saved = (user.userMetadata?.get(EmailLanguage.METADATA_KEY) as? JsonPrimitive)?.contentOrNull
        if (saved == language) return
        try {
            client.auth.updateUser { data = EmailLanguage.userData(language) }
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Exception) {
            // The language is only used by the emails: keep the session.
        }
    }
}

/**
 * Reads the provider of a Supabase user.
 *
 * @receiver the Supabase user.
 */
internal fun UserInfo.toAuthenticatedUser(): AuthenticatedUser {
    val method = when (appMetadata?.get("provider")?.let { (it as? JsonPrimitive)?.contentOrNull }) {
        "apple" -> SignInMethod.APPLE
        "google" -> SignInMethod.GOOGLE
        else -> SignInMethod.EMAIL
    }
    val name = listOf("full_name", "name")
        .firstNotNullOfOrNull { key -> (userMetadata?.get(key) as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() } }
    return AuthenticatedUser(id = id, email = email, displayName = name, signInMethod = method)
}

private fun SessionStatus.toAuthenticationState(): AuthenticationState = when (this) {
    is SessionStatus.Authenticated -> session.user?.let { AuthenticationState.SignedIn(it.toAuthenticatedUser()) }
        ?: AuthenticationState.Loading
    is SessionStatus.NotAuthenticated -> AuthenticationState.SignedOut
    SessionStatus.Initializing -> AuthenticationState.Loading
    is SessionStatus.RefreshFailure -> AuthenticationState.Loading
}
