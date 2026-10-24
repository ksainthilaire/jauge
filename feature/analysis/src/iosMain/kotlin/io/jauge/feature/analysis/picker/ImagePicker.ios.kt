package io.jauge.feature.analysis.picker

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import io.jauge.core.domain.model.ImageOrigin
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.useContents
import kotlinx.cinterop.usePinned
import platform.CoreGraphics.CGRectMake
import platform.CoreGraphics.CGSizeMake
import platform.Foundation.NSData
import platform.PhotosUI.PHPickerConfiguration
import platform.PhotosUI.PHPickerFilter
import platform.PhotosUI.PHPickerResult
import platform.PhotosUI.PHPickerViewController
import platform.PhotosUI.PHPickerViewControllerDelegateProtocol
import platform.UIKit.UIApplication
import platform.UIKit.UIGraphicsBeginImageContextWithOptions
import platform.UIKit.UIGraphicsEndImageContext
import platform.UIKit.UIGraphicsGetImageFromCurrentImageContext
import platform.UIKit.UIImage
import platform.UIKit.UIImageJPEGRepresentation
import platform.UIKit.UIImagePickerController
import platform.UIKit.UIImagePickerControllerDelegateProtocol
import platform.UIKit.UIImagePickerControllerOriginalImage
import platform.UIKit.UIImagePickerControllerSourceType
import platform.UIKit.UINavigationControllerDelegateProtocol
import platform.UIKit.UIViewController
import platform.darwin.NSObject
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue
import platform.posix.memcpy

/** iOS: PHPickerViewController for screenshots, UIImagePickerController with the camera for photos. */
@Composable
actual fun rememberImagePicker(onResult: (PickedImage?) -> Unit, onCancel: () -> Unit): ImagePicker {
    val currentOnResult by rememberUpdatedState(onResult)
    val currentOnCancel by rememberUpdatedState(onCancel)
    return remember { IosImagePicker(onResult = { currentOnResult(it) }, onCancel = { currentOnCancel() }) }
}

/**
 * [ImagePicker] presenting the system pickers above the top view controller. It keeps its delegates alive while a
 * picker is shown.
 *
 * @param onResult receives the image, or null when it could not be read.
 * @param onCancel called when the user closed the picker.
 */
private class IosImagePicker(
    private val onResult: (PickedImage?) -> Unit,
    private val onCancel: () -> Unit,
) : ImagePicker {
    private var galleryDelegate: GalleryDelegate? = null
    private var cameraDelegate: CameraDelegate? = null

    override fun pickScreenshot() {
        val configuration = PHPickerConfiguration().apply {
            filter = PHPickerFilter.imagesFilter
            selectionLimit = 1
        }
        val picker = PHPickerViewController(configuration)
        val delegate = GalleryDelegate { image ->
            galleryDelegate = null
            deliver(image, ImageOrigin.SCREENSHOT)
        }
        galleryDelegate = delegate
        picker.delegate = delegate
        present(picker)
    }

    override fun takePhoto() {
        if (!UIImagePickerController.isSourceTypeAvailable(UIImagePickerControllerSourceType.UIImagePickerControllerSourceTypeCamera)) {
            onResult(null)
            return
        }
        val picker = UIImagePickerController()
        picker.sourceType = UIImagePickerControllerSourceType.UIImagePickerControllerSourceTypeCamera
        val delegate = CameraDelegate { image ->
            cameraDelegate = null
            deliver(image, ImageOrigin.PHOTO)
        }
        cameraDelegate = delegate
        picker.delegate = delegate
        present(picker)
    }

    private fun deliver(result: PickerOutcome, origin: ImageOrigin) {
        when (result) {
            PickerOutcome.Cancelled -> onCancel()
            is PickerOutcome.Picked -> onResult(result.image?.toPickedImage(origin))
        }
    }

    private fun present(controller: UIViewController) {
        val top = topViewController()
        if (top == null) onResult(null) else top.presentViewController(controller, animated = true, completion = null)
    }
}

/** What a picker returned. */
private sealed interface PickerOutcome {
    /** The user closed the picker. */
    data object Cancelled : PickerOutcome

    /**
     * The user chose an image, null when it could not be read.
     *
     * @property image the image.
     */
    class Picked(val image: UIImage?) : PickerOutcome
}

private class GalleryDelegate(private val onDone: (PickerOutcome) -> Unit) : NSObject(), PHPickerViewControllerDelegateProtocol {
    override fun picker(picker: PHPickerViewController, didFinishPicking: List<*>) {
        picker.dismissViewControllerAnimated(true, completion = null)
        val result = didFinishPicking.firstOrNull() as? PHPickerResult
        if (result == null) {
            onDone(PickerOutcome.Cancelled)
            return
        }
        result.itemProvider.loadDataRepresentationForTypeIdentifier(IMAGE_TYPE_IDENTIFIER) { data, _ ->
            val image = data?.let { UIImage(data = it) }
            dispatch_async(dispatch_get_main_queue()) { onDone(PickerOutcome.Picked(image)) }
        }
    }
}

/** Uniform type identifier of every image format. */
private const val IMAGE_TYPE_IDENTIFIER = "public.image"

private class CameraDelegate(private val onDone: (PickerOutcome) -> Unit) :
    NSObject(), UIImagePickerControllerDelegateProtocol, UINavigationControllerDelegateProtocol {
    override fun imagePickerController(picker: UIImagePickerController, didFinishPickingMediaWithInfo: Map<Any?, *>) {
        picker.dismissViewControllerAnimated(true, completion = null)
        onDone(PickerOutcome.Picked(didFinishPickingMediaWithInfo[UIImagePickerControllerOriginalImage] as? UIImage))
    }

    override fun imagePickerControllerDidCancel(picker: UIImagePickerController) {
        picker.dismissViewControllerAnimated(true, completion = null)
        onDone(PickerOutcome.Cancelled)
    }
}

private fun topViewController(): UIViewController? {
    var controller = UIApplication.sharedApplication.keyWindow?.rootViewController
    while (controller?.presentedViewController != null) controller = controller.presentedViewController
    return controller
}

/** Reduces the image to [MAXIMUM_IMAGE_SIDE] points on its longest side and encodes it as JPEG. */
@OptIn(ExperimentalForeignApi::class)
private fun UIImage.toPickedImage(origin: ImageOrigin): PickedImage? {
    val (width, height) = size.useContents { width to height }
    if (width <= 0.0 || height <= 0.0) return null
    val ratio = minOf(1.0, MAXIMUM_IMAGE_SIDE / maxOf(width, height))
    val targetWidth = width * ratio
    val targetHeight = height * ratio
    UIGraphicsBeginImageContextWithOptions(CGSizeMake(targetWidth, targetHeight), true, 1.0)
    drawInRect(CGRectMake(0.0, 0.0, targetWidth, targetHeight))
    val scaled = UIGraphicsGetImageFromCurrentImageContext()
    UIGraphicsEndImageContext()
    val data = UIImageJPEGRepresentation(scaled ?: this, JPEG_QUALITY / 100.0) ?: return null
    return PickedImage(data.toByteArray(), "image/jpeg", origin)
}

@OptIn(ExperimentalForeignApi::class)
private fun NSData.toByteArray(): ByteArray {
    val result = ByteArray(length.toInt())
    if (result.isNotEmpty()) result.usePinned { memcpy(it.addressOf(0), bytes, length) }
    return result
}
