package io.jauge.feature.analysis.home

import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.view.textclassifier.TextClassifier
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/** Android: the system clipboard; the URL guess uses the classification of Android 12 and later. */
@Composable
actual fun rememberClipboardReader(): ClipboardReader {
    val context = LocalContext.current
    return remember(context) { AndroidClipboardReader(context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager) }
}

private class AndroidClipboardReader(private val clipboard: ClipboardManager) : ClipboardReader {
    override fun mayContainLink(): Boolean = runCatching {
        // The description is metadata only: reading it never triggers the "pasted from clipboard" toast.
        val description = clipboard.primaryClipDescription ?: return false
        if (description.hasMimeType(ClipDescription.MIMETYPE_TEXT_URILIST)) return true
        if (!description.hasMimeType(ClipDescription.MIMETYPE_TEXT_PLAIN)) return false
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            description.classificationStatus == ClipDescription.CLASSIFICATION_COMPLETE &&
            description.getConfidenceScore(TextClassifier.TYPE_URL) >= LINK_CONFIDENCE
    }.getOrDefault(false)

    override fun readText(): String? = runCatching {
        clipboard.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.text?.toString()
    }.getOrNull()?.takeIf { it.isNotBlank() }

    private companion object {
        const val LINK_CONFIDENCE = 0.5f
    }
}
