package io.jauge.core.notifications

import io.jauge.core.domain.model.JaugeNotification
import io.jauge.core.domain.model.NotificationKind
import io.jauge.core.domain.model.Outcome
import io.jauge.core.domain.model.PushPermissionStatus
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

class NotificationRowTest {

    private val row = NotificationRow(
        id = "7d0c3a4e-0000-4000-8000-000000000001",
        kind = "calm_alert",
        title = "Une offre jumelle devient calme",
        body = "Chargée de communication, Villeurbanne : 19/100",
        analysisId = "a-41872",
        createdAt = "2026-10-24T10:15:30.123456+00:00",
    )

    @Test
    fun rowsBecomeNotifications() {
        val notification = row.toDomain()!!
        assertEquals(NotificationKind.CALM_ALERT, notification.kind)
        assertEquals("a-41872", notification.analysisId)
        assertEquals(false, notification.isRead)
        assertEquals(Instant.parse("2026-10-24T10:15:30.123456Z"), notification.createdAt)
        assertTrue(row.copy(readAt = "2026-10-24T11:00:00+00:00").toDomain()!!.isRead)
        assertEquals(NotificationKind.OTHER, row.copy(kind = "weekly_digest").toDomain()!!.kind)
    }

    @Test
    fun unreadableRowsAreLeftOut() {
        assertNull(row.copy(createdAt = "hier").toDomain())
        assertNull(row.copy(title = "").toDomain())
    }

    @Test
    fun simulatedRepositoryGrantsAndEmits() = runTest {
        val repository = SimulatedNotificationRepository(latencyMilliseconds = 0)
        assertEquals(PushPermissionStatus.NOT_DETERMINED, repository.getPermissionStatus())
        assertEquals(Outcome.Success(PushPermissionStatus.GRANTED), repository.requestPermission())
        val notification = JaugeNotification("1", NotificationKind.CALM_ALERT, "t", "b", null, null, Instant.fromEpochMilliseconds(0), false)
        repository.emit(notification)
        assertEquals(listOf(notification), (repository.getNotifications() as Outcome.Success).value)
        repository.markAsRead("1")
        assertTrue((repository.getNotifications() as Outcome.Success).value.first().isRead)
    }
}
