package io.jauge.core.data

import io.jauge.core.data.offline.OfflineDataset
import io.jauge.core.data.offline.OfflineJaugeApi
import io.jauge.core.data.offline.OfflineScenarioController
import io.jauge.core.data.preferences.InMemoryKeyValueStore
import io.jauge.core.data.preferences.PreferencesRepositoryImplementation
import io.jauge.core.data.preferences.SearchAreaSource
import io.jauge.core.data.repository.AnalysisRepositoryImplementation
import io.jauge.core.data.repository.PollingSchedule
import io.jauge.core.data.repository.InsightRepositoryImplementation
import io.jauge.core.data.repository.TwinRepositoryImplementation
import io.jauge.core.domain.model.AnalysisProgress
import io.jauge.core.domain.model.OfferSource
import io.jauge.core.domain.model.SearchArea
import io.jauge.core.domain.model.SearchAreaOrigin
import io.jauge.core.domain.model.TwinSort
import io.jauge.core.domain.model.getOrNull
import io.jauge.core.network.payload.AreaPayload
import io.jauge.core.network.payload.CreateAnalysisRequestPayload
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.last
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The search area: kept on the device, sent to the API (city and department only) and followed by the offline API. */
class SearchAreaTest {
    private val scenarios = OfflineScenarioController(latencyMilliseconds = OfflineScenarioController.NO_LATENCY)
    private val api = OfflineJaugeApi(OfflineDataset(), scenarios)
    private val nantes = SearchArea("Nantes", 10, "44", SearchAreaOrigin.DEVICE)

    @Test
    fun areaAndLocationAnswerSurviveARestart() = runTest {
        val store = InMemoryKeyValueStore()
        PreferencesRepositoryImplementation(store).update { it.copy(searchArea = nantes, hasAnsweredLocation = true) }
        val restarted = PreferencesRepositoryImplementation(store).observePreferences().first()
        assertEquals(nantes, restarted.searchArea)
        assertTrue(restarted.hasAnsweredLocation)
    }

    @Test
    fun areaSavedBeforeTheOriginExistedIsATypedCity() = runTest {
        val store = InMemoryKeyValueStore()
        store.putString("settings.searchArea.city", "Lyon")
        store.putString("settings.searchArea.radius", "30")
        assertEquals(SearchArea("Lyon", 30, null, SearchAreaOrigin.MANUAL), PreferencesRepositoryImplementation(store).observePreferences().first().searchArea)
    }

    @Test
    fun withoutAreaPreferencesGiveParis() = runTest {
        assertEquals(SearchArea.DEFAULT, SearchAreaSource.fromPreferences(PreferencesRepositoryImplementation(InMemoryKeyValueStore())).current())
    }

    @Test
    fun twinsAndSalaryFollowTheArea() = runTest {
        val twins = TwinRepositoryImplementation(api) { nantes }.getTwinOffers(OfflineDataset.MARKETING_ID, TwinSort.LEAST_DISPUTED).getOrNull()!!
        assertEquals(10, twins.radiusKilometers)
        assertTrue(twins.offers.all { it.place == "Nantes" && it.distanceKilometers <= 10 })
        val salary = InsightRepositoryImplementation(api) { nantes }.getSalary(OfflineDataset.MARKETING_ID).getOrNull()!!
        assertEquals("Nantes", salary.areaName)
    }

    @Test
    fun newAnalysisUsesTheRadiusOfTheArea() = runTest {
        val analyses = AnalysisRepositoryImplementation(api, polling = PollingSchedule.IMMEDIATE, searchArea = { nantes })
        val completed = analyses.analyze(OfferSource.Link("https://example.com/offre"), "request-key-0001").last()
        assertIs<AnalysisProgress.Completed>(completed)
        assertEquals(10, completed.analysis.twinRadiusKilometers)
    }

    @Test
    fun requestCarriesOnlyTheCityTheDepartmentAndTheRadius() {
        val body = io.jauge.core.network.JaugeJson.encodeToString(
            CreateAnalysisRequestPayload.serializer(),
            CreateAnalysisRequestPayload(type = "link", url = "https://example.com", area = AreaPayload("Nantes", "44", 10)),
        )
        assertTrue(body.contains("\"area\":{\"city\":\"Nantes\",\"departmentCode\":\"44\",\"radiusKilometers\":10}"))
        assertTrue(!body.contains("latitude") && !body.contains("longitude"))
    }

    @Test
    fun noAreaSendsNothing() = runTest {
        assertNull(SearchAreaSource.NONE.current())
    }
}
