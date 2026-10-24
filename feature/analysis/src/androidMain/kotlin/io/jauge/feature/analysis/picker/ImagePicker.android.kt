package io.jauge.feature.analysis.picker

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import io.jauge.core.domain.model.ImageOrigin
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File

/** Android: Photo Picker for screenshots, the camera app (through a FileProvider file) for photos. */
@Composable
actual fun rememberImagePicker(onResult: (PickedImage?) -> Unit, onCancel: () -> Unit): ImagePicker {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val currentOnResult by rememberUpdatedState(onResult)
    val currentOnCancel by rememberUpdatedState(onCancel)
    var photoPath by rememberSaveable { mutableStateOf<String?>(null) }

    fun deliver(uri: Uri, origin: ImageOrigin) {
        scope.launch {
            val image = withContext(Dispatchers.IO) { runCatching { readImage(context, uri, origin) }.getOrNull() }
            currentOnResult(image)
        }
    }

    val galleryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri == null) currentOnCancel() else deliver(uri, ImageOrigin.SCREENSHOT)
    }
    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { saved ->
        val path = photoPath
        if (saved && path != null) deliver(Uri.fromFile(File(path)), ImageOrigin.PHOTO) else currentOnCancel()
    }

    return remember(context, galleryLauncher, cameraLauncher) {
        object : ImagePicker {
            override fun pickScreenshot() {
                galleryLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            }

            override fun takePhoto() {
                val file = runCatching {
                    File(context.cacheDir, "photos").apply { mkdirs() }.let { File(it, "offer.jpg") }
                }.getOrNull()
                if (file == null) {
                    currentOnResult(null)
                    return
                }
                photoPath = file.absolutePath
                val uri = FileProvider.getUriForFile(context, context.packageName + ".analysis.files", file)
                runCatching { cameraLauncher.launch(uri) }.onFailure { currentOnResult(null) }
            }
        }
    }
}

/** Decodes the image at [uri] at a reduced size and encodes it as JPEG. */
private fun readImage(context: Context, uri: Uri, origin: ImageOrigin): PickedImage? {
    val resolver = context.contentResolver
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) } ?: return null
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
    var sampleSize = 1
    while (maxOf(bounds.outWidth, bounds.outHeight) / (sampleSize * 2) >= MAXIMUM_IMAGE_SIDE) sampleSize *= 2
    val options = BitmapFactory.Options().apply { inSampleSize = sampleSize }
    val decoded = resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) } ?: return null
    val longest = maxOf(decoded.width, decoded.height)
    val bitmap = if (longest > MAXIMUM_IMAGE_SIDE) {
        val ratio = MAXIMUM_IMAGE_SIDE.toFloat() / longest
        Bitmap.createScaledBitmap(decoded, (decoded.width * ratio).toInt(), (decoded.height * ratio).toInt(), true)
    } else {
        decoded
    }
    val output = ByteArrayOutputStream()
    bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, output)
    return PickedImage(output.toByteArray(), "image/jpeg", origin)
}
