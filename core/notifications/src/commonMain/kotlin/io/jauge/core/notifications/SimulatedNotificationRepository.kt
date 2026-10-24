package io.jauge.core.notifications

import io.jauge.core.domain.model.JaugeNotification
import io.jauge.core.domain.model.Outcome
import io.jauge.core.domain.model.PushPermissionStatus
import io.jauge.core.domain.repository.NotificationRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * In memory notifications used when Supabase or push is not configured: the permission prompt answers "allowed",
 * nothing is sent. [emit] lets a debug menu or a test simulate a calm alert received by Realtime.
 *
 * @param latencyMilliseconds simulated delay of each call.
 */
class SimulatedNotificationRepository(
    private val latencyMilliseconds: Long = 300,
) : NotificationRepository {

    private val incoming = MutableSharedFlow<JaugeNotification>(extraBufferCapacity = 8)
    private val notifications = mutableListOf<JaugeNotification>()
    private var permission = PushPermissionStatus.NOT_DETERMINED

    /**
     * Simulates a notification received while the app is open.
     *
     * @param notification the notification.
     */
    fun emit(notification: JaugeNotification) {
        notifications.add(0, notification)
        incoming.tryEmit(notification)
    }

    override fun observeIncoming(): Flow<JaugeNotification> = incoming.asSharedFlow()

    override suspend fun getNotifications(): Outcome<List<JaugeNotification>> {
        delay(latencyMilliseconds)
        return Outcome.Success(notifications.toList())
    }

    override suspend fun markAsRead(notificationId: String): Outcome<Unit> {
        val index = notifications.indexOfFirst { it.id == notificationId }
        if (index >= 0) notifications[index] = notifications[index].copy(isRead = true)
        return Outcome.Success(Unit)
    }

    override suspend fun getPermissionStatus(): PushPermissionStatus = permission

    override suspend fun requestPermission(): Outcome<PushPermissionStatus> {
        delay(latencyMilliseconds)
        permission = PushPermissionStatus.GRANTED
        return Outcome.Success(permission)
    }

    override suspend fun registerDevice(): Outcome<Unit> = Outcome.Success(Unit)

    override suspend fun unregisterDevice(): Outcome<Unit> = Outcome.Success(Unit)
}
