package io.jauge.core.notifications

import io.jauge.core.domain.model.PushPermissionStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import platform.Foundation.NSLocale
import platform.Foundation.currentLocale
import platform.Foundation.languageCode
import platform.UIKit.UIApplication
import platform.UIKit.registerForRemoteNotifications
import platform.UserNotifications.UNAuthorizationOptionAlert
import platform.UserNotifications.UNAuthorizationOptionBadge
import platform.UserNotifications.UNAuthorizationOptionSound
import platform.UserNotifications.UNAuthorizationStatusAuthorized
import platform.UserNotifications.UNAuthorizationStatusDenied
import platform.UserNotifications.UNAuthorizationStatusEphemeral
import platform.UserNotifications.UNAuthorizationStatusNotDetermined
import platform.UserNotifications.UNAuthorizationStatusProvisional
import platform.UserNotifications.UNUserNotificationCenter
import kotlin.coroutines.resume

/**
 * Device token given by iOS to the app delegate (`iosApp/iosApp/iOSApp.swift`), forwarded through
 * `onApnsTokenReceived` of the shared framework.
 */
object ApnsTokenStore {
    private val mutableToken = MutableStateFlow<String?>(null)

    /** Current APNs token, as a hexadecimal string. */
    val token: StateFlow<String?> = mutableToken.asStateFlow()

    /**
     * Saves a new token.
     *
     * @param hexadecimalToken the token, as a hexadecimal string.
     */
    fun update(hexadecimalToken: String) {
        if (hexadecimalToken.isNotBlank()) mutableToken.value = hexadecimalToken
    }
}

/** Apple Push Notification service: permission with UserNotifications, token from the app delegate. */
class ApnsPushService : PushService {

    override val platformName: String = "ios"

    override val language: String
        get() = NSLocale.currentLocale.languageCode.ifBlank { "fr" }

    override suspend fun permissionStatus(): PushPermissionStatus = suspendCancellableCoroutine { continuation ->
        UNUserNotificationCenter.currentNotificationCenter().getNotificationSettingsWithCompletionHandler { settings ->
            val status = when (settings?.authorizationStatus) {
                UNAuthorizationStatusAuthorized, UNAuthorizationStatusProvisional, UNAuthorizationStatusEphemeral -> PushPermissionStatus.GRANTED
                UNAuthorizationStatusDenied -> PushPermissionStatus.DENIED
                UNAuthorizationStatusNotDetermined -> PushPermissionStatus.NOT_DETERMINED
                else -> PushPermissionStatus.UNAVAILABLE
            }
            continuation.resume(status)
        }
    }

    override suspend fun requestPermission(): PushPermissionStatus {
        val granted = suspendCancellableCoroutine { continuation ->
            UNUserNotificationCenter.currentNotificationCenter().requestAuthorizationWithOptions(
                UNAuthorizationOptionAlert or UNAuthorizationOptionSound or UNAuthorizationOptionBadge,
            ) { allowed, _ -> continuation.resume(allowed) }
        }
        if (granted) registerForRemoteNotifications()
        return if (granted) PushPermissionStatus.GRANTED else PushPermissionStatus.DENIED
    }

    override suspend fun token(): String? {
        ApnsTokenStore.token.value?.let { return it }
        if (permissionStatus() != PushPermissionStatus.GRANTED) return null
        registerForRemoteNotifications()
        return withTimeoutOrNull(TOKEN_TIMEOUT_MILLISECONDS) { ApnsTokenStore.token.filterNotNull().first() }
    }

    private suspend fun registerForRemoteNotifications() = withContext(Dispatchers.Main) {
        UIApplication.sharedApplication.registerForRemoteNotifications()
    }

    private companion object {
        const val TOKEN_TIMEOUT_MILLISECONDS = 10_000L
    }
}
