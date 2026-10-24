package io.jauge.feature.verdict.verdict

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.jauge.core.domain.error.JaugeError
import io.jauge.core.domain.model.Analysis
import io.jauge.core.domain.model.Outcome
import io.jauge.core.domain.usecase.FavoriteAnalysisUseCase
import io.jauge.core.domain.usecase.GetAnalysisUseCase
import io.jauge.core.ui.state.ContentState
import io.jauge.core.ui.state.reduce
import io.jauge.core.ui.state.refreshing
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * A star change refused by the server, already undone on screen.
 *
 * @property wasAdding true when the user tried to add the star, false when they tried to remove it.
 * @property error the reason, never shown as is.
 */
data class VerdictFavoriteFailure(val wasAdding: Boolean, val error: JaugeError)

/**
 * State holder of the verdict screen (mockup 04).
 *
 * @param analysisId identifier of the analysis to show.
 * @param getAnalysis loads the analysis.
 * @param favoriteAnalysis star of the analysis.
 */
class VerdictViewModel(
    private val analysisId: String,
    private val getAnalysis: GetAnalysisUseCase,
    private val favoriteAnalysis: FavoriteAnalysisUseCase,
) : ViewModel() {

    private val state = MutableStateFlow(ContentState<Analysis>())
    private val favoriteFailureChannel = Channel<VerdictFavoriteFailure>(Channel.BUFFERED)

    /** Observed state of the screen. */
    val uiState: StateFlow<ContentState<Analysis>> = state.asStateFlow()

    /** True when the analysis is a favorite, changed at once when the star is tapped. */
    val isFavorite: StateFlow<Boolean> = favoriteAnalysis.observe(analysisId)
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    /** Star changes refused by the server (already undone), to tell the user. */
    val favoriteFailures: Flow<VerdictFavoriteFailure> = favoriteFailureChannel.receiveAsFlow()

    init {
        refresh()
    }

    /** Loads the analysis; also called by pull to refresh and by "Réessayer". */
    fun refresh() {
        viewModelScope.launch {
            state.update { it.refreshing() }
            val outcome = getAnalysis(analysisId)
            state.update { it.reduce(outcome) }
        }
    }

    /**
     * Adds or removes the star. Optimistic: [isFavorite] changes at once and comes back when the server refuses.
     *
     * @param isFavorite new value.
     */
    fun toggleFavorite(isFavorite: Boolean) {
        viewModelScope.launch {
            val outcome = favoriteAnalysis(analysisId, isFavorite)
            if (outcome is Outcome.Failure) favoriteFailureChannel.send(VerdictFavoriteFailure(isFavorite, outcome.error))
        }
    }
}
