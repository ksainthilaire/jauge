package io.jauge.feature.analysis.picker

import androidx.compose.runtime.Composable
import io.jauge.core.domain.model.ImageOrigin

/**
 * An image chosen by the user, already reduced to a size the API accepts.
 *
 * @property bytes encoded image (JPEG).
 * @property mimeType type of [bytes], for example `image/jpeg`.
 * @property origin screenshot from the gallery or photo from the camera.
 */
class PickedImage(val bytes: ByteArray, val mimeType: String, val origin: ImageOrigin)

/** Opens the system pickers: the gallery for a screenshot, the camera for a photo. */
interface ImagePicker {
    /** Opens the gallery, images only. */
    fun pickScreenshot()

    /** Opens the camera. */
    fun takePhoto()
}

/**
 * Remembers an [ImagePicker]: Photo Picker and the camera app on Android, PHPickerViewController and
 * UIImagePickerController on iOS. Images are reduced to [MAXIMUM_IMAGE_SIDE] pixels on their longest side.
 *
 * @param onResult called on the main thread with the image, or null when the user cancelled or the image could not
 * be opened (the screen shows a friendly message).
 * @param onCancel called when the user closed the picker without choosing.
 * @return the picker.
 */
@Composable
expect fun rememberImagePicker(onResult: (PickedImage?) -> Unit, onCancel: () -> Unit = {}): ImagePicker

/** Longest side of the images sent to the API, enough to read an offer. */
const val MAXIMUM_IMAGE_SIDE = 2048

/** JPEG quality of the images sent to the API. */
const val JPEG_QUALITY = 85
