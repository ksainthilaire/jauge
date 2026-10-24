package io.jauge.core.notifications

import com.google.firebase.messaging.FirebaseMessagingService
import io.jauge.core.domain.repository.NotificationRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.koin.mp.KoinPlatform

/**
 * Receives the new FCM tokens and saves them in Supabase. Notifications sent by the Edge Function carry a
 * `notification` part: Android shows them itself when the app is in the background, and the app shows calm alerts
 * from Realtime when it is open, so no message needs to be drawn here.
 */
class JaugeMessagingService : FirebaseMessagingService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** Saves the new token of this device for the signed in user. */
    override fun onNewToken(token: String) {
        val repository = KoinPlatform.getKoinOrNull()?.getOrNull<NotificationRepository>() ?: return
        scope.launch { repository.registerDevice() }
    }
}
