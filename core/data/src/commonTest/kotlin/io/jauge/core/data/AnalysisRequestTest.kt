package io.jauge.core.data

import io.jauge.core.data.offline.OfflineDataset
import io.jauge.core.data.offline.OfflineJaugeApi
import io.jauge.core.data.offline.OfflineScenarioController
import io.jauge.core.data.repository.AnalysisRepositoryImplementation
import io.jauge.core.domain.error.JaugeError
import io.jauge.core.domain.model.AnalysisProgress
import io.jauge.core.domain.model.ImageOrigin
import io.jauge.core.domain.model.OfferSource
import io.jauge.core.network.payload.CreateAnalysisRequestPayload
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AnalysisRequestTest {
    private val scenarios = OfflineScenarioController(latencyMilliseconds = OfflineScenarioController.NO_LATENCY)
    private val api = RecordingJaugeApi(OfflineJaugeApi(OfflineDataset(), scenarios))
    private val analyses = AnalysisRepositoryImplementation(api, pollIntervalMilliseconds = 0)

    @Test
    fun theTextOfACaptureIsSentWithTheTextType() = runTest {
        val capture = OfferSource.Text("Chargé(e) de marketing digital, Maison Odier, Lyon 3e, CDI", ImageOrigin.SCREENSHOT)
        assertIs<AnalysisProgress.Completed>(analyses.analyze(capture).toList().last())
        val request = api.createdAnalyses.single()
        assertEquals(CreateAnalysisRequestPayload.TYPE_TEXT, request.type)
        assertEquals(capture.text, request.text)
        assertEquals("screenshot", request.origin)
        assertNull(request.url)
    }

    @Test
    fun anImageIsNeverUploaded() = runTest {
        val image = OfferSource.Image(byteArrayOf(1, 2, 3), "image/jpeg", ImageOrigin.PHOTO)
        assertEquals(listOf<AnalysisProgress>(AnalysisProgress.Failed(JaugeError.UnreadableOffer())), analyses.analyze(image).toList())
        assertTrue(api.createdAnalyses.isEmpty())
    }
}
