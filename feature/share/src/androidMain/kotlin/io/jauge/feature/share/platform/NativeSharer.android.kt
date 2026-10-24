package io.jauge.feature.share.platform

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.provider.Telephony
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File

/** Android implementation: intents, a FileProvider for the card and MediaStore for saving. */
@Composable
actual fun rememberNativeSharer(): NativeSharer {
    val context = LocalContext.current
    return remember(context) { AndroidNativeSharer(context) }
}

/** Encodes the bitmap as PNG, copying hardware bitmaps to a software one first. */
actual fun ImageBitmap.encodeToPng(): ByteArray {
    val bitmap = asAndroidBitmap().let { source ->
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && source.config == Bitmap.Config.HARDWARE) {
            source.copy(Bitmap.Config.ARGB_8888, false)
        } else {
            source
        }
    }
    return ByteArrayOutputStream().use { output ->
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)
        output.toByteArray()
    }
}

/**
 * Shares with Android intents. The card is written to the cache and exposed through the FileProvider declared in the
 * manifest of this module (authority `<applicationId>.jauge.share`).
 *
 * @param context context of the screen.
 */
class AndroidNativeSharer(private val context: Context) : NativeSharer {

    override fun copyText(text: String) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return
        clipboard.setPrimaryClip(ClipData.newPlainText(text, text))
    }

    override fun shareText(text: String): Boolean {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
        }
        return start(Intent.createChooser(intent, null))
    }

    override fun shareImage(png: ByteArray, text: String): Boolean {
        val uri = writeCard(png) ?: return shareText(text)
        return start(Intent.createChooser(imageIntent(uri, text), null))
    }

    override fun sendMessage(text: String, png: ByteArray?): Boolean {
        val smsPackage = Telephony.Sms.getDefaultSmsPackage(context)
        val intent = if (png != null) {
            val uri = writeCard(png) ?: return false
            imageIntent(uri, text).apply { smsPackage?.let { setPackage(it) } }
        } else {
            Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:")).apply { putExtra("sms_body", text) }
        }
        return start(intent) || (if (png != null) shareImage(png, text) else shareText(text))
    }

    override suspend fun saveImage(png: ByteArray, fileName: String): Boolean = withContext(Dispatchers.IO) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return@withContext false
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "$fileName.png")
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/Jauge")
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
        val uri = runCatching { resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) }.getOrNull()
            ?: return@withContext false
        val written = runCatching {
            resolver.openOutputStream(uri)?.use { it.write(png) } != null
        }.getOrDefault(false)
        if (!written) {
            runCatching { resolver.delete(uri, null, null) }
            return@withContext false
        }
        values.clear()
        values.put(MediaStore.Images.Media.IS_PENDING, 0)
        runCatching { resolver.update(uri, values, null, null) }.isSuccess
    }

    private fun imageIntent(uri: Uri, text: String) = Intent(Intent.ACTION_SEND).apply {
        type = "image/png"
        putExtra(Intent.EXTRA_STREAM, uri)
        putExtra(Intent.EXTRA_TEXT, text)
        clipData = ClipData.newRawUri(null, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }

    private fun writeCard(png: ByteArray): Uri? = runCatching {
        val directory = File(context.cacheDir, SHARE_DIRECTORY).apply { mkdirs() }
        val file = File(directory, CARD_FILE_NAME).apply { writeBytes(png) }
        FileProvider.getUriForFile(context, context.packageName + AUTHORITY_SUFFIX, file)
    }.getOrNull()

    private fun start(intent: Intent): Boolean = try {
        if (context !is android.app.Activity) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
        true
    } catch (exception: ActivityNotFoundException) {
        false
    } catch (exception: SecurityException) {
        false
    }

    private companion object {
        const val SHARE_DIRECTORY = "jauge-share"
        const val CARD_FILE_NAME = "jauge-card.png"
        const val AUTHORITY_SUFFIX = ".jauge.share"
    }
}
