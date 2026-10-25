package io.jauge.feature.analysis.home

import androidx.compose.runtime.Composable

/**
 * Access to the clipboard for the "Coller" button of the home screen.
 *
 * The content is read only when the person taps the button ([readText]): on iOS this is the moment the system may
 * ask "Autoriser le collage ?", and on Android 12 and later the moment it shows "Jauge a collé depuis le presse-papiers".
 * [mayContainLink] never reads the content: it only looks at what the system says about it, so it shows no alert.
 */
interface ClipboardReader {
    /** True when the system says the clipboard probably holds a web address, without reading it. */
    fun mayContainLink(): Boolean

    /** Text of the clipboard, null when it is empty, not text, or the read was refused. Call it only after a tap. */
    fun readText(): String?
}

/** Clipboard reader of the platform, remembered for the composition. */
@Composable
expect fun rememberClipboardReader(): ClipboardReader
