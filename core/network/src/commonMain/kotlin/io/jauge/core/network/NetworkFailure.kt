package io.jauge.core.network

/**
 * The only exception type thrown by [JaugeApi] implementations. The data layer maps it to a domain error.
 *
 * @param description developer description, never shown to the user.
 * @param cause the original exception, if any.
 */
sealed class NetworkFailure(description: String, cause: Throwable? = null) : Exception(description, cause) {
    /**
     * No network connection (or the host cannot be reached).
     *
     * @param cause the original exception.
     */
    class NoConnection(cause: Throwable? = null) : NetworkFailure("No connection", cause)

    /**
     * The request timed out.
     *
     * @param cause the original exception.
     */
    class Timeout(cause: Throwable? = null) : NetworkFailure("Timeout", cause)

    /**
     * The server answered with an error status.
     *
     * @property statusCode HTTP status code.
     * @property errorCode machine readable code of the error body, if any.
     */
    class Http(val statusCode: Int, val errorCode: String?) : NetworkFailure("HTTP $statusCode ${errorCode.orEmpty()}")

    /**
     * The answer could not be decoded.
     *
     * @param cause the original exception.
     */
    class InvalidResponse(cause: Throwable? = null) : NetworkFailure("Invalid response", cause)

    /**
     * Anything else.
     *
     * @param cause the original exception.
     */
    class Unexpected(cause: Throwable? = null) : NetworkFailure("Unexpected failure", cause)
}
