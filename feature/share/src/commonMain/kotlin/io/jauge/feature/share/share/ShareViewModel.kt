package io.jauge.feature.share.share

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.jauge.core.domain.error.JaugeError
import io.jauge.core.domain.model.Analysis
import io.jauge.core.domain.model.CompetitionLevel
import io.jauge.core.domain.model.Outcome
import io.jauge.core.domain.model.Share
import io.jauge.core.domain.model.ShareFormat
import io.jauge.core.domain.model.ShareLinks
import io.jauge.core.domain.model.ShareOptions
import io.jauge.core.domain.model.TwinOffer
import io.jauge.core.domain.model.TwinSort
import io.jauge.core.domain.model.getOrNull
import io.jauge.core.domain.usecase.CreateShareUseCase
import io.jauge.core.domain.usecase.GetAnalysisUseCase
import io.jauge.core.domain.usecase.GetTwinOffersUseCase
import io.jauge.core.ui.state.ContentState
import io.jauge.core.ui.state.reduce
import io.jauge.core.ui.state.refreshing
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Where the user sends the analysis (buttons at the bottom of mockup 07). */
enum class ShareDestination {
    /** The messaging app. */
    MESSAGES,

    /** Copy the public link. */
    COPY,

    /** Save the card in the photos. */
    SAVE,

    /** The share sheet of the system. */
    OTHERS,
}

/**
 * Observed state of the share screen.
 *
 * @property analysis the shared analysis.
 * @property calmTwin calmest twin offer, shown on the card when the user wants it, null when there is none.
 * @property options format and options chosen by the user.
 * @property pendingDestination destination being prepared (share link being created), null otherwise.
 */
data class ShareUiState(
    val analysis: ContentState<Analysis> = ContentState(),
    val calmTwin: TwinOffer? = null,
    val options: ShareOptions = ShareOptions(),
    val pendingDestination: ShareDestination? = null,
) {
    /** The public link, always `https://jauge.io/a/{analysisId}`. */
    val link: String?
        get() = analysis.content?.let { ShareLinks.forAnalysis(it.id) }

    /** True when the card shows the calm twin. */
    val showsCalmTwin: Boolean
        get() = options.showCalmTwin && calmTwin != null
}

/** One time events of the share screen. */
sealed interface ShareEvent {
    /**
     * The share is registered (`POST /v1/shares`): the screen sends it to [destination].
     *
     * @property destination where to send it.
     * @property share the created share, with its public link.
     */
    data class Ready(val destination: ShareDestination, val share: Share) : ShareEvent

    /**
     * The share could not be created.
     *
     * @property error the reason, shown as a friendly message.
     */
    data class Failed(val error: JaugeError) : ShareEvent
}

/**
 * State holder of the share card (mockup 07). The share is registered on the server once per set of options, when
 * the user picks a destination; the image of the card is rendered by the app.
 *
 * @param analysisId shared analysis.
 * @param getAnalysis loads the analysis.
 * @param getTwinOffers finds the calmest twin offer.
 * @param createShare registers the share and returns the public link.
 */
class ShareViewModel(
    private val analysisId: String,
    private val getAnalysis: GetAnalysisUseCase,
    private val getTwinOffers: GetTwinOffersUseCase,
    private val createShare: CreateShareUseCase,
) : ViewModel() {

    private val state = MutableStateFlow(ShareUiState())
    private val eventChannel = Channel<ShareEvent>(Channel.BUFFERED)
    private val createdShares = mutableMapOf<ShareOptions, Share>()

    /** Observed state of the screen. */
    val uiState: StateFlow<ShareUiState> = state.asStateFlow()

    /** One time events: share ready to send, or failure. */
    val events: Flow<ShareEvent> = eventChannel.receiveAsFlow()

    init {
        refresh()
    }

    /** Loads the analysis and its calmest twin; also called by pull to refresh and by "Réessayer". */
    fun refresh() {
        viewModelScope.launch {
            state.update { it.copy(analysis = it.analysis.refreshing()) }
            val twins = async { getTwinOffers(analysisId, TwinSort.LEAST_DISPUTED) }
            val analysis = getAnalysis(analysisId)
            // The twin is optional: without it the card simply does not show it.
            val calmTwin = twins.await().getOrNull()?.offers?.firstOrNull { it.level == CompetitionLevel.CALM }
            state.update { it.copy(analysis = it.analysis.reduce(analysis), calmTwin = calmTwin ?: it.calmTwin) }
        }
    }

    /**
     * Changes the format of the card.
     *
     * @param format story, square or link.
     */
    fun selectFormat(format: ShareFormat) = updateOptions { it.copy(format = format) }

    /**
     * Hides or shows the company name on the card.
     *
     * @param hidden true to hide it.
     */
    fun setCompanyHidden(hidden: Boolean) = updateOptions { it.copy(hideCompany = hidden) }

    /**
     * Shows or hides the calm twin on the card.
     *
     * @param shown true to show it.
     */
    fun setCalmTwinShown(shown: Boolean) = updateOptions { it.copy(showCalmTwin = shown) }

    /**
     * Registers the share with the current options (once per set of options), then asks the screen to send it.
     *
     * @param destination where the user wants to send it.
     */
    fun prepare(destination: ShareDestination) {
        val current = state.value
        if (current.analysis.content == null || current.pendingDestination != null) return
        val options = current.options.copy(showCalmTwin = current.showsCalmTwin)
        createdShares[options]?.let { share ->
            eventChannel.trySend(ShareEvent.Ready(destination, share))
            return
        }
        viewModelScope.launch {
            state.update { it.copy(pendingDestination = destination) }
            when (val outcome = createShare(analysisId, options)) {
                is Outcome.Success -> {
                    createdShares[options] = outcome.value
                    eventChannel.send(ShareEvent.Ready(destination, outcome.value))
                }
                is Outcome.Failure -> eventChannel.send(ShareEvent.Failed(outcome.error))
            }
            state.update { it.copy(pendingDestination = null) }
        }
    }

    private fun updateOptions(transform: (ShareOptions) -> ShareOptions) {
        state.update { it.copy(options = transform(it.options)) }
    }
}
