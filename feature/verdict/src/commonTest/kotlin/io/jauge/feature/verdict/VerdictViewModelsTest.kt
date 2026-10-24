package io.jauge.feature.verdict

import io.jauge.core.domain.error.JaugeError
import io.jauge.core.domain.model.Outcome
import io.jauge.core.domain.usecase.FavoriteAnalysisUseCase
import io.jauge.core.domain.usecase.GetAnalysisUseCase
import io.jauge.core.domain.usecase.GetMethodologyUseCase
import io.jauge.core.domain.usecase.GetSalaryInsightUseCase
import io.jauge.core.domain.usecase.GetSkillsInsightUseCase
import io.jauge.core.domain.usecase.GetTrendInsightUseCase
import io.jauge.core.domain.usecase.SubmitActualApplicantsUseCase
import io.jauge.feature.verdict.common.formatGrouped
import io.jauge.feature.verdict.common.formatSigned
import io.jauge.feature.verdict.details.DetailsViewModel
import io.jauge.feature.verdict.methodology.MethodologyEvent
import io.jauge.feature.verdict.methodology.MethodologyViewModel
import io.jauge.feature.verdict.verdict.VerdictFavoriteFailure
import io.jauge.feature.verdict.verdict.VerdictViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
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
class VerdictViewModelsTest {

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun verdictLoadsTheAnalysis() = runTest {
        val repository = FakeAnalysisRepository()
        val viewModel = VerdictViewModel("analysis-marketing", GetAnalysisUseCase(repository), FavoriteAnalysisUseCase(repository))

        val state = viewModel.uiState.value
        assertEquals(SampleData.analysis, state.content)
        assertFalse(state.isLoading)
        assertNull(state.error)
    }

    @Test
    fun verdictShowsAFriendlyErrorThenRecoversOnRefresh() = runTest {
        val repository = FakeAnalysisRepository(noConnection)
        val viewModel = VerdictViewModel("analysis-marketing", GetAnalysisUseCase(repository), FavoriteAnalysisUseCase(repository))
        assertTrue(viewModel.uiState.value.showsFullScreenError)
        assertEquals(JaugeError.NoConnection, viewModel.uiState.value.error)

        repository.outcome = Outcome.Success(SampleData.analysis)
        viewModel.refresh()

        assertEquals(SampleData.analysis, viewModel.uiState.value.content)
        assertEquals(2, repository.loadCount)
    }

    @Test
    fun verdictKeepsTheContentWhenARefreshFails() = runTest {
        val repository = FakeAnalysisRepository()
        val viewModel = VerdictViewModel("analysis-marketing", GetAnalysisUseCase(repository), FavoriteAnalysisUseCase(repository))
        repository.outcome = noConnection

        viewModel.refresh()

        assertEquals(SampleData.analysis, viewModel.uiState.value.content)
        assertEquals(JaugeError.NoConnection, viewModel.uiState.value.error)
        assertFalse(viewModel.uiState.value.showsFullScreenError)
    }

    @Test
    fun detailsLoadsEverySectionAndTheReliability() = runTest {
        val viewModel = detailsViewModel(FakeInsightRepository())

        val state = viewModel.uiState.value
        assertEquals(SampleData.salary, state.salary.content)
        assertEquals(SampleData.skills, state.skills.content)
        assertEquals(SampleData.trend, state.trend.content)
        assertEquals(SampleData.analysis.reliability, state.reliability)
        assertFalse(state.showsFullScreenError)
    }

    @Test
    fun detailsKeepsTheOtherSectionsWhenOneFails() = runTest {
        val insights = FakeInsightRepository().apply { skills = noConnection }
        val viewModel = detailsViewModel(insights)

        val state = viewModel.uiState.value
        assertEquals(SampleData.salary, state.salary.content)
        assertEquals(JaugeError.NoConnection, state.skills.error)
        assertFalse(state.showsFullScreenError)
    }

    @Test
    fun detailsShowsAFullScreenErrorWhenEverySectionFails() = runTest {
        val insights = FakeInsightRepository().apply {
            salary = noConnection
            skills = noConnection
            trend = noConnection
        }
        assertTrue(detailsViewModel(insights).uiState.value.showsFullScreenError)
    }

    @Test
    fun methodologyRejectsAnImplausibleNumberWithoutCallingTheServer() = runTest {
        val insights = FakeInsightRepository()
        val viewModel = methodologyViewModel(insights)
        viewModel.openFeedback()
        viewModel.onFeedbackTextChange("0")

        viewModel.sendFeedback()

        assertTrue(viewModel.uiState.value.feedback.isInvalid)
        assertTrue(insights.submitted.isEmpty())
    }

    @Test
    fun methodologyKeepsDigitsOnlyAndSendsTheNumber() = runTest {
        val insights = FakeInsightRepository()
        val viewModel = methodologyViewModel(insights)
        viewModel.openFeedback()
        viewModel.onFeedbackTextChange("1 8a0")

        viewModel.sendFeedback()

        assertEquals(listOf(180), insights.submitted)
        assertFalse(viewModel.uiState.value.feedback.isOpen)
        assertEquals(MethodologyEvent.FeedbackSent, viewModel.events.first())
    }

    @Test
    fun methodologyReportsAFailedFeedbackAndKeepsTheDialogOpen() = runTest {
        val insights = FakeInsightRepository().apply { feedback = noConnection }
        val viewModel = methodologyViewModel(insights)
        viewModel.openFeedback()
        viewModel.onFeedbackTextChange("180")

        viewModel.sendFeedback()

        assertTrue(viewModel.uiState.value.feedback.isOpen)
        assertEquals(MethodologyEvent.FeedbackFailed(JaugeError.NoConnection), viewModel.events.first())
    }

    @Test
    fun numbersAreGroupedForTheLanguage() {
        assertEquals("1 284", formatGrouped(1_284, "fr"))
        assertEquals("1,284", formatGrouped(1_284, "en"))
        assertEquals("210", formatGrouped(210, "fr"))
        assertEquals("+18", formatSigned(18))
        assertEquals("-4", formatSigned(-4))
    }

    private fun detailsViewModel(insights: FakeInsightRepository) = DetailsViewModel(
        analysisId = "analysis-marketing",
        getSalary = GetSalaryInsightUseCase(insights),
        getSkills = GetSkillsInsightUseCase(insights),
        getTrend = GetTrendInsightUseCase(insights),
        getAnalysis = GetAnalysisUseCase(FakeAnalysisRepository()),
    )

    private fun methodologyViewModel(insights: FakeInsightRepository) = MethodologyViewModel(
        analysisId = "analysis-marketing",
        getMethodology = GetMethodologyUseCase(insights),
        submitActualApplicants = SubmitActualApplicantsUseCase(insights),
    )

    @Test
    fun verdictStarIsOptimisticAndUndoneWhenTheServerRefuses() = runTest {
        val repository = FakeAnalysisRepository()
        val viewModel = VerdictViewModel("analysis-marketing", GetAnalysisUseCase(repository), FavoriteAnalysisUseCase(repository))
        assertFalse(viewModel.isFavorite.value)

        viewModel.toggleFavorite(true)
        assertTrue(viewModel.isFavorite.value)

        repository.favoriteError = JaugeError.NoConnection
        val failures = mutableListOf<VerdictFavoriteFailure>()
        val collection = launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.favoriteFailures.toList(failures) }
        viewModel.toggleFavorite(false)
        assertTrue(viewModel.isFavorite.value)
        assertEquals(listOf(VerdictFavoriteFailure(wasAdding = false, error = JaugeError.NoConnection)), failures)
        collection.cancel()
    }
}
