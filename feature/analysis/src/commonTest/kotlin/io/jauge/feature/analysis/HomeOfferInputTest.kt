package io.jauge.feature.analysis

import io.jauge.core.domain.error.JaugeError
import io.jauge.core.domain.model.OfferSource
import io.jauge.core.domain.usecase.FavoriteAnalysisUseCase
import io.jauge.core.domain.usecase.GetAnalysisHistoryUseCase
import io.jauge.core.domain.usecase.ObserveAuthenticationStateUseCase
import io.jauge.feature.analysis.home.GreetingMoment
import io.jauge.feature.analysis.home.HomeViewModel
import io.jauge.feature.analysis.home.OfferLinkRecognizer
import io.jauge.feature.analysis.home.OfferLinkStatus
import io.jauge.feature.analysis.pending.PendingAnalysisStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The link composer of the home screen: link recognition, state of "Jauger", "Coller" and the greeting. */
@OptIn(ExperimentalCoroutinesApi::class)
class HomeOfferInputTest {
    private val repository = FakeAnalysisRepository()
    private val store = PendingAnalysisStore()
    private var hour = 9

    @BeforeTest
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private fun homeViewModel() = HomeViewModel(
        ObserveAuthenticationStateUseCase(SignedInAuthenticationRepository()),
        GetAnalysisHistoryUseCase(repository),
        store,
        FavoriteAnalysisUseCase(repository),
        currentHour = { hour },
    )

    @Test
    fun knownJobBoardsAreNamed() {
        val status = OfferLinkRecognizer.recognize("https://www.welcometothejungle.com/fr/companies/sorel/jobs/marketing")
        assertEquals(OfferLinkStatus.Recognized("https://www.welcometothejungle.com/fr/companies/sorel/jobs/marketing", "welcometothejungle.com", "Welcome to the Jungle"), status)
        assertEquals("Indeed", (OfferLinkRecognizer.recognize("fr.indeed.com/viewjob?jk=12") as OfferLinkStatus.Recognized).jobBoardName)
        assertEquals("France Travail", OfferLinkRecognizer.jobBoardNameOf("candidat.francetravail.fr"))
    }

    @Test
    fun otherSitesShowTheirAddress() {
        val status = OfferLinkRecognizer.recognize(" emplois-rhone.fr/offre/41872 ")
        assertEquals(OfferLinkStatus.Recognized("https://emplois-rhone.fr/offre/41872", "emplois-rhone.fr", null), status)
        assertEquals("example.org", OfferLinkRecognizer.hostOf("http://m.Example.org:8080/a?b#c"))
        assertNull(OfferLinkRecognizer.jobBoardNameOf("notindeed.com"))
    }

    @Test
    fun emptyAndIncompleteTexts() {
        assertEquals(OfferLinkStatus.Empty, OfferLinkRecognizer.recognize("  "))
        assertEquals(OfferLinkStatus.Incomplete, OfferLinkRecognizer.recognize("chargée de marketing"))
        assertEquals(OfferLinkStatus.Incomplete, OfferLinkRecognizer.recognize("indeed"))
    }

    @Test
    fun gaugeIsOffUntilALinkIsRecognized() {
        val viewModel = homeViewModel()
        assertFalse(viewModel.uiState.value.canAnalyze)
        assertEquals(OfferLinkStatus.Empty, viewModel.uiState.value.linkStatus)
        viewModel.onLinkChange("emplois-rhone")
        assertFalse(viewModel.uiState.value.canAnalyze)
        assertEquals(OfferLinkStatus.Incomplete, viewModel.uiState.value.linkStatus)
        viewModel.onLinkChange("emplois-rhone.fr/offre/1")
        assertTrue(viewModel.uiState.value.canAnalyze)
        viewModel.clearLink()
        assertEquals("", viewModel.uiState.value.link)
        assertFalse(viewModel.uiState.value.canAnalyze)
    }

    @Test
    fun pastingKeepsTheAddressOfACopiedOffer() {
        val viewModel = homeViewModel()
        viewModel.onClipboardChecked(true)
        assertTrue(viewModel.uiState.value.clipboardMayHoldLink)
        viewModel.onPasted("Chargée de marketing digital\nhttps://www.hellowork.com/fr-fr/emplois/41872.html")
        val state = viewModel.uiState.value
        assertEquals("https://www.hellowork.com/fr-fr/emplois/41872.html", state.link)
        assertFalse(state.clipboardMayHoldLink)
        assertEquals("HelloWork", (state.linkStatus as OfferLinkStatus.Recognized).jobBoardName)
        viewModel.analyzeLink()
        val requestId = assertNotNull(viewModel.uiState.value.startedRequestId)
        assertEquals(OfferSource.Link("https://www.hellowork.com/fr-fr/emplois/41872.html"), store.get(requestId))
    }

    @Test
    fun pastingNothingSaysSoAndTypingClearsTheMessage() {
        val viewModel = homeViewModel()
        viewModel.onPasted(null)
        assertTrue(viewModel.uiState.value.isClipboardEmpty)
        assertEquals("", viewModel.uiState.value.link)
        viewModel.onLinkChange("a")
        assertFalse(viewModel.uiState.value.isClipboardEmpty)
    }

    @Test
    fun pastedTextWithoutLinkStaysAsIsAndGaugeIsRefused() {
        val viewModel = homeViewModel()
        viewModel.onPasted("Chargée de marketing")
        assertEquals("Chargée de marketing", viewModel.uiState.value.link)
        assertFalse(viewModel.uiState.value.canAnalyze)
        viewModel.analyzeLink()
        assertEquals(JaugeError.InvalidLink, viewModel.uiState.value.linkError)
    }

    @Test
    fun greetingFollowsTheHour() {
        assertEquals(GreetingMoment.EVENING, GreetingMoment.of(4))
        assertEquals(GreetingMoment.DAY, GreetingMoment.of(5))
        assertEquals(GreetingMoment.DAY, GreetingMoment.of(17))
        assertEquals(GreetingMoment.EVENING, GreetingMoment.of(18))
        val viewModel = homeViewModel()
        assertEquals(GreetingMoment.DAY, viewModel.uiState.value.greetingMoment)
        hour = 21
        viewModel.updateGreeting()
        assertEquals(GreetingMoment.EVENING, viewModel.uiState.value.greetingMoment)
    }
}
