package io.jauge.core.domain.model

/**
 * The signed in user, as known by the authentication service (Supabase Auth).
 *
 * @property id stable identifier of the user (Supabase user id), also used as the RevenueCat app user id.
 * @property email email address, if the provider shares it (Apple can hide it).
 * @property displayName name given by the provider, if any.
 * @property signInMethod how the user signed in.
 */
data class AuthenticatedUser(
    val id: String,
    val email: String?,
    val displayName: String?,
    val signInMethod: SignInMethod,
)

/** Authentication state of the app, observed by the screens (mockup 11, profile 13). */
sealed interface AuthenticationState {
    /** The stored session is being read or refreshed at start up. */
    data object Loading : AuthenticationState

    /** Nobody is signed in ("Continuer sans compte"). */
    data object SignedOut : AuthenticationState

    /**
     * A user is signed in.
     *
     * @property user the signed in user.
     */
    data class SignedIn(val user: AuthenticatedUser) : AuthenticationState
}

/** Third party providers offered on the account screen (mockup 11). */
enum class AuthenticationProvider {
    /** Sign in with Apple. */
    APPLE,

    /** Sign in with Google. */
    GOOGLE,
}
