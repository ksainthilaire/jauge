package io.jauge.feature.share

import io.jauge.core.domain.error.JaugeError
import io.jauge.core.domain.model.Outcome
import io.jauge.core.domain.model.ShareFormat
import io.jauge.core.domain.model.ShareOptions
import io.jauge.core.domain.usecase.CreateShareUseCase
import io.jauge.core.domain.usecase.DeleteShareUseCase
import io.jauge.core.domain.usecase.GetAnalysisHistoryUseCase
import io.jauge.core.domain.usecase.GetAnalysisUseCase
import io.jauge.core.domain.usecase.GetSharesUseCase
import io.jauge.core.domain.usecase.GetTwinOffersUseCase
import io.jauge.feature.share.link.AnalysisLink
import io.jauge.feature.share.share.ShareDestination
import io.jauge.feature.share.share.ShareEvent
import io.jauge.feature.share.share.ShareViewModel
import io.jauge.feature.share.shares.SharesEvent
import io.jauge.feature.share.shares.SharesViewModel
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
class ShareViewModelsTest {

    private val shares = FakeShareRepository()

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun shareViewModel(twins: FakeTwinRepository = FakeTwinRepository(listOf(ShareSampleData.calmTwin))) = ShareViewModel(
        analysisId = "analysis-marketing",
        getAnalysis = GetAnalysisUseCase(FakeAnalysisRepository()),
        getTwinOffers = GetTwinOffersUseCase(twins),
        createShare = CreateShareUseCase(shares),
    )

    @Test
    fun loadsTheAnalysisAndItsCalmTwin() = runTest {
        val state = shareViewModel().uiState.value

        assertEquals(ShareSampleData.analysis, state.analysis.content)
        assertEquals(ShareSampleData.calmTwin, state.calmTwin)
        assertEquals("https://jauge.io/a/analysis-marketing", state.link)
    }

    @Test
    fun aDisputedTwinIsNotShownAsCalm() = runTest {
        val state = shareViewModel(FakeTwinRepository(listOf(ShareSampleData.disputedTwin))).uiState.value

        assertNull(state.calmTwin)
        assertFalse(state.showsCalmTwin)
    }

    @Test
    fun theShareIsCreatedOncePerOptionsWithThePublicLink() = runTest {
        val viewModel = shareViewModel()
        viewModel.setCompanyHidden(true)

        viewModel.prepare(ShareDestination.COPY)
        val first = viewModel.events.first() as ShareEvent.Ready
        viewModel.prepare(ShareDestination.OTHERS)
        val second = viewModel.events.first() as ShareEvent.Ready

        assertEquals("https://jauge.io/a/analysis-marketing", first.share.url)
        assertEquals(ShareDestination.OTHERS, second.destination)
        assertEquals(listOf(ShareOptions(format = ShareFormat.STORY, hideCompany = true, showCalmTwin = true)), shares.createdOptions)
    }

    @Test
    fun changingTheFormatCreatesANewShare() = runTest {
        val viewModel = shareViewModel()
        viewModel.prepare(ShareDestination.COPY)
        viewModel.events.first()

        viewModel.selectFormat(ShareFormat.LINK)
        viewModel.prepare(ShareDestination.COPY)
        viewModel.events.first()

        assertEquals(listOf(ShareFormat.STORY, ShareFormat.LINK), shares.createdOptions.map { it.format })
    }

    @Test
    fun aFailedShareIsReportedAndCanBeRetried() = runTest {
        shares.createOutcome = Outcome.Failure(JaugeError.NoConnection)
        val viewModel = shareViewModel()

        viewModel.prepare(ShareDestination.SAVE)

        assertEquals(ShareEvent.Failed(JaugeError.NoConnection), viewModel.events.first())
        assertNull(viewModel.uiState.value.pendingDestination)
    }

    @Test
    fun theSharesListShowsTheTitlesOfTheHistory() = runTest {
        val viewModel = SharesViewModel(GetSharesUseCase(shares), GetAnalysisHistoryUseCase(FakeAnalysisRepository()), DeleteShareUseCase(shares))

        val items = viewModel.uiState.value.shares.content!!
        assertEquals(listOf("share-1", "share-2"), items.map { it.share.id })
        assertEquals("Chargée de marketing digital", items.first().analysis?.jobTitle)
        assertNull(items.last().analysis)
    }

    @Test
    fun deletingAShareAsksFirstThenRemovesIt() = runTest {
        val viewModel = SharesViewModel(GetSharesUseCase(shares), GetAnalysisHistoryUseCase(FakeAnalysisRepository()), DeleteShareUseCase(shares))
        val item = viewModel.uiState.value.shares.content!!.first()

        viewModel.askToDelete(item)
        assertEquals(item, viewModel.uiState.value.shareToDelete)
        viewModel.confirmDelete()

        assertEquals(listOf("share-1"), shares.deleted)
        assertEquals(listOf("share-2"), viewModel.uiState.value.shares.content!!.map { it.share.id })
        assertEquals(SharesEvent.Deleted, viewModel.events.first())
    }

    @Test
    fun anEmptySharesListIsNotAnError() = runTest {
        shares.shares = Outcome.Success(emptyList())
        val viewModel = SharesViewModel(GetSharesUseCase(shares), GetAnalysisHistoryUseCase(FakeAnalysisRepository()), DeleteShareUseCase(shares))

        assertTrue(viewModel.uiState.value.shares.content!!.isEmpty())
        assertNull(viewModel.uiState.value.shares.error)
    }

    @Test
    fun analysisLinksAreRecognized() {
        assertEquals("analysis-marketing", AnalysisLink.analysisIdOrNull("https://jauge.io/a/analysis-marketing"))
        assertEquals("abc123", AnalysisLink.analysisIdOrNull("https://www.jauge.io/a/abc123/?utm_source=story#top"))
        assertEquals("abc123", AnalysisLink.analysisIdOrNull("HTTPS://JAUGE.io/a/abc123"))
        assertNull(AnalysisLink.analysisIdOrNull("http://jauge.io/a/abc123"))
        assertNull(AnalysisLink.analysisIdOrNull("https://evil.example@jauge.io/a/abc123"))
        assertNull(AnalysisLink.analysisIdOrNull("https://jauge.io:8443/a/abc123"))
        assertNull(AnalysisLink.analysisIdOrNull("https://jauge.io\\@evil.example/a/abc123"))
        assertNull(AnalysisLink.analysisIdOrNull("https://jauge.io/a/" + "x".repeat(65)))
        assertNull(AnalysisLink.analysisIdOrNull("https://jauge.io/b/abc123"))
        assertNull(AnalysisLink.analysisIdOrNull("https://jauge.io/a/"))
        assertNull(AnalysisLink.analysisIdOrNull("https://jauge.io/a/abc/def"))
        assertNull(AnalysisLink.analysisIdOrNull("https://evil.example/a/abc123"))
        assertNull(AnalysisLink.analysisIdOrNull("https://jauge.io.evil.example/a/abc123"))
        assertNull(AnalysisLink.analysisIdOrNull("https://jauge.io/a/abc%2F123"))
        assertNull(AnalysisLink.analysisIdOrNull("jauge://a/abc123"))
        assertNull(AnalysisLink.analysisIdOrNull(null))
    }
}
