package io.jauge.feature.verdict.verdict

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.jauge.core.domain.model.Analysis
import io.jauge.core.domain.usecase.GetAnalysisUseCase
import io.jauge.core.ui.state.ContentState
import io.jauge.core.ui.state.reduce
import io.jauge.core.ui.state.refreshing
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * State holder of the verdict screen (mockup 04).
 *
 * @param analysisId identifier of the analysis to show.
 * @param getAnalysis loads the analysis.
 */
class VerdictViewModel(
    private val analysisId: String,
    private val getAnalysis: GetAnalysisUseCase,
) : ViewModel() {

    private val state = MutableStateFlow(ContentState<Analysis>())

    /** Observed state of the screen. */
    val uiState: StateFlow<ContentState<Analysis>> = state.asStateFlow()

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
}
