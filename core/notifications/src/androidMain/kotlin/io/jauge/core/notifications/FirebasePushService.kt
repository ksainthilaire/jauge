package io.jauge.core.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.google.firebase.FirebaseApp
import com.google.firebase.messaging.FirebaseMessaging
import io.jauge.core.domain.model.PushPermissionStatus
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.Locale
import kotlin.coroutines.resume

/**
 * Firebase Cloud Messaging. Push is available only when the app was built with a `google-services.json` (Firebase
 * initialized); otherwise every call answers "unavailable" and the app keeps working with Realtime only.
 *
 * @param context application context.
 */
class FirebasePushService(private val context: Context) : PushService {

    override val platformName: String = "android"

    override val language: String
        get() = Locale.getDefault().language.ifBlank { "fr" }

    private val isFirebaseReady: Boolean
        get() = runCatching { FirebaseApp.getApps(context).isNotEmpty() }.getOrDefault(false)

    private val preferences by lazy { context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE) }

    init {
        createChannel()
    }

    override suspend fun permissionStatus(): PushPermissionStatus = when {
        !isFirebaseReady -> PushPermissionStatus.UNAVAILABLE
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ->
            if (NotificationManagerCompat.from(context).areNotificationsEnabled()) PushPermissionStatus.GRANTED else PushPermissionStatus.DENIED
        isPermissionGranted() -> PushPermissionStatus.GRANTED
        preferences.getBoolean(KEY_ASKED, false) -> PushPermissionStatus.DENIED
        else -> PushPermissionStatus.NOT_DETERMINED
    }

    override suspend fun requestPermission(): PushPermissionStatus {
        val current = permissionStatus()
        if (current != PushPermissionStatus.NOT_DETERMINED && current != PushPermissionStatus.DENIED) return current
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return current
        val granted = NotificationPermissionActivity.request(context)
        preferences.edit().putBoolean(KEY_ASKED, true).apply()
        return if (granted) PushPermissionStatus.GRANTED else PushPermissionStatus.DENIED
    }

    override suspend fun token(): String? {
        if (!isFirebaseReady) return null
        return suspendCancellableCoroutine { continuation ->
            FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
                continuation.resume(if (task.isSuccessful) task.result else null)
            }
        }
    }

    private fun isPermissionGranted(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    private fun createChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            CALM_ALERTS_CHANNEL,
            context.getString(R.string.notification_channel_calm_alerts),
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply { description = context.getString(R.string.notification_channel_calm_alerts_description) }
        context.getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
    }

    /** Names shared with the manifest and the Edge Function. */
    companion object {
        /** Notification channel of calm alerts (also the default channel of Firebase in the manifest). */
        const val CALM_ALERTS_CHANNEL = "calm_alerts"
        private const val PREFERENCES = "jauge_notifications"
        private const val KEY_ASKED = "permission_asked"
    }
}

/** Starts an activity from the application context. */
internal fun Context.startActivityFromApplication(intent: Intent) {
    startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}
