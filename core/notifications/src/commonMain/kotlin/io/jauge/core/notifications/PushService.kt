package io.jauge.core.notifications

import io.jauge.core.domain.model.PushPermissionStatus

/**
 * Push service of the platform: Firebase Cloud Messaging on Android, Apple Push Notification service on iOS.
 */
interface PushService {
    /** Value of the `platform` column of the `devices` table: `android` or `ios`. */
    val platformName: String

    /** Current system permission. */
    suspend fun permissionStatus(): PushPermissionStatus

    /** Shows the system prompt when the user has not answered yet, and returns the result. */
    suspend fun requestPermission(): PushPermissionStatus

    /** Push token of this device, null when push is not available (no Firebase or APNs configuration). */
    suspend fun token(): String?

    /** Language of the phone ("fr", "en"), used by the server to write the notifications. */
    val language: String
}

/** Push service used by tests and when the platform has no push configuration. */
class UnavailablePushService(override val platformName: String, override val language: String = "fr") : PushService {
    override suspend fun permissionStatus(): PushPermissionStatus = PushPermissionStatus.UNAVAILABLE

    override suspend fun requestPermission(): PushPermissionStatus = PushPermissionStatus.UNAVAILABLE

    override suspend fun token(): String? = null
}
