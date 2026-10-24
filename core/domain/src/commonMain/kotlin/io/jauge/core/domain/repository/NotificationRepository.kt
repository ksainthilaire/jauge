package io.jauge.core.domain.repository

import io.jauge.core.domain.model.JaugeNotification
import io.jauge.core.domain.model.Outcome
import io.jauge.core.domain.model.PushPermissionStatus
import kotlinx.coroutines.flow.Flow

/**
 * Notifications of the user: push registration (FCM on Android, APNs on iOS), the notification list stored in
 * Supabase and the Realtime feed used to show calm alerts while the app is open. Implemented in
 * `core:notifications`. Implementations never throw.
 */
interface NotificationRepository {
    /** Notifications created while the app is open (Supabase Realtime), one item per new notification. */
    fun observeIncoming(): Flow<JaugeNotification>

    /** Notifications of the user, newest first. */
    suspend fun getNotifications(): Outcome<List<JaugeNotification>>

    /**
     * Marks a notification as read.
     *
     * @param notificationId identifier of the notification.
     */
    suspend fun markAsRead(notificationId: String): Outcome<Unit>

    /** Current system permission. */
    suspend fun getPermissionStatus(): PushPermissionStatus

    /** Shows the system permission prompt (mockup 12, "Autoriser les alertes"), then registers the device. */
    suspend fun requestPermission(): Outcome<PushPermissionStatus>

    /** Saves the push token of this device for the signed in user (called after sign in and at start up). */
    suspend fun registerDevice(): Outcome<Unit>

    /** Removes the push token of this device (called before sign out). */
    suspend fun unregisterDevice(): Outcome<Unit>
}
