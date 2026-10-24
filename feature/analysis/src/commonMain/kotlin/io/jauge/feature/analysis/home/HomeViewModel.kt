package io.jauge.feature.analysis.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.jauge.core.domain.error.JaugeError
import io.jauge.core.domain.model.AnalysisSummary
import io.jauge.core.domain.model.AuthenticationState
import io.jauge.core.domain.model.OfferSource
import io.jauge.core.domain.model.Outcome
import io.jauge.core.domain.usecase.AnalyzeOfferUseCase
import io.jauge.core.domain.usecase.FavoriteAnalysisUseCase
import io.jauge.core.domain.usecase.GetAnalysisHistoryUseCase
import io.jauge.core.domain.usecase.ObserveAuthenticationStateUseCase
import io.jauge.core.ui.state.ContentState
import io.jauge.feature.analysis.favorites.FavoriteFailure
import io.jauge.feature.analysis.pending.PendingAnalysisStore
import io.jauge.feature.analysis.picker.PickedImage
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * State of the home screen (mockup 02).
 *
 * @property firstName first name of the signed in user ("Inès"), null without account.
 * @property initials initials of the avatar ("IB"), empty without account.
 * @property link text of the link field.
 * @property linkError friendly error of the link field, null when none.
 * @property isImageUnreadable true when the chosen image could not be opened.
 * @property history the last analyses.
 * @property startedRequestId request to open on the reading screen, consumed by [HomeViewModel.onReadingOpened].
 */
data class HomeUiState(
    val firstName: String? = null,
    val initials: String = "",
    val link: String = "",
    val linkError: JaugeError? = null,
    val isImageUnreadable: Boolean = false,
    val history: ContentState<List<AnalysisSummary>> = ContentState(),
    val startedRequestId: String? = null,
)

/**
 * View model of the home screen (mockup 02): the link field, the screenshot and photo choices and the last analyses.
 *
 * @param observeAuthenticationState authentication state, for the greeting.
 * @param getHistory analyses of the user.
 * @param pendingAnalyses keeps the chosen offer for the reading screen.
 * @param favoriteAnalysis adds or removes the star of an analysis.
 */
class HomeViewModel(
    observeAuthenticationState: ObserveAuthenticationStateUseCase,
    private val getHistory: GetAnalysisHistoryUseCase,
    private val pendingAnalyses: PendingAnalysisStore,
    private val favoriteAnalysis: FavoriteAnalysisUseCase,
) : ViewModel() {
    private val state = MutableStateFlow(HomeUiState())
    private val favoriteFailureChannel = Channel<FavoriteFailure>(Channel.BUFFERED)
    private var hasLoadedOnce = false

    /** Observed state of the screen. */
    val uiState: StateFlow<HomeUiState> = state.asStateFlow()

    /** Star changes refused by the server (already undone), to tell the user. */
    val favoriteFailures: Flow<FavoriteFailure> = favoriteFailureChannel.receiveAsFlow()

    init {
        viewModelScope.launch {
            observeAuthenticationState().collect { authentication ->
                val user = (authentication as? AuthenticationState.SignedIn)?.user
                val name = user?.displayName?.trim().orEmpty()
                state.update {
                    it.copy(
                        firstName = name.split(' ').firstOrNull()?.takeIf { first -> first.isNotBlank() },
                        initials = initialsOf(name),
                    )
                }
            }
        }
        viewModelScope.launch {
            getHistory().collect { analyses ->
                if (hasLoadedOnce) state.update { it.copy(history = it.history.copy(content = analyses)) }
            }
        }
        refresh()
    }

    /** Reloads the last analyses (first load and pull to refresh). */
    fun refresh() {
        viewModelScope.launch {
            state.update {
                val history = it.history
                it.copy(history = history.copy(isLoading = history.content == null, isRefreshing = history.content != null, error = null))
            }
            val outcome = getHistory.refresh()
            hasLoadedOnce = true
            val analyses = runCatching { getHistory().first() }.getOrNull()
            state.update {
                it.copy(
                    history = ContentState(
                        content = if (outcome is Outcome.Success) analyses ?: it.history.content else it.history.content,
                        isLoading = false,
                        isRefreshing = false,
                        error = (outcome as? Outcome.Failure)?.error,
                    ),
                )
            }
        }
    }

    /**
     * The link field changed.
     *
     * @param link new text.
     */
    fun onLinkChange(link: String) {
        state.update { it.copy(link = link, linkError = null) }
    }

    /** "Jauger cette offre": checks the link, then opens the reading screen. */
    fun analyzeLink() {
        val link = AnalyzeOfferUseCase.normalizeLink(state.value.link)
        if (link == null) {
            state.update { it.copy(linkError = JaugeError.InvalidLink) }
            return
        }
        start(OfferSource.Link(link))
    }

    /**
     * An image came back from the gallery or the camera.
     *
     * @param image the image, null when it could not be opened.
     */
    fun onImagePicked(image: PickedImage?) {
        if (image == null) {
            state.update { it.copy(isImageUnreadable = true) }
            return
        }
        state.update { it.copy(isImageUnreadable = false) }
        start(OfferSource.Image(image.bytes, image.mimeType, image.origin))
    }

    /**
     * Adds or removes the star of an analysis of the list. The star changes at once; when the server refuses, it
     * comes back and [favoriteFailures] tells the user.
     *
     * @param analysisId identifier of the analysis.
     * @param isFavorite new value.
     */
    fun toggleFavorite(analysisId: String, isFavorite: Boolean) {
        viewModelScope.launch {
            val outcome = favoriteAnalysis(analysisId, isFavorite)
            if (outcome is Outcome.Failure) favoriteFailureChannel.send(FavoriteFailure(isFavorite, outcome.error))
        }
    }

    /** The reading screen was opened for [HomeUiState.startedRequestId]. */
    fun onReadingOpened() {
        state.update { it.copy(startedRequestId = null, link = "") }
    }

    private fun start(source: OfferSource) {
        val requestId = pendingAnalyses.put(source)
        state.update { it.copy(startedRequestId = requestId, linkError = null) }
    }

    private companion object {
        fun initialsOf(name: String): String = name.split(' ', '-')
            .filter { it.isNotBlank() }
            .take(2)
            .joinToString("") { it.first().uppercase() }
    }
}
