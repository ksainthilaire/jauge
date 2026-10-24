package io.jauge.core.domain.usecase

import io.jauge.core.domain.model.JaugeNotification
import io.jauge.core.domain.model.NotificationKind
import io.jauge.core.domain.model.Outcome
import io.jauge.core.domain.model.PushPermissionStatus
import io.jauge.core.domain.repository.NotificationRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filter

/**
 * Calm alerts received while the app is open (Supabase Realtime), shown as an in app banner.
 *
 * @param repository notification repository.
 */
class ObserveIncomingCalmAlertsUseCase(private val repository: NotificationRepository) {
    /** New calm alerts, one item per alert. */
    operator fun invoke(): Flow<JaugeNotification> = repository.observeIncoming().filter { it.kind == NotificationKind.CALM_ALERT }
}

/**
 * Notifications of the user, newest first.
 *
 * @param repository notification repository.
 */
class GetNotificationsUseCase(private val repository: NotificationRepository) {
    /** Loads the notifications. */
    suspend operator fun invoke(): Outcome<List<JaugeNotification>> = repository.getNotifications()
}

/**
 * Marks a notification as read.
 *
 * @param repository notification repository.
 */
class MarkNotificationAsReadUseCase(private val repository: NotificationRepository) {
    /**
     * Marks it.
     *
     * @param notificationId identifier of the notification.
     */
    suspend operator fun invoke(notificationId: String): Outcome<Unit> = repository.markAsRead(notificationId)
}

/**
 * Current notification permission (mockup 12 is skipped when already granted).
 *
 * @param repository notification repository.
 */
class GetPushPermissionUseCase(private val repository: NotificationRepository) {
    /** Reads the permission. */
    suspend operator fun invoke(): PushPermissionStatus = repository.getPermissionStatus()
}

/**
 * Asks the system permission ("Autoriser les alertes", mockup 12) and registers the device when granted.
 *
 * @param repository notification repository.
 */
class RequestPushPermissionUseCase(private val repository: NotificationRepository) {
    /** Shows the system prompt. */
    suspend operator fun invoke(): Outcome<PushPermissionStatus> = repository.requestPermission()
}

/**
 * Saves the push token of this device for the signed in user.
 *
 * @param repository notification repository.
 */
class RegisterDeviceUseCase(private val repository: NotificationRepository) {
    /** Registers the device. */
    suspend operator fun invoke(): Outcome<Unit> = repository.registerDevice()
}
