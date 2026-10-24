package io.jauge.core.data.error

import io.jauge.core.billing.BillingFailure
import io.jauge.core.common.error.StorageFullException
import io.jauge.core.domain.error.JaugeError
import io.jauge.core.domain.model.Outcome
import io.jauge.core.network.NetworkFailure
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.SerializationException

/** Machine readable error codes of the API (`docs/api.md`, section "Errors"). */
object ApiErrorCodes {
    /** Free analysis of the week used. */
    const val QUOTA_REACHED = "quota_reached"

    /** Screenshot or photo unreadable. */
    const val UNREADABLE_OFFER = "unreadable_offer"

    /** The text is not a link. */
    const val INVALID_LINK = "invalid_link"

    /** The link is not a readable job offer. */
    const val UNSUPPORTED_LINK = "unsupported_link"

    /** Image larger than the limit. */
    const val IMAGE_TOO_LARGE = "image_too_large"

    /** Invalid request body. */
    const val VALIDATION_FAILED = "validation_failed"

    /** Session expired. */
    const val UNAUTHORIZED = "unauthorized"

    /** Item not found. */
    const val NOT_FOUND = "not_found"

    /** Too many requests. */
    const val RATE_LIMITED = "rate_limited"

    /** Planned maintenance. */
    const val MAINTENANCE = "maintenance"
}

/**
 * Translates an error code of the API into a domain error, or null when the code is unknown.
 *
 * @param code machine readable code.
 * @param recognizedCompany company still readable, for unreadable offers.
 */
fun apiErrorCodeToJaugeError(code: String?, recognizedCompany: String? = null): JaugeError? = when (code) {
    ApiErrorCodes.QUOTA_REACHED -> JaugeError.QuotaReached
    ApiErrorCodes.UNREADABLE_OFFER -> JaugeError.UnreadableOffer(recognizedCompany)
    ApiErrorCodes.INVALID_LINK -> JaugeError.InvalidLink
    ApiErrorCodes.UNSUPPORTED_LINK -> JaugeError.UnsupportedLink
    ApiErrorCodes.IMAGE_TOO_LARGE -> JaugeError.ImageTooLarge
    ApiErrorCodes.VALIDATION_FAILED -> JaugeError.InvalidData
    ApiErrorCodes.UNAUTHORIZED -> JaugeError.SessionExpired
    ApiErrorCodes.NOT_FOUND -> JaugeError.NotFound
    ApiErrorCodes.RATE_LIMITED -> JaugeError.TooManyRequests
    ApiErrorCodes.MAINTENANCE -> JaugeError.ServerUnavailable
    else -> null
}

/**
 * Translates any failure into a [JaugeError]: network failures, HTTP statuses and API codes, store failures, full
 * storage, invalid data. Unknown failures become [JaugeError.Unknown]; nothing technical reaches the screen.
 */
fun Throwable.toJaugeError(): JaugeError = when (this) {
    is NetworkFailure.NoConnection -> JaugeError.NoConnection
    is NetworkFailure.Timeout -> JaugeError.Timeout
    is NetworkFailure.InvalidResponse -> JaugeError.InvalidData
    is NetworkFailure.Unexpected -> JaugeError.Unknown
    is NetworkFailure.Http -> apiErrorCodeToJaugeError(errorCode) ?: httpStatusToJaugeError(statusCode)
    is BillingFailure.Cancelled -> JaugeError.PurchaseCancelled
    is BillingFailure.Unavailable -> JaugeError.PurchaseUnavailable
    is BillingFailure.Pending -> JaugeError.PurchasePending
    is BillingFailure.Network -> JaugeError.NoConnection
    is BillingFailure.Store -> JaugeError.PurchaseUnavailable
    is StorageFullException -> JaugeError.StorageFull
    is SerializationException -> JaugeError.InvalidData
    is IllegalArgumentException -> JaugeError.InvalidData
    else -> JaugeError.Unknown
}

/**
 * Translates an HTTP status without a known error code.
 *
 * @param statusCode HTTP status code.
 */
fun httpStatusToJaugeError(statusCode: Int): JaugeError = when (statusCode) {
    401, 403 -> JaugeError.SessionExpired
    402 -> JaugeError.QuotaReached
    404, 410 -> JaugeError.NotFound
    408 -> JaugeError.Timeout
    413 -> JaugeError.ImageTooLarge
    400, 422 -> JaugeError.InvalidData
    429 -> JaugeError.TooManyRequests
    in 500..599 -> JaugeError.ServerUnavailable
    else -> JaugeError.Unknown
}

/**
 * Runs [block] and wraps its result in an [Outcome], translating every failure except coroutine cancellation.
 *
 * @param block the operation.
 */
suspend fun <T> outcomeOf(block: suspend () -> T): Outcome<T> = try {
    Outcome.Success(block())
} catch (cancellation: CancellationException) {
    throw cancellation
} catch (failure: Exception) {
    Outcome.Failure(failure.toJaugeError())
}
