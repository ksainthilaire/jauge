package io.jauge.core.textrecognition

import io.jauge.core.domain.error.JaugeError
import io.jauge.core.domain.model.Outcome
import io.jauge.core.domain.service.TextRecognizer
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext
import platform.Foundation.NSData
import platform.Foundation.create
import platform.Vision.VNImageRequestHandler
import platform.Vision.VNRecognizeTextRequest
import platform.Vision.VNRecognizedText
import platform.Vision.VNRecognizedTextObservation
import platform.Vision.VNRequestTextRecognitionLevelAccurate

/**
 * [TextRecognizer] of iOS: Apple Vision (`VNRecognizeTextRequest`, accurate level, French then English). It runs on
 * the phone and works offline: the image is never sent anywhere. Vision reads the orientation of the image itself.
 *
 * @param dispatcher where the synchronous Vision request runs.
 */
@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
internal class VisionTextRecognizer(
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) : TextRecognizer {

    override suspend fun recognize(image: ByteArray, mimeType: String): Outcome<String> = try {
        if (image.isEmpty()) {
            Outcome.Failure(JaugeError.UnreadableOffer())
        } else {
            withContext(dispatcher) { read(image) }
        }
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (failure: Exception) {
        Outcome.Failure(JaugeError.UnreadableOffer())
    }

    private fun read(image: ByteArray): Outcome<String> {
        val request = VNRecognizeTextRequest().apply {
            recognitionLevel = VNRequestTextRecognitionLevelAccurate
            usesLanguageCorrection = true
            recognitionLanguages = RECOGNITION_LANGUAGES
        }
        val handler = VNImageRequestHandler(data = image.toNSData(), options = emptyMap<Any?, Any?>())
        if (!handler.performRequests(listOf(request), error = null)) return Outcome.Failure(JaugeError.UnreadableOffer())
        val lines = request.results.orEmpty()
            .filterIsInstance<VNRecognizedTextObservation>()
            .mapNotNull { observation -> (observation.topCandidates(1u).firstOrNull() as? VNRecognizedText)?.string }
        return Outcome.Success(lines.joinToString("\n"))
    }

    private fun ByteArray.toNSData(): NSData = usePinned { pinned ->
        NSData.create(bytes = pinned.addressOf(0), length = size.toULong())
    }

    private companion object {
        /** Offers are in French, sometimes in English. */
        val RECOGNITION_LANGUAGES = listOf("fr-FR", "en-US")
    }
}
