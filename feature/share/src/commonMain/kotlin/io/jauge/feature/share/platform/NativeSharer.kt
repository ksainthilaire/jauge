package io.jauge.feature.share.platform

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.ImageBitmap

/**
 * Sharing features of the platform (Android intents and FileProvider, iOS UIActivityViewController and
 * MFMessageComposeViewController). Every function returns false instead of throwing when the platform cannot do it,
 * so that the screen can show a friendly message or fall back to the share sheet.
 */
interface NativeSharer {
    /**
     * Copies a text (the public link) to the clipboard.
     *
     * @param text the text to copy.
     */
    fun copyText(text: String)

    /**
     * Opens the share sheet with a text only (link format).
     *
     * @param text message with the public link.
     * @return false when no app can share it.
     */
    fun shareText(text: String): Boolean

    /**
     * Opens the share sheet with the card image and a text.
     *
     * @param png the card, encoded as PNG.
     * @param text message with the public link.
     * @return false when no app can share it.
     */
    fun shareImage(png: ByteArray, text: String): Boolean

    /**
     * Opens the messaging app with the text and, when given, the card attached.
     *
     * @param text message with the public link.
     * @param png the card, or null for the link format.
     * @return false when no messaging app is available.
     */
    fun sendMessage(text: String, png: ByteArray?): Boolean

    /**
     * Saves the card in the photos of the device.
     *
     * @param png the card, encoded as PNG.
     * @param fileName file name without extension.
     * @return false when the platform cannot save it here (the screen then opens the share sheet).
     */
    suspend fun saveImage(png: ByteArray, fileName: String): Boolean
}

/** The [NativeSharer] of the platform, bound to the current screen. */
@Composable
expect fun rememberNativeSharer(): NativeSharer

/** Encodes an image rendered by Compose (the share card) as PNG bytes. */
expect fun ImageBitmap.encodeToPng(): ByteArray
