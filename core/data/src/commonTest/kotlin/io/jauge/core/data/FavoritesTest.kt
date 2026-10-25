package io.jauge.core.data

import io.jauge.core.data.mapper.toDomainOrNull
import io.jauge.core.data.offline.OfflineDataset
import io.jauge.core.data.offline.OfflineJaugeApi
import io.jauge.core.data.offline.OfflineScenario
import io.jauge.core.data.offline.OfflineScenarioController
import io.jauge.core.data.preferences.FavoriteStore
import io.jauge.core.data.preferences.InMemoryKeyValueStore
import io.jauge.core.data.repository.AccountRepositoryImplementation
import io.jauge.core.data.repository.AnalysisRepositoryImplementation
import io.jauge.core.data.repository.PollingSchedule
import io.jauge.core.domain.error.JaugeError
import io.jauge.core.domain.model.Outcome
import io.jauge.core.domain.model.getOrNull
import io.jauge.core.network.payload.AnalysisSummaryPayload
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** Favorites: optimistic change, undo on failure, local persistence and offline API. */
class FavoritesTest {
    private val scenarios = OfflineScenarioController(latencyMilliseconds = OfflineScenarioController.NO_LATENCY)
    private val deviceStore = InMemoryKeyValueStore()
    private val api = OfflineJaugeApi(OfflineDataset(), scenarios, favoriteStore = FavoriteStore(deviceStore, FavoriteStore.OFFLINE_KEY))

    private fun repository() = AnalysisRepositoryImplementation(api, FavoriteStore(deviceStore), polling = PollingSchedule.IMMEDIATE)

    @Test
    fun starShowsInTheHistoryAndTheAnalysis() = runTest {
        val analyses = repository()
        analyses.refreshHistory()
        assertEquals(Outcome.Success(Unit), analyses.setFavorite(OfflineDataset.TRAFFIC_ID, true))
        assertTrue(analyses.observeHistory().first().first { it.id == OfflineDataset.TRAFFIC_ID }.isFavorite)
        assertTrue(analyses.getAnalysis(OfflineDataset.TRAFFIC_ID).getOrNull()!!.isFavorite)
        assertEquals(setOf(OfflineDataset.TRAFFIC_ID), analyses.observeFavoriteIds().first())
    }

    @Test
    fun refusedStarIsUndone() = runTest {
        val analyses = repository()
        analyses.refreshHistory()
        scenarios.select(OfflineScenario.NO_CONNECTION)
        val outcome = analyses.setFavorite(OfflineDataset.CRM_ID, true)
        assertIs<Outcome.Failure>(outcome)
        assertEquals(JaugeError.NoConnection, outcome.error)
        assertFalse(analyses.observeHistory().first().first { it.id == OfflineDataset.CRM_ID }.isFavorite)
        assertEquals(emptySet(), analyses.observeFavoriteIds().first())
    }

    @Test
    fun starsSurviveARestartAndFollowTheServer() = runTest {
        repository().setFavorite(OfflineDataset.MARKETING_ID, true)
        val restarted = repository()
        assertEquals(setOf(OfflineDataset.MARKETING_ID), restarted.observeFavoriteIds().first())

        // The server forgot the star (removed on another device): the refresh follows the server.
        api.removeFavorite(OfflineDataset.MARKETING_ID)
        restarted.refreshHistory()
        assertEquals(emptySet(), restarted.observeFavoriteIds().first())
    }

    @Test
    fun deletedAnalysisLeavesTheFavorites() = runTest {
        val analyses = repository()
        analyses.refreshHistory()
        analyses.setFavorite(OfflineDataset.TRAFFIC_ID, true)
        analyses.deleteAnalysis(OfflineDataset.TRAFFIC_ID)
        assertEquals(emptySet(), analyses.observeFavoriteIds().first())
    }

    @Test
    fun sameValueDoesNotCallTheServer() = runTest {
        val analyses = repository()
        scenarios.select(OfflineScenario.NO_CONNECTION)
        assertEquals(Outcome.Success(Unit), analyses.setFavorite(OfflineDataset.TRAFFIC_ID, false))
    }

    @Test
    fun favoriteStoreIgnoresUnreadableValues() {
        val store = InMemoryKeyValueStore()
        store.putString(FavoriteStore.KEY, "\n  a1 \n\n")
        assertEquals(setOf("a1"), FavoriteStore(store).read())
        FavoriteStore(store).write(emptySet())
        assertEquals(null, store.getString(FavoriteStore.KEY))
    }

    @Test
    fun summaryPayloadKeepsTheStar() {
        val summary = AnalysisSummaryPayload("a1", "2026-10-23T08:00:00Z", "Responsable CRM", null, "Lyon", 19, isFavorite = true)
        assertTrue(summary.toDomainOrNull()!!.isFavorite)
        assertFalse(summary.copy(isFavorite = false).toDomainOrNull()!!.isFavorite)
    }

    @Test
    fun deletedAccountForgetsTheStarsOfTheDevice() = runTest {
        val analyses = repository()
        analyses.setFavorite(OfflineDataset.TRAFFIC_ID, true)
        val account = AccountRepositoryImplementation(api) { analyses.clearLocalFavorites() }
        account.deleteAccount()
        assertEquals(emptySet(), analyses.observeFavoriteIds().first())
        assertEquals(null, deviceStore.getString(FavoriteStore.KEY))
    }
}
