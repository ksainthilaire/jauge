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
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock

/** Part of the day, for the greeting: "Bonjour" from 5 h, "Bonsoir" from 18 h. */
enum class GreetingMoment {
    /** From 5 h to 17 h 59. */
    DAY,

    /** From 18 h to 4 h 59. */
    EVENING,
    ;

    /** Helpers. */
    companion object {
        /**
         * Moment of [hour].
         *
         * @param hour hour of the day, 0 to 23.
         */
        fun of(hour: Int): GreetingMoment = if (hour in 5..17) DAY else EVENING
    }
}

/**
 * State of the home screen (mockup 02).
 *
 * @property firstName first name of the signed in user ("Inès"), null without account.
 * @property initials initials of the avatar ("IB"), empty without account.
 * @property greetingMoment "Bonjour" or "Bonsoir".
 * @property link text of the link field.
 * @property linkError friendly error of the link field, null when none.
 * @property clipboardMayHoldLink true when the system says the clipboard holds a link ("Coller le lien copié").
 * @property isClipboardEmpty true after a tap on "Coller" with nothing to paste.
 * @property isImageUnreadable true when the chosen image could not be opened.
 * @property history the last analyses.
 * @property startedRequestId request to open on the reading screen, consumed by [HomeViewModel.onReadingOpened].
 */
data class HomeUiState(
    val firstName: String? = null,
    val initials: String = "",
    val greetingMoment: GreetingMoment = GreetingMoment.DAY,
    val link: String = "",
    val linkError: JaugeError? = null,
    val clipboardMayHoldLink: Boolean = false,
    val isClipboardEmpty: Boolean = false,
    val isImageUnreadable: Boolean = false,
    val history: ContentState<List<AnalysisSummary>> = ContentState(),
    val startedRequestId: String? = null,
) {
    /** What the link field holds, for the line under it. */
    val linkStatus: OfferLinkStatus get() = OfferLinkRecognizer.recognize(link)

    /** True when "Jauger" can be tapped: the field holds a web address. */
    val canAnalyze: Boolean get() = linkStatus is OfferLinkStatus.Recognized
}

/**
 * View model of the home screen (mockup 02): the link field, the screenshot and photo choices and the last analyses.
 *
 * @param observeAuthenticationState authentication state, for the greeting.
 * @param getHistory analyses of the user.
 * @param pendingAnalyses keeps the chosen offer for the reading screen.
 * @param favoriteAnalysis adds or removes the star of an analysis.
 * @param currentHour hour of the day on the phone, for the greeting.
 */
class HomeViewModel(
    observeAuthenticationState: ObserveAuthenticationStateUseCase,
    private val getHistory: GetAnalysisHistoryUseCase,
    private val pendingAnalyses: PendingAnalysisStore,
    private val favoriteAnalysis: FavoriteAnalysisUseCase,
    private val currentHour: () -> Int = ::localHour,
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
        updateGreeting()
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
        state.update { it.copy(link = link, linkError = null, isClipboardEmpty = false) }
    }

    /** The cross of the field: empties it. */
    fun clearLink() {
        state.update { it.copy(link = "", linkError = null, isClipboardEmpty = false) }
    }

    /**
     * What the system says about the clipboard, checked when the screen comes back to the front. Never reads it.
     *
     * @param mayHoldLink true when the clipboard probably holds a web address.
     */
    fun onClipboardChecked(mayHoldLink: Boolean) {
        state.update { it.copy(clipboardMayHoldLink = mayHoldLink) }
    }

    /**
     * "Coller" was tapped and the clipboard read.
     *
     * @param text text of the clipboard, null when there was nothing to paste.
     */
    fun onPasted(text: String?) {
        val pasted = text?.trim().orEmpty()
        if (pasted.isEmpty()) {
            state.update { it.copy(isClipboardEmpty = true, linkError = null) }
            return
        }
        // A copied offer often comes with its title ("Chargée de marketing https://..."): keep the address only.
        val link = pasted.split(Regex("\\s+")).firstOrNull { AnalyzeOfferUseCase.normalizeLink(it) != null } ?: pasted
        state.update { it.copy(link = link, linkError = null, isClipboardEmpty = false, clipboardMayHoldLink = false) }
    }

    /** Recomputes "Bonjour" or "Bonsoir" (first load, pull to refresh, back to the front). */
    fun updateGreeting() {
        state.update { it.copy(greetingMoment = GreetingMoment.of(currentHour())) }
    }

    /** "Jauger": checks the link, then opens the reading screen. */
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
        fun localHour(): Int = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).hour

        fun initialsOf(name: String): String = name.split(' ', '-')
            .filter { it.isNotBlank() }
            .take(2)
            .joinToString("") { it.first().uppercase() }
    }
}
