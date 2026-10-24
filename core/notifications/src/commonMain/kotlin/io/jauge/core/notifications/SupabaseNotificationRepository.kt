package io.jauge.core.notifications

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Order
import io.github.jan.supabase.postgrest.rpc
import io.github.jan.supabase.realtime.PostgresAction
import io.github.jan.supabase.realtime.channel
import io.github.jan.supabase.realtime.decodeRecord
import io.github.jan.supabase.realtime.postgresChangeFlow
import io.github.jan.supabase.realtime.realtime
import io.github.jan.supabase.postgrest.query.filter.FilterOperator
import io.jauge.core.authentication.supabaseOutcome
import io.jauge.core.domain.model.AuthenticationState
import io.jauge.core.domain.model.JaugeNotification
import io.jauge.core.domain.model.Outcome
import io.jauge.core.domain.model.PushPermissionStatus
import io.jauge.core.domain.repository.AuthenticationRepository
import io.jauge.core.domain.repository.NotificationRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlin.time.Clock

/**
 * [NotificationRepository] backed by Supabase: the `notifications` table (Postgrest, row level security limits every
 * query to the signed in user), Realtime for the notifications created while the app is open, and the
 * `register_device` / `unregister_device` functions for the push token of the platform [PushService].
 *
 * @param client the Supabase client.
 * @param authentication authentication state (Realtime only listens while a user is signed in).
 * @param pushService push service of the platform.
 */
class SupabaseNotificationRepository(
    private val client: SupabaseClient,
    private val authentication: AuthenticationRepository,
    private val pushService: PushService,
) : NotificationRepository {

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun observeIncoming(): Flow<JaugeNotification> = authentication.state
        .map { (it as? AuthenticationState.SignedIn)?.user?.id }
        .distinctUntilChanged()
        .flatMapLatest { userId -> if (userId == null) emptyFlow() else realtimeNotifications(userId) }

    private fun realtimeNotifications(userId: String): Flow<JaugeNotification> = flow {
        val channel = client.channel("notifications-$userId")
        val inserts = channel.postgresChangeFlow<PostgresAction.Insert>(schema = "public") {
            table = TABLE_NOTIFICATIONS
            filter("user_id", FilterOperator.EQ, userId)
        }
        try {
            channel.subscribe()
            inserts
                .mapNotNull { action -> runCatching { action.decodeRecord<NotificationRow>() }.getOrNull()?.toDomain() }
                .collect { emit(it) }
        } finally {
            runCatching { client.realtime.removeChannel(channel) }
        }
    }

    override suspend fun getNotifications(): Outcome<List<JaugeNotification>> = supabaseOutcome {
        if (!isSignedIn()) return@supabaseOutcome emptyList()
        client.from(TABLE_NOTIFICATIONS)
            .select {
                order("created_at", Order.DESCENDING)
                limit(MAXIMUM_NOTIFICATIONS)
            }
            .decodeList<NotificationRow>()
            .mapNotNull { it.toDomain() }
    }

    override suspend fun markAsRead(notificationId: String): Outcome<Unit> = supabaseOutcome {
        if (!isSignedIn()) return@supabaseOutcome
        client.from(TABLE_NOTIFICATIONS).update({ set("read_at", Clock.System.now().toString()) }) {
            filter { eq("id", notificationId) }
        }
    }

    override suspend fun getPermissionStatus(): PushPermissionStatus = pushService.permissionStatus()

    override suspend fun requestPermission(): Outcome<PushPermissionStatus> {
        val status = pushService.requestPermission()
        if (status == PushPermissionStatus.GRANTED) registerDevice()
        return Outcome.Success(status)
    }

    override suspend fun registerDevice(): Outcome<Unit> = supabaseOutcome {
        if (!isSignedIn() || pushService.permissionStatus() != PushPermissionStatus.GRANTED) return@supabaseOutcome
        val token = pushService.token() ?: return@supabaseOutcome
        client.postgrest.rpc(
            "register_device",
            RegisterDeviceParameters(token = token, platform = pushService.platformName, language = pushService.language),
        )
    }

    override suspend fun unregisterDevice(): Outcome<Unit> = supabaseOutcome {
        if (!isSignedIn()) return@supabaseOutcome
        val token = pushService.token() ?: return@supabaseOutcome
        client.postgrest.rpc("unregister_device", UnregisterDeviceParameters(token))
    }

    private fun isSignedIn(): Boolean = authentication.state.value is AuthenticationState.SignedIn

    private companion object {
        const val TABLE_NOTIFICATIONS = "notifications"
        const val MAXIMUM_NOTIFICATIONS = 50L
    }
}
