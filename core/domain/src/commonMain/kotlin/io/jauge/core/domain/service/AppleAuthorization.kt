package io.jauge.core.domain.service

import io.jauge.core.domain.model.Outcome

/**
 * Asks Apple for a fresh Sign in with Apple authorization code, at the moment the user deletes the account. The server
 * exchanges it for a token and revokes it (App Store guideline 5.1.1(v)).
 *
 * iOS shows the native Apple sheet (`ASAuthorizationAppleIDProvider`, client `io.jauge.app`, the client the server
 * uses). Other platforms cannot give a code for that client and answer `Success(null)`: the deletion goes on without
 * it, as the API allows.
 */
fun interface AppleAuthorization {
    /**
     * Requests the code.
     *
     * @return the code, null when the platform cannot give one, or a failure:
     *   [io.jauge.core.domain.error.JaugeError.SignInCancelled] when the user closed the Apple sheet.
     */
    suspend fun requestAuthorizationCode(): Outcome<String?>
}

/** [AppleAuthorization] of platforms without Sign in with Apple for the app client: no code. */
object NoAppleAuthorization : AppleAuthorization {
    override suspend fun requestAuthorizationCode(): Outcome<String?> = Outcome.Success(null)
}
