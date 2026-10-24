package io.jauge.core.authentication

import io.github.jan.supabase.auth.exception.AuthErrorCode
import io.github.jan.supabase.auth.exception.AuthRestException
import io.github.jan.supabase.exceptions.HttpRequestException
import io.github.jan.supabase.exceptions.RestException
import io.jauge.core.domain.error.JaugeError
import io.jauge.core.domain.model.Outcome
import io.ktor.client.plugins.HttpRequestTimeoutException
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.SerializationException

/**
 * Translates a Supabase failure into a [JaugeError]. Authentication codes become the sign in errors of the app;
 * network problems, timeouts and server errors become their friendly cases. Nothing technical reaches the screen.
 *
 * @param fallback error used for the failures that have no better translation.
 */
fun Throwable.toJaugeError(fallback: JaugeError = JaugeError.Unknown): JaugeError = when (this) {
    is AuthRestException -> when (errorCode) {
        AuthErrorCode.OtpExpired, AuthErrorCode.FlowStateExpired, AuthErrorCode.FlowStateNotFound, AuthErrorCode.BadCodeVerifier ->
            JaugeError.SignInLinkExpired
        AuthErrorCode.EmailAddressInvalid, AuthErrorCode.EmailAddressNotAuthorized -> JaugeError.InvalidEmail
        AuthErrorCode.OverEmailSendRateLimit, AuthErrorCode.OverRequestRateLimit -> JaugeError.TooManyRequests
        AuthErrorCode.SessionNotFound, AuthErrorCode.SessionExpired, AuthErrorCode.RefreshTokenNotFound -> JaugeError.SessionExpired
        else -> statusToJaugeError(statusCode, fallback = JaugeError.SignInFailed)
    }
    is RestException -> statusToJaugeError(statusCode, fallback)
    is HttpRequestTimeoutException -> JaugeError.Timeout
    is HttpRequestException -> JaugeError.NoConnection
    is SerializationException -> JaugeError.InvalidData
    else -> fallback
}

private fun statusToJaugeError(statusCode: Int, fallback: JaugeError): JaugeError = when (statusCode) {
    401, 403 -> JaugeError.SessionExpired
    408 -> JaugeError.Timeout
    429 -> JaugeError.TooManyRequests
    in 500..599 -> JaugeError.ServerUnavailable
    else -> fallback
}

/**
 * Runs a Supabase call and wraps its result in an [Outcome], translating every failure except coroutine cancellation.
 *
 * @param fallback error used for failures without a better translation.
 * @param block the call.
 */
suspend fun <T> supabaseOutcome(fallback: JaugeError = JaugeError.Unknown, block: suspend () -> T): Outcome<T> = try {
    Outcome.Success(block())
} catch (cancellation: CancellationException) {
    throw cancellation
} catch (failure: Exception) {
    Outcome.Failure(failure.toJaugeError(fallback))
}
