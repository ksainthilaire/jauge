package io.jauge.core.domain

import io.jauge.core.domain.error.JaugeError
import io.jauge.core.domain.model.AuthenticationProvider
import io.jauge.core.domain.model.AuthenticationState
import io.jauge.core.domain.model.JaugeNotification
import io.jauge.core.domain.model.NotificationKind
import io.jauge.core.domain.model.Outcome
import io.jauge.core.domain.model.PushPermissionStatus
import io.jauge.core.domain.repository.AuthenticationRepository
import io.jauge.core.domain.repository.NotificationRepository
import io.jauge.core.domain.usecase.ObserveIncomingCalmAlertsUseCase
import io.jauge.core.domain.usecase.SendMagicLinkUseCase
import io.jauge.core.domain.usecase.SignOutUseCase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Instant

class AuthenticationUseCaseTest {

    private class RecordingAuthenticationRepository(private val calls: MutableList<String>) : AuthenticationRepository {
        override val state: StateFlow<AuthenticationState> = MutableStateFlow(AuthenticationState.SignedOut)
        override suspend fun signInWith(provider: AuthenticationProvider): Outcome<Unit> = Outcome.Success(Unit)
        override suspend fun sendMagicLink(email: String): Outcome<Unit> {
            calls += "link:$email"
            return Outcome.Success(Unit)
        }
        override suspend fun completeSignIn(url: String): Outcome<Boolean> = Outcome.Success(false)
        override suspend fun signOut(): Outcome<Unit> {
            calls += "signOut"
            return Outcome.Success(Unit)
        }
    }

    private class RecordingNotificationRepository(
        private val calls: MutableList<String>,
        private val incoming: List<JaugeNotification> = emptyList(),
    ) : NotificationRepository {
        override fun observeIncoming(): Flow<JaugeNotification> = flowOf(*incoming.toTypedArray())
        override suspend fun getNotifications(): Outcome<List<JaugeNotification>> = Outcome.Success(incoming)
        override suspend fun markAsRead(notificationId: String): Outcome<Unit> = Outcome.Success(Unit)
        override suspend fun getPermissionStatus(): PushPermissionStatus = PushPermissionStatus.GRANTED
        override suspend fun requestPermission(): Outcome<PushPermissionStatus> = Outcome.Success(PushPermissionStatus.GRANTED)
        override suspend fun registerDevice(): Outcome<Unit> = Outcome.Success(Unit)
        override suspend fun unregisterDevice(): Outcome<Unit> {
            calls += "unregister"
            return Outcome.Success(Unit)
        }
    }

    @Test
    fun emailAddressesAreChecked() {
        assertTrue(SendMagicLinkUseCase.isValidEmail("ines.bertrand@exemple.fr"))
        assertTrue(SendMagicLinkUseCase.isValidEmail("a+jauge@b.io"))
        assertFalse(SendMagicLinkUseCase.isValidEmail("ines.bertrand"))
        assertFalse(SendMagicLinkUseCase.isValidEmail("ines@exemple"))
        assertFalse(SendMagicLinkUseCase.isValidEmail("ines @exemple.fr"))
        assertFalse(SendMagicLinkUseCase.isValidEmail(""))
    }

    @Test
    fun invalidEmailFailsWithoutCallingTheServer() = runTest {
        val calls = mutableListOf<String>()
        val result = SendMagicLinkUseCase(RecordingAuthenticationRepository(calls))("pas une adresse")
        assertEquals(Outcome.Failure(JaugeError.InvalidEmail), result)
        assertTrue(calls.isEmpty())
    }

    @Test
    fun validEmailIsSentTrimmed() = runTest {
        val calls = mutableListOf<String>()
        SendMagicLinkUseCase(RecordingAuthenticationRepository(calls))("  ines@exemple.fr ")
        assertEquals(listOf("link:ines@exemple.fr"), calls)
    }

    @Test
    fun signOutRemovesThePushTokenFirst() = runTest {
        val calls = mutableListOf<String>()
        SignOutUseCase(RecordingAuthenticationRepository(calls), RecordingNotificationRepository(calls))()
        assertEquals(listOf("unregister", "signOut"), calls)
    }

    @Test
    fun onlyCalmAlertsAreShownAsBanners() = runTest {
        fun notification(id: String, kind: NotificationKind) =
            JaugeNotification(id, kind, "t", "b", null, null, Instant.fromEpochMilliseconds(0), isRead = false)
        val repository = RecordingNotificationRepository(
            mutableListOf(),
            listOf(notification("1", NotificationKind.TRIAL_REMINDER), notification("2", NotificationKind.CALM_ALERT)),
        )
        val received = ObserveIncomingCalmAlertsUseCase(repository)().toList()
        assertEquals(listOf("2"), received.map { it.id })
    }
}
