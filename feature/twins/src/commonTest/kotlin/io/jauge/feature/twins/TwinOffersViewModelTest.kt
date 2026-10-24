package io.jauge.feature.twins

import io.jauge.core.domain.error.JaugeError
import io.jauge.core.domain.model.CalmAlert
import io.jauge.core.domain.model.Outcome
import io.jauge.core.domain.model.TwinSort
import io.jauge.core.domain.usecase.CreateCalmAlertUseCase
import io.jauge.core.domain.usecase.DeleteCalmAlertUseCase
import io.jauge.core.domain.usecase.GetAnalysisHistoryUseCase
import io.jauge.core.domain.usecase.GetCalmAlertsUseCase
import io.jauge.core.domain.usecase.GetTwinOffersUseCase
import io.jauge.feature.twins.twinoffers.TwinOffersEvent
import io.jauge.feature.twins.twinoffers.TwinOffersViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class TwinOffersViewModelTest {

    private val twins = FakeTwinRepository()

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel(analysisId: String?, history: FakeHistoryRepository = FakeHistoryRepository(listOf(TwinsSampleData.latestAnalysis))) =
        TwinOffersViewModel(
            initialAnalysisId = analysisId,
            getTwinOffers = GetTwinOffersUseCase(twins),
            getAnalysisHistory = GetAnalysisHistoryUseCase(history),
            getCalmAlerts = GetCalmAlertsUseCase(twins),
            createCalmAlert = CreateCalmAlertUseCase(twins),
            deleteCalmAlert = DeleteCalmAlertUseCase(twins),
        )

    @Test
    fun loadsTheTwinsOfTheGivenAnalysis() = runTest {
        val viewModel = viewModel("analysis-42")

        assertEquals(listOf("analysis-42" to TwinSort.LEAST_DISPUTED), twins.requestedAnalyses)
        assertEquals(TwinsSampleData.twins.offers.map { it.id }, viewModel.uiState.value.twins.content?.offers?.map { it.id })
        assertFalse(viewModel.uiState.value.isAlertOn)
    }

    @Test
    fun theTabShowsTheTwinsOfTheMostRecentAnalysis() = runTest {
        val viewModel = viewModel(null)

        assertEquals("analysis-marketing", viewModel.uiState.value.analysisId)
        assertEquals("analysis-marketing", twins.requestedAnalyses.single().first)
    }

    @Test
    fun theTabShowsAnEmptyStateWithoutAnalysis() = runTest {
        val viewModel = viewModel(null, FakeHistoryRepository(emptyList()))

        assertTrue(viewModel.uiState.value.hasNoAnalysis)
        assertTrue(twins.requestedAnalyses.isEmpty())
        assertFalse(viewModel.uiState.value.twins.isLoading)
    }

    @Test
    fun changingTheSortReloadsTheList() = runTest {
        val viewModel = viewModel("analysis-42")

        viewModel.selectSort(TwinSort.BEST_PAID)

        assertEquals(TwinSort.BEST_PAID, twins.requestedAnalyses.last().second)
        assertEquals("brune", viewModel.uiState.value.twins.content?.offers?.first()?.id)
    }

    @Test
    fun turningTheAlertOnCreatesItWithTheDefaultThreshold() = runTest {
        val viewModel = viewModel("analysis-42")

        viewModel.setAlert(true)

        assertTrue(viewModel.uiState.value.isAlertOn)
        assertEquals(CalmAlert.DEFAULT_THRESHOLD, twins.alerts.value.single().threshold)
        assertEquals(TwinOffersEvent.AlertChanged(isOn = true, threshold = 30), viewModel.events.first())
    }

    @Test
    fun turningTheAlertOffDeletesIt() = runTest {
        twins.alerts.value = listOf(CalmAlert("alert-9", "analysis-42", 30, true))
        val viewModel = viewModel("analysis-42")
        assertTrue(viewModel.uiState.value.isAlertOn)

        viewModel.setAlert(false)

        assertEquals(listOf("alert-9"), twins.deletedAlerts)
        assertFalse(viewModel.uiState.value.isAlertOn)
    }

    @Test
    fun aFailedAlertPutsTheSwitchBack() = runTest {
        twins.alertOutcome = Outcome.Failure(JaugeError.NoConnection)
        val viewModel = viewModel("analysis-42")

        viewModel.setAlert(true)

        assertFalse(viewModel.uiState.value.isAlertOn)
        assertNull(viewModel.uiState.value.alertSwitchOverride)
        assertEquals(TwinOffersEvent.AlertFailed(JaugeError.NoConnection), viewModel.events.first())
    }

    @Test
    fun aFailedLoadShowsAFriendlyError() = runTest {
        twins.twinsOutcome = Outcome.Failure(JaugeError.ServerUnavailable)
        val viewModel = viewModel("analysis-42")

        assertTrue(viewModel.uiState.value.twins.showsFullScreenError)
    }
}
