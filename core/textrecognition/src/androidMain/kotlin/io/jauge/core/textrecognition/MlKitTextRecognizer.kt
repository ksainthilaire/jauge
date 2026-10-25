package io.jauge.core.textrecognition

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.google.android.gms.tasks.Task
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import io.jauge.core.domain.error.JaugeError
import io.jauge.core.domain.model.Outcome
import io.jauge.core.domain.service.TextRecognizer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * [TextRecognizer] of Android: ML Kit Text Recognition with the Latin model bundled in the app
 * (`com.google.mlkit:text-recognition`). It runs on the phone, works offline and needs no Google service: the image
 * is never sent anywhere.
 *
 * Photos are re-encoded by the picker without their orientation tag, so a photo taken sideways reaches ML Kit turned
 * by a quarter: when the upright reading finds little text, the image is also read turned by 90 and 270 degrees and
 * the longest text wins.
 *
 * @param dispatcher where the image is decoded.
 */
internal class MlKitTextRecognizer(
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
) : TextRecognizer {

    private val client by lazy { TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS) }

    override suspend fun recognize(image: ByteArray, mimeType: String): Outcome<String> = try {
        val bitmap = withContext(dispatcher) { decode(image) }
        if (bitmap == null) {
            Outcome.Failure(JaugeError.UnreadableOffer())
        } else {
            var best = read(bitmap, rotationDegrees = 0)
            if (best.length < ENOUGH_CHARACTERS) {
                for (rotation in SIDEWAYS_ROTATIONS) {
                    val turned = read(bitmap, rotation)
                    if (turned.length > best.length) best = turned
                }
            }
            Outcome.Success(best)
        }
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (failure: Exception) {
        Outcome.Failure(JaugeError.UnreadableOffer())
    } catch (memory: OutOfMemoryError) {
        Outcome.Failure(JaugeError.UnreadableOffer())
    }

    private suspend fun read(bitmap: Bitmap, rotationDegrees: Int): String =
        client.process(InputImage.fromBitmap(bitmap, rotationDegrees)).await().text

    /** Decodes the image, reduced so that its longest side stays under [MAXIMUM_SIDE] pixels. */
    private fun decode(image: ByteArray): Bitmap? {
        if (image.isEmpty()) return null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(image, 0, image.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sampleSize = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / sampleSize > MAXIMUM_SIDE) sampleSize *= 2
        val options = BitmapFactory.Options().apply { inSampleSize = sampleSize }
        return BitmapFactory.decodeByteArray(image, 0, image.size, options)
    }

    private companion object {
        /** Below this length the upright reading is probably wrong (sideways photo). */
        const val ENOUGH_CHARACTERS = 80

        /** Quarter turns tried for sideways photos. */
        val SIDEWAYS_ROTATIONS = listOf(90, 270)

        /** Longest side given to ML Kit, enough for the small text of a screenshot. */
        const val MAXIMUM_SIDE = 4096
    }
}

/** Waits for a Google Play services [Task] without blocking. */
private suspend fun <T> Task<T>.await(): T = suspendCancellableCoroutine { continuation ->
    addOnSuccessListener { result -> continuation.resume(result) }
    addOnFailureListener { failure -> continuation.resumeWithException(failure) }
    addOnCanceledListener { continuation.cancel() }
}
