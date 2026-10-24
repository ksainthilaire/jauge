package io.jauge.core.data

import io.jauge.core.billing.SimulatedBillingClient
import io.jauge.core.billing.UnavailableBillingClient
import io.jauge.core.data.offline.OfflineDataset
import io.jauge.core.data.offline.OfflineJaugeApi
import io.jauge.core.data.offline.OfflineScenario
import io.jauge.core.data.offline.OfflineScenarioController
import io.jauge.core.data.repository.AnalysisRepositoryImplementation
import io.jauge.core.data.repository.InsightRepositoryImplementation
import io.jauge.core.data.repository.ShareRepositoryImplementation
import io.jauge.core.data.repository.SubscriptionRepositoryImplementation
import io.jauge.core.data.repository.TwinRepositoryImplementation
import io.jauge.core.domain.error.JaugeError
import io.jauge.core.domain.model.AnalysisProgress
import io.jauge.core.domain.model.CompetitionLevel
import io.jauge.core.domain.model.ImageOrigin
import io.jauge.core.domain.model.OfferSource
import io.jauge.core.domain.model.Outcome
import io.jauge.core.domain.model.ShareOptions
import io.jauge.core.domain.model.SubscriptionOffer
import io.jauge.core.domain.model.SubscriptionStatus
import io.jauge.core.domain.model.TwinSort
import io.jauge.core.domain.model.getOrNull
import io.jauge.core.domain.usecase.CreateShareUseCase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class OfflineModeTest {

    private val scenarios = OfflineScenarioController(latencyMilliseconds = OfflineScenarioController.NO_LATENCY)
    private val api = OfflineJaugeApi(OfflineDataset(), scenarios)
    private val analyses = AnalysisRepositoryImplementation(api, pollIntervalMilliseconds = 0)

    @Test
    fun historyShowsTheThreeMockupOffers() = runTest {
        assertEquals(Outcome.Success(Unit), analyses.refreshHistory())
        val history = analyses.observeHistory().first()
        assertEquals(listOf(78, 44, 19), history.map { it.score })
        assertEquals(listOf(CompetitionLevel.CROWDED, CompetitionLevel.DISPUTED, CompetitionLevel.CALM), history.map { it.level })
        assertEquals("Atelier Sorel", history.first().company)
    }

    @Test
    fun verdictMatchesMockup04() = runTest {
        val analysis = analyses.getAnalysis(OfflineDataset.MARKETING_ID).getOrNull()
        assertNotNull(analysis)
        assertEquals(78, analysis.score)
        assertEquals(84, analysis.percentileInRegion)
        assertEquals(210, analysis.applicants.estimate)
        assertEquals(160, analysis.applicants.low)
        assertEquals(260, analysis.applicants.high)
        assertEquals(36, analysis.estimatedSalary?.minimumThousands)
        assertEquals(41, analysis.estimatedSalary?.maximumThousands)
        assertEquals(46, analysis.twinOfferCount)
        assertEquals(18, analysis.trendThreeMonthsPercent)
        assertEquals(null, analysis.offer.displayedSalary)
    }

    @Test
    fun linkAnalysisProgressesThenCompletesAndJoinsTheHistory() = runTest {
        val progress = analyses.analyze(OfferSource.Link("https://emplois-rhone.fr/offre/41872")).toList()
        val readings = progress.filterIsInstance<AnalysisProgress.Reading>()
        assertEquals(listOf(20, 40, 62, 85), readings.map { it.percent })
        val completed = assertIs<AnalysisProgress.Completed>(progress.last())
        assertEquals("https://emplois-rhone.fr/offre/41872", completed.analysis.offer.sourceUrl)
        assertTrue(analyses.observeHistory().first().any { it.id == completed.analysis.id })
    }

    @Test
    fun unreadableScreenshotFailsWithTheRecognizedCompany() = runTest {
        scenarios.select(OfflineScenario.UNREADABLE_OFFER)
        val image = OfferSource.Image(byteArrayOf(1, 2, 3), "image/png", ImageOrigin.SCREENSHOT)
        val last = analyses.analyze(image).toList().last()
        assertEquals(AnalysisProgress.Failed(JaugeError.UnreadableOffer(OfflineDataset.UNREADABLE_COMPANY)), last)
    }

    @Test
    fun simulatedFailuresReachTheDomainAsFriendlyErrors() = runTest {
        val cases = mapOf(
            OfflineScenario.NO_CONNECTION to JaugeError.NoConnection,
            OfflineScenario.TIMEOUT to JaugeError.Timeout,
            OfflineScenario.SERVER_UNAVAILABLE to JaugeError.ServerUnavailable,
            OfflineScenario.SESSION_EXPIRED to JaugeError.SessionExpired,
            OfflineScenario.INVALID_RESPONSE to JaugeError.InvalidData,
        )
        cases.forEach { (scenario, error) ->
            scenarios.select(scenario)
            assertEquals(Outcome.Failure(error), analyses.refreshHistory(), scenario.name)
        }
        scenarios.select(OfflineScenario.QUOTA_REACHED)
        assertEquals(AnalysisProgress.Failed(JaugeError.QuotaReached), analyses.analyze(OfferSource.Link("https://a.fr/b")).first())
    }

    @Test
    fun emptyScenarioGivesEmptyLists() = runTest {
        scenarios.select(OfflineScenario.EMPTY)
        analyses.refreshHistory()
        assertTrue(analyses.observeHistory().first().isEmpty())
        val twins = TwinRepositoryImplementation(api).getTwinOffers(OfflineDataset.MARKETING_ID, TwinSort.LEAST_DISPUTED).getOrNull()
        assertTrue(twins!!.offers.isEmpty())
    }

    @Test
    fun detailsAndTwinsMatchMockups05And06() = runTest {
        val insights = InsightRepositoryImplementation(api)
        val salary = insights.getSalary(OfflineDataset.MARKETING_ID).getOrNull()!!
        assertEquals(37, salary.medianThousands)
        assertEquals(40, salary.negotiationTargetThousands)
        assertEquals(412, salary.offerCount)
        val skills = insights.getSkills(OfflineDataset.MARKETING_ID).getOrNull()!!
        assertEquals(false, skills.skills.first { it.name == "Looker Studio" }.isOwned)
        val trend = insights.getTrend(OfflineDataset.MARKETING_ID).getOrNull()!!
        assertEquals(12, trend.points.size)
        val twins = TwinRepositoryImplementation(api).getTwinOffers(OfflineDataset.MARKETING_ID, TwinSort.LEAST_DISPUTED).getOrNull()!!
        assertEquals("Coopérative Brune", twins.offers.first().company)
        assertEquals(CompetitionLevel.CALM, twins.offers.first().level)
    }

    @Test
    fun unknownAnalysisIsNotFound() = runTest {
        assertEquals(Outcome.Failure(JaugeError.NotFound), analyses.getAnalysis("missing"))
    }

    @Test
    fun sharesUseTheJaugeLink() = runTest {
        val share = CreateShareUseCase(ShareRepositoryImplementation(api))(OfflineDataset.MARKETING_ID, ShareOptions(hideCompany = true)).getOrNull()
        assertEquals("https://jauge.io/a/demo-marketing-digital", share?.url)
        assertEquals(true, share?.options?.hideCompany)
    }

    @Test
    fun alertsAreCachedAndObserved() = runTest {
        val twins = TwinRepositoryImplementation(api)
        val created = twins.createAlert(OfflineDataset.MARKETING_ID, 30).getOrNull()!!
        assertEquals(listOf(created), twins.observeAlerts().first())
        twins.deleteAlert(created.id)
        assertTrue(twins.observeAlerts().first().isEmpty())
    }

    @Test
    fun simulatedPurchaseStartsTheTrial() = runTest {
        val billing = SimulatedBillingClient(latencyMilliseconds = 0) { 1_800_000_000_000 }
        val subscriptions = SubscriptionRepositoryImplementation(api, billing)
        assertEquals(SubscriptionStatus.FREE, subscriptions.getSubscription().getOrNull()?.status)
        val offer = subscriptions.getOffer().getOrNull()!!
        assertEquals(SubscriptionOffer.MONTHLY_PRODUCT_ID, offer.productId)
        assertEquals(3, offer.trialDays)
        assertEquals(SubscriptionStatus.TRIAL, subscriptions.purchase(offer).getOrNull()?.status)
        billing.cancelNextPurchase = true
        assertEquals(Outcome.Failure(JaugeError.PurchaseCancelled), subscriptions.purchase(offer))
    }

    @Test
    fun purchasesAreDisabledWithoutKeys() = runTest {
        val subscriptions = SubscriptionRepositoryImplementation(api, UnavailableBillingClient())
        assertEquals(Outcome.Failure(JaugeError.PurchaseUnavailable), subscriptions.getOffer())
        assertEquals(SubscriptionStatus.FREE, subscriptions.getSubscription().getOrNull()?.status)
    }
}
