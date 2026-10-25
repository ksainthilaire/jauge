package io.jauge.core.data

import io.jauge.core.data.offline.OfflineDataset
import io.jauge.core.data.offline.OfflineJaugeApi
import io.jauge.core.data.offline.OfflineScenarioController
import io.jauge.core.data.repository.AnalysisRepositoryImplementation
import io.jauge.core.data.repository.PollingSchedule
import io.jauge.core.domain.error.JaugeError
import io.jauge.core.domain.model.AnalysisProgress
import io.jauge.core.domain.model.ImageOrigin
import io.jauge.core.domain.model.OfferSource
import io.jauge.core.network.payload.CreateAnalysisRequestPayload
import kotlinx.coroutines.flow.first
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
    private val analyses = AnalysisRepositoryImplementation(api, polling = PollingSchedule.IMMEDIATE)

    @Test
    fun theTextOfACaptureIsSentWithTheTextType() = runTest {
        val capture = OfferSource.Text("Chargé(e) de marketing digital, Maison Odier, Lyon 3e, CDI", ImageOrigin.SCREENSHOT)
        assertIs<AnalysisProgress.Completed>(analyses.analyze(capture, "request-key-0001").toList().last())
        val request = api.createdAnalyses.single()
        assertEquals(CreateAnalysisRequestPayload.TYPE_TEXT, request.type)
        assertEquals(capture.text, request.text)
        assertEquals("screenshot", request.origin)
        assertNull(request.url)
    }

    @Test
    fun anImageIsNeverUploaded() = runTest {
        val image = OfferSource.Image(byteArrayOf(1, 2, 3), "image/jpeg", ImageOrigin.PHOTO)
        assertEquals(listOf<AnalysisProgress>(AnalysisProgress.Failed(JaugeError.UnreadableOffer())), analyses.analyze(image, "request-key-0001").toList())
        assertTrue(api.createdAnalyses.isEmpty())
    }

    @Test
    fun theRequestKeyIsSentAndGivesBackTheSameAnalysis() = runTest {
        val link = OfferSource.Link("https://emplois-rhone.fr/offre/41872")
        val first = assertIs<AnalysisProgress.Reading>(analyses.analyze(link, "key-same-tap-0001").first()).analysisId
        val again = assertIs<AnalysisProgress.Reading>(analyses.analyze(link, "key-same-tap-0001").first()).analysisId
        assertEquals(listOf<String?>("key-same-tap-0001", "key-same-tap-0001"), api.idempotencyKeys)
        assertEquals(first, again)
        val other = assertIs<AnalysisProgress.Reading>(analyses.analyze(link, "key-other-tap-0002").first()).analysisId
        assertTrue(other != first)
    }

    @Test
    fun pollingSlowsDownAfterTenSeconds() {
        val schedule = PollingSchedule()
        assertEquals(List(10) { 1_000L }, (0 until 10).map(schedule::delayBeforePoll))
        assertEquals(2_000L, schedule.delayBeforePoll(10))
        assertEquals(2_000L, schedule.delayBeforePoll(49))
        val totalWait = (0 until schedule.maximumPolls).sumOf(schedule::delayBeforePoll)
        assertEquals(90_000L, totalWait, "the app still waits 90 seconds before the timeout message")
    }

    @Test
    fun pollingFollowsTheScheduleInVirtualTime() = runTest {
        val repository = AnalysisRepositoryImplementation(api, polling = PollingSchedule())
        val start = testScheduler.currentTime
        assertIs<AnalysisProgress.Completed>(repository.analyze(OfferSource.Link("https://a.fr/b"), "key-virtual-time-01").toList().last())
        // The offline analysis completes at its fourth poll: four quick polls of one second.
        assertEquals(4_000L, testScheduler.currentTime - start)
        assertEquals(4, api.polledAnalyses.size)
    }
}
