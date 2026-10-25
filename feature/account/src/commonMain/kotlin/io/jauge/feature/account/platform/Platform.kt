package io.jauge.feature.account.platform

import androidx.compose.runtime.Composable

/**
 * Remembers a function that opens the system share sheet with a text (the export of the analyses).
 *
 * @return the function, taking the title of the sheet and the text.
 */
@Composable
expect fun rememberTextSharer(): (title: String, text: String) -> Unit

/** Version of the installed app ("1.0.0"), empty when it cannot be read. */
@Composable
expect fun rememberAppVersion(): String

/**
 * Remembers a function that opens the mail app of the phone (Android: the default email app, iOS: Mail with the
 * `message://` scheme). It never throws: when no mail app can be opened it calls `onUnavailable` instead.
 *
 * @return the function, taking the callback for a missing mail app.
 */
@Composable
expect fun rememberMailAppOpener(): (onUnavailable: () -> Unit) -> Unit
