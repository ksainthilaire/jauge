package io.jauge.core.domain

import io.jauge.core.domain.error.JaugeError
import io.jauge.core.domain.model.AnalysisProgress
import io.jauge.core.domain.model.ImageOrigin
import io.jauge.core.domain.model.OfferSource
import io.jauge.core.domain.model.Outcome
import io.jauge.core.domain.service.RecognizedText
import io.jauge.core.domain.service.TextRecognizer
import io.jauge.core.domain.usecase.AnalyzeOfferUseCase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** Text of a sample offer, long enough for the API. */
const val SAMPLE_OFFER_TEXT = "Chargé(e) de marketing digital\nMaison Odier\nLyon 3e, CDI\n38 à 42 k€ brut par an"

class TextRecognitionUseCaseTest {
    private val repository = FakeAnalysisRepository()
    private val image = OfferSource.Image(byteArrayOf(1, 2, 3), "image/jpeg", ImageOrigin.PHOTO)

    @Test
    fun imagesAreReadOnThePhoneAndOnlyTheirTextIsSent() = runTest {
        val readImages = mutableListOf<ByteArray>()
        val useCase = AnalyzeOfferUseCase(repository) { bytes, _ ->
            readImages += bytes
            Outcome.Success("  Chargé(e) de marketing digital  \n\n\n\nMaison   Odier\r\nLyon 3e, CDI, 38 à 42 k€ brut par an ")
        }
        assertIs<AnalysisProgress.Reading>(useCase(image).first())
        assertEquals(1, readImages.size)
        val sent = assertIs<OfferSource.Text>(repository.receivedSources.single())
        assertEquals("Chargé(e) de marketing digital\n\nMaison Odier\nLyon 3e, CDI, 38 à 42 k€ brut par an", sent.text)
        assertEquals(ImageOrigin.PHOTO, sent.origin)
        assertTrue(repository.receivedSources.none { it is OfferSource.Image })
    }

    @Test
    fun anEmptyOrShortTextIsUnreadableWithoutCallingTheServer() = runTest {
        listOf("", "   ", "Maison Odier", "- - - - - - - - - - - - - - - - - - - - - - - -").forEach { text ->
            val useCase = AnalyzeOfferUseCase(repository) { _, _ -> Outcome.Success(text) }
            assertEquals(AnalysisProgress.Failed(JaugeError.UnreadableOffer()), useCase(image).first(), text)
        }
        assertTrue(repository.receivedSources.isEmpty())
    }

    @Test
    fun aRecognitionFailureIsAnUnreadableOffer() = runTest {
        val useCase = AnalyzeOfferUseCase(repository) { _, _ -> Outcome.Failure(JaugeError.Unknown) }
        assertEquals(AnalysisProgress.Failed(JaugeError.UnreadableOffer()), useCase(image).first())
        assertTrue(repository.receivedSources.isEmpty())
    }

    @Test
    fun longTextsAreCutToTheLimitOfTheApi() = runTest {
        val useCase = AnalyzeOfferUseCase(repository) { _, _ -> Outcome.Success("Développeur Kotlin ".repeat(2_000)) }
        useCase(image).first()
        val sent = assertIs<OfferSource.Text>(repository.receivedSources.single())
        assertTrue(sent.text.length <= RecognizedText.MAXIMUM_LENGTH)
    }

    @Test
    fun readabilityNeedsLettersOrDigits() {
        assertTrue(RecognizedText.isReadable(RecognizedText.clean(SAMPLE_OFFER_TEXT)))
        assertFalse(RecognizedText.isReadable(RecognizedText.clean("....................................................")))
    }
}
