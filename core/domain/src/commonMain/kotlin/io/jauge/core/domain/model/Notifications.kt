package io.jauge.core.domain.model

import kotlin.time.Instant

/** Kinds of notifications sent by Jauge (stored in the Supabase `notifications` table). */
enum class NotificationKind {
    /** A twin offer became calm (mockups 06 and 12). */
    CALM_ALERT,

    /** Kind reserved by the server for a trial notice; the app promises no reminder before the end of the trial. */
    TRIAL_REMINDER,

    /** Any other message (kept for kinds added later on the server). */
    OTHER,
}

/**
 * A notification of the user, received by push or by Realtime while the app is open.
 *
 * @property id identifier.
 * @property kind kind of notification.
 * @property title short title, already written in the language of the user by the server.
 * @property body text of the notification.
 * @property analysisId analysis to open when the user taps it, if any.
 * @property alertId calm alert that produced it, if any.
 * @property createdAt when it was created.
 * @property isRead true once the user has seen it.
 */
data class JaugeNotification(
    val id: String,
    val kind: NotificationKind,
    val title: String,
    val body: String,
    val analysisId: String?,
    val alertId: String?,
    val createdAt: Instant,
    val isRead: Boolean,
)

/** Whether the system lets Jauge show notifications (mockup 12). */
enum class PushPermissionStatus {
    /** The user has not been asked yet. */
    NOT_DETERMINED,

    /** Notifications are allowed. */
    GRANTED,

    /** The user refused: only the system settings can change it. */
    DENIED,

    /** Push is not available in this build (no Firebase or APNs configuration). */
    UNAVAILABLE,
}
