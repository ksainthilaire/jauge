package io.jauge.core.notifications

import io.jauge.core.domain.model.JaugeNotification
import io.jauge.core.domain.model.NotificationKind
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.time.Instant

/**
 * Row of the `notifications` table (`supabase/migrations`), as read by Postgrest and Realtime.
 *
 * @property id identifier.
 * @property kind `calm_alert`, `trial_reminder` or a kind added later.
 * @property title title written by the server.
 * @property body text written by the server.
 * @property analysisId analysis to open, if any.
 * @property alertId calm alert that produced it, if any.
 * @property createdAt creation time (ISO 8601).
 * @property readAt when the user read it, null when unread.
 */
@Serializable
data class NotificationRow(
    val id: String,
    val kind: String,
    val title: String,
    val body: String,
    @SerialName("analysis_id") val analysisId: String? = null,
    @SerialName("alert_id") val alertId: String? = null,
    @SerialName("created_at") val createdAt: String,
    @SerialName("read_at") val readAt: String? = null,
) {
    /** The domain notification, or null when the row cannot be read (it is then left out, never shown wrong). */
    fun toDomain(): JaugeNotification? {
        val created = runCatching { Instant.parse(createdAt) }.getOrNull() ?: return null
        if (id.isBlank() || title.isBlank()) return null
        return JaugeNotification(
            id = id,
            kind = when (kind) {
                "calm_alert" -> NotificationKind.CALM_ALERT
                "trial_reminder" -> NotificationKind.TRIAL_REMINDER
                else -> NotificationKind.OTHER
            },
            title = title,
            body = body,
            analysisId = analysisId,
            alertId = alertId,
            createdAt = created,
            isRead = readAt != null,
        )
    }
}

/**
 * Parameters of the `register_device` database function.
 *
 * @property token push token.
 * @property platform `android` or `ios`.
 * @property language language of the phone.
 */
@Serializable
data class RegisterDeviceParameters(
    @SerialName("device_token") val token: String,
    @SerialName("device_platform") val platform: String,
    @SerialName("device_language") val language: String,
)

/**
 * Parameters of the `unregister_device` database function.
 *
 * @property token push token to remove.
 */
@Serializable
data class UnregisterDeviceParameters(@SerialName("device_token") val token: String)
