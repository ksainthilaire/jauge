package io.jauge.feature.onboarding.alerts

import androidx.compose.runtime.Composable

/**
 * Remembers a function that asks the system for the permission to show notifications: `POST_NOTIFICATIONS` on
 * Android 13 and later (granted at install before), `UNUserNotificationCenter` on iOS.
 *
 * @param onResult called on the main thread with true when notifications are allowed.
 * @return the function that shows the system request.
 */
@Composable
expect fun rememberNotificationPermissionRequest(onResult: (granted: Boolean) -> Unit): () -> Unit
