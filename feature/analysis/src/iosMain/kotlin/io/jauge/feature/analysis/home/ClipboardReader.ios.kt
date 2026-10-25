package io.jauge.feature.analysis.home

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import platform.UIKit.UIPasteboard

/** iOS: the general pasteboard. `hasURLs` does not show the paste alert; `string` may, so it waits for a tap. */
@Composable
actual fun rememberClipboardReader(): ClipboardReader = remember { IosClipboardReader() }

private class IosClipboardReader : ClipboardReader {
    override fun mayContainLink(): Boolean = UIPasteboard.generalPasteboard.hasURLs

    override fun readText(): String? =
        (UIPasteboard.generalPasteboard.string ?: UIPasteboard.generalPasteboard.URL?.absoluteString)?.takeIf { it.isNotBlank() }
}
