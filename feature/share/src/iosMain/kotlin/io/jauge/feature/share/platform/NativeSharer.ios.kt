package io.jauge.feature.share.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asSkiaBitmap
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Image
import platform.Foundation.NSData
import platform.Foundation.dataWithBytes
import platform.MessageUI.MFMessageComposeViewController
import platform.MessageUI.MFMessageComposeViewControllerDelegateProtocol
import platform.MessageUI.MessageComposeResult
import platform.UIKit.UIActivityViewController
import platform.UIKit.UIApplication
import platform.UIKit.UIImage
import platform.UIKit.UIImageWriteToSavedPhotosAlbum
import platform.UIKit.UIPasteboard
import platform.UIKit.UIViewController
import platform.UIKit.popoverPresentationController
import platform.darwin.NSObject

/** iOS implementation: UIActivityViewController, MFMessageComposeViewController and the photo album. */
@Composable
actual fun rememberNativeSharer(): NativeSharer = remember { IosNativeSharer() }

/** Encodes the bitmap as PNG with Skia. */
actual fun ImageBitmap.encodeToPng(): ByteArray =
    Image.makeFromBitmap(asSkiaBitmap()).encodeToData(EncodedImageFormat.PNG)?.bytes ?: ByteArray(0)

/** Shares with the iOS view controllers, presented on top of the current screen. */
@OptIn(ExperimentalForeignApi::class)
class IosNativeSharer : NativeSharer {

    override fun copyText(text: String) {
        UIPasteboard.generalPasteboard.string = text
    }

    override fun shareText(text: String): Boolean = present(listOf(text))

    override fun shareImage(png: ByteArray, text: String): Boolean {
        val image = png.toImage() ?: return shareText(text)
        return present(listOf(image, text))
    }

    override fun sendMessage(text: String, png: ByteArray?): Boolean {
        if (!MFMessageComposeViewController.canSendText()) {
            return if (png != null) shareImage(png, text) else shareText(text)
        }
        val presenter = topViewController() ?: return false
        val composer = MFMessageComposeViewController()
        composer.setBody(text)
        if (png != null && MFMessageComposeViewController.canSendAttachments()) {
            png.toData()?.let { composer.addAttachmentData(it, typeIdentifier = "public.png", filename = "jauge.png") }
        }
        composer.messageComposeDelegate = MessageComposeDismisser
        presenter.presentViewController(composer, animated = true, completion = null)
        return true
    }

    override suspend fun saveImage(png: ByteArray, fileName: String): Boolean {
        val image = png.toImage() ?: return false
        UIImageWriteToSavedPhotosAlbum(image, null, null, null)
        return true
    }

    private fun present(items: List<Any>): Boolean {
        val presenter = topViewController() ?: return false
        val controller = UIActivityViewController(activityItems = items, applicationActivities = null)
        controller.popoverPresentationController?.sourceView = presenter.view
        presenter.presentViewController(controller, animated = true, completion = null)
        return true
    }

    private fun topViewController(): UIViewController? {
        var controller = UIApplication.sharedApplication.keyWindow?.rootViewController
        while (controller?.presentedViewController != null) {
            controller = controller.presentedViewController
        }
        return controller
    }

    private fun ByteArray.toData(): NSData? {
        if (isEmpty()) return null
        return usePinned { pinned -> NSData.dataWithBytes(pinned.addressOf(0), size.toULong()) }
    }

    private fun ByteArray.toImage(): UIImage? = toData()?.let { UIImage.imageWithData(it) }
}

/** Closes the message composer when the user sends or cancels. Kept as an object so that it is never collected. */
private object MessageComposeDismisser : NSObject(), MFMessageComposeViewControllerDelegateProtocol {
    override fun messageComposeViewController(controller: MFMessageComposeViewController, didFinishWithResult: MessageComposeResult) {
        controller.dismissViewControllerAnimated(true, completion = null)
    }
}
