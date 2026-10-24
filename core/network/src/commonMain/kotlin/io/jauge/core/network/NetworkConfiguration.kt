package io.jauge.core.network

import io.jauge.core.network.payload.ApiErrorBodyPayload
import kotlinx.serialization.json.Json

/** JSON settings shared by every client: unknown fields are ignored so that the API can grow. */
val JaugeJson: Json = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
    encodeDefaults = true
    coerceInputValues = true
}

/** Network timeouts, in seconds. */
object NetworkTimeouts {
    /** Connection timeout. */
    const val CONNECT_SECONDS = 15L

    /** Read and write timeout (uploads of photos can be slow). */
    const val READ_WRITE_SECONDS = 30L
}

/**
 * Provides the access token of the signed in user, sent as `Authorization: Bearer`.
 * The Supabase session will implement it; until then [NoAccessTokenProvider] sends no header.
 */
fun interface AccessTokenProvider {
    /** The current access token, or null when signed out. */
    suspend fun accessToken(): String?
}

/** Provider used while authentication is not wired: no token. */
object NoAccessTokenProvider : AccessTokenProvider {
    override suspend fun accessToken(): String? = null
}

/**
 * Reads the machine readable code of an API error body, or null when the body is not the documented shape.
 *
 * @param body raw error body.
 */
fun parseApiErrorCode(body: String?): String? {
    if (body.isNullOrBlank()) return null
    return runCatching { JaugeJson.decodeFromString(ApiErrorBodyPayload.serializer(), body).error.code }.getOrNull()
}

/** Values of the `origin` part of image uploads. */
object ImageOrigins {
    /** A screenshot. */
    const val SCREENSHOT = "screenshot"

    /** A photo. */
    const val PHOTO = "photo"
}
