package io.jauge.feature.analysis.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.jauge.core.domain.model.AnalysisSummary
import io.jauge.core.domain.model.Outcome
import io.jauge.core.domain.usecase.GetAnalysisHistoryUseCase
import io.jauge.core.ui.state.ContentState
import io.jauge.core.ui.state.refreshing
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * View model of the list of analyses ("Mes offres jaugées", "Tout voir").
 *
 * @param getHistory analyses of the user.
 */
class HistoryViewModel(private val getHistory: GetAnalysisHistoryUseCase) : ViewModel() {
    private val state = MutableStateFlow(ContentState<List<AnalysisSummary>>())
    private var hasLoadedOnce = false

    /** Observed state of the screen. */
    val uiState: StateFlow<ContentState<List<AnalysisSummary>>> = state.asStateFlow()

    init {
        viewModelScope.launch {
            getHistory().collect { analyses -> if (hasLoadedOnce) state.update { it.copy(content = analyses) } }
        }
        refresh()
    }

    /** Reloads the list (first load, pull to refresh and "Réessayer"). */
    fun refresh() {
        viewModelScope.launch {
            state.update { it.refreshing() }
            val outcome = getHistory.refresh()
            hasLoadedOnce = true
            state.update {
                when (outcome) {
                    is Outcome.Success -> ContentState(content = getHistory().first(), isLoading = false)
                    is Outcome.Failure -> it.copy(isLoading = false, isRefreshing = false, error = outcome.error)
                }
            }
        }
    }
}
