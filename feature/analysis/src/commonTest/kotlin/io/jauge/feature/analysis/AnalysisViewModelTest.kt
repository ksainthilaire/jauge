package io.jauge.feature.analysis

import io.jauge.core.domain.error.JaugeError
import io.jauge.core.domain.model.AnalysisProgress
import io.jauge.core.domain.model.ImageOrigin
import io.jauge.core.domain.model.OfferSource
import io.jauge.core.domain.usecase.AnalyzeOfferUseCase
import io.jauge.core.domain.usecase.GetAnalysisHistoryUseCase
import io.jauge.core.domain.usecase.ObserveAuthenticationStateUseCase
import io.jauge.feature.analysis.history.HistoryViewModel
import io.jauge.feature.analysis.home.HomeViewModel
import io.jauge.feature.analysis.manualentry.ManualEntryViewModel
import io.jauge.feature.analysis.pending.PendingAnalysisStore
import io.jauge.feature.analysis.picker.PickedImage
import io.jauge.feature.analysis.reading.ReadingViewModel
import io.jauge.feature.analysis.unreadableoffer.UnreadableOfferViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class AnalysisViewModelTest {
    private val repository = FakeAnalysisRepository()
    private val store = PendingAnalysisStore()

    @BeforeTest
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private fun homeViewModel() =
        HomeViewModel(ObserveAuthenticationStateUseCase(SignedInAuthenticationRepository()), GetAnalysisHistoryUseCase(repository), store)

    @Test
    fun homeGreetsTheUserAndLoadsTheHistory() {
        val state = homeViewModel().uiState.value
        assertEquals("Inès", state.firstName)
        assertEquals("IB", state.initials)
        assertEquals(sampleAnalyses, state.history.content)
        assertEquals(false, state.history.isLoading)
    }

    @Test
    fun failedRefreshKeepsTheListAndShowsTheError() {
        val viewModel = homeViewModel()
        repository.refreshError = JaugeError.NoConnection
        viewModel.refresh()
        assertEquals(sampleAnalyses, viewModel.uiState.value.history.content)
        assertEquals(JaugeError.NoConnection, viewModel.uiState.value.history.error)
    }

    @Test
    fun firstLoadFailureHasNoContent() {
        repository.refreshError = JaugeError.ServerUnavailable
        val state = homeViewModel().uiState.value
        assertTrue(state.history.showsFullScreenError)
    }

    @Test
    fun textThatIsNotALinkIsRefused() {
        val viewModel = homeViewModel()
        viewModel.onLinkChange("bonjour tout le monde")
        viewModel.analyzeLink()
        assertEquals(JaugeError.InvalidLink, viewModel.uiState.value.linkError)
        assertNull(viewModel.uiState.value.startedRequestId)
    }

    @Test
    fun validLinkStartsTheReading() {
        val viewModel = homeViewModel()
        viewModel.onLinkChange(" emplois-rhone.fr/offre/41872 ")
        viewModel.analyzeLink()
        val requestId = assertNotNull(viewModel.uiState.value.startedRequestId)
        assertEquals(OfferSource.Link("https://emplois-rhone.fr/offre/41872"), store.get(requestId))
        viewModel.onReadingOpened()
        assertNull(viewModel.uiState.value.startedRequestId)
        assertEquals("", viewModel.uiState.value.link)
    }

    @Test
    fun imagesStartTheReadingOrShowAMessage() {
        val viewModel = homeViewModel()
        viewModel.onImagePicked(null)
        assertTrue(viewModel.uiState.value.isImageUnreadable)
        viewModel.onImagePicked(PickedImage(byteArrayOf(1, 2), "image/jpeg", ImageOrigin.SCREENSHOT))
        val requestId = assertNotNull(viewModel.uiState.value.startedRequestId)
        assertIs<OfferSource.Image>(store.get(requestId))
    }

    @Test
    fun readingFollowsTheProgressUntilTheVerdict() {
        val requestId = store.put(OfferSource.Link("https://emplois-rhone.fr/offre/1"))
        val reading = AnalysisProgress.Reading("analysis-1", 62, emptyList(), 2, false)
        repository.progress = listOf(reading)
        val viewModel = ReadingViewModel(requestId, store, AnalyzeOfferUseCase(repository))
        assertEquals(reading, viewModel.uiState.value.progress)
        assertNull(viewModel.uiState.value.completedAnalysisId)
    }

    @Test
    fun unreadableOfferOpensItsScreen() {
        val requestId = store.put(OfferSource.Image(byteArrayOf(1), "image/jpeg", ImageOrigin.PHOTO))
        repository.progress = listOf(AnalysisProgress.Failed(JaugeError.UnreadableOffer("Maison Odier")))
        val viewModel = ReadingViewModel(requestId, store, AnalyzeOfferUseCase(repository))
        assertEquals("Maison Odier", viewModel.uiState.value.unreadable?.recognizedCompany)
        viewModel.onNavigated()
        assertNull(viewModel.uiState.value.unreadable)
        val unreadable = UnreadableOfferViewModel(requestId, "Maison Odier", store)
        assertNotNull(unreadable.uiState.value.imageBytes)
        unreadable.onImagePicked(PickedImage(byteArrayOf(3), "image/jpeg", ImageOrigin.PHOTO))
        assertNotNull(unreadable.uiState.value.startedRequestId)
    }

    @Test
    fun otherFailuresAreShownAndCanBeRetried() {
        val requestId = store.put(OfferSource.Link("https://emplois-rhone.fr/offre/1"))
        repository.progress = listOf(AnalysisProgress.Failed(JaugeError.Timeout))
        val viewModel = ReadingViewModel(requestId, store, AnalyzeOfferUseCase(repository))
        assertEquals(JaugeError.Timeout, viewModel.uiState.value.error)
        repository.progress = listOf(AnalysisProgress.Reading("analysis-1", 10, emptyList(), null, null))
        viewModel.retry()
        assertNull(viewModel.uiState.value.error)
    }

    @Test
    fun unknownRequestIsNotFound() {
        val viewModel = ReadingViewModel("request-unknown", store, AnalyzeOfferUseCase(repository))
        assertEquals(JaugeError.NotFound, viewModel.uiState.value.error)
    }

    @Test
    fun manualEntryNeedsBothFields() {
        val viewModel = ManualEntryViewModel(store)
        viewModel.onJobTitleChange("Responsable CRM")
        viewModel.analyze()
        assertTrue(viewModel.uiState.value.isIncomplete)
        viewModel.onCityChange(" Lyon ")
        viewModel.analyze()
        val requestId = assertNotNull(viewModel.uiState.value.startedRequestId)
        assertEquals(OfferSource.Manual("Responsable CRM", "Lyon"), store.get(requestId))
    }

    @Test
    fun historyLoadsAndRefreshes() {
        val viewModel = HistoryViewModel(GetAnalysisHistoryUseCase(repository))
        assertEquals(sampleAnalyses, viewModel.uiState.value.content)
        repository.refreshError = JaugeError.Timeout
        viewModel.refresh()
        assertEquals(JaugeError.Timeout, viewModel.uiState.value.error)
        assertEquals(sampleAnalyses, viewModel.uiState.value.content)
    }
}
