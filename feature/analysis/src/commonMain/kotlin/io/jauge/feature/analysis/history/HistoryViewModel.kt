package io.jauge.feature.analysis.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.jauge.core.common.text.SearchText
import io.jauge.core.domain.model.AnalysisSummary
import io.jauge.core.domain.model.Outcome
import io.jauge.core.domain.usecase.AnalysisFilter
import io.jauge.core.domain.usecase.FavoriteAnalysisUseCase
import io.jauge.core.domain.usecase.GetAnalysisHistoryUseCase
import io.jauge.core.ui.state.ContentState
import io.jauge.core.ui.state.refreshing
import io.jauge.feature.analysis.favorites.FavoriteFailure
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * State of the list of analyses.
 *
 * @property analyses every analysis of the user, with the loading, refreshing and error states.
 * @property filter chosen filter ("Toutes" or "Favoris").
 * @property query text of the search field, shown as typed.
 * @property appliedQuery search applied to the list, [query] after a short typing delay.
 */
data class HistoryUiState(
    val analyses: ContentState<List<AnalysisSummary>> = ContentState(),
    val filter: AnalysisFilter = AnalysisFilter.ALL,
    val query: String = "",
    val appliedQuery: String = "",
) {
    /** Analyses kept by the [filter] only, before the search, null while unknown. */
    private val filteredAnalyses: List<AnalysisSummary>?
        get() = analyses.content?.let(filter::apply)

    /** Analyses shown with the current [filter] and search (job title, company or city), null while unknown. */
    val visibleAnalyses: List<AnalysisSummary>?
        get() = filteredAnalyses?.filter { SearchText.matches(appliedQuery, it.jobTitle, it.company, it.place) }

    /** True when the filtered list is long enough to show the search field, or while a search is typed. */
    val showsSearch: Boolean
        get() = (filteredAnalyses?.size ?: 0) > SearchText.LIST_SIZE_WITHOUT_SEARCH || query.isNotBlank()

    /** True when the filter keeps analyses but none matches the search ("Aucun résultat pour « … »"). */
    val hasNoSearchResult: Boolean
        get() = appliedQuery.isNotBlank() && !filteredAnalyses.isNullOrEmpty() && visibleAnalyses.isNullOrEmpty()
}

/**
 * View model of the list of analyses ("Mes offres jaugées", "Tout voir"): filter "Favoris" and stars.
 *
 * @param getHistory analyses of the user.
 * @param favoriteAnalysis adds or removes the star of an analysis.
 */
class HistoryViewModel(
    private val getHistory: GetAnalysisHistoryUseCase,
    private val favoriteAnalysis: FavoriteAnalysisUseCase,
) : ViewModel() {
    private val state = MutableStateFlow(HistoryUiState())
    private val favoriteFailureChannel = Channel<FavoriteFailure>(Channel.BUFFERED)
    private var hasLoadedOnce = false
    private var searchJob: Job? = null

    /** Observed state of the screen. */
    val uiState: StateFlow<HistoryUiState> = state.asStateFlow()

    /** Star changes refused by the server (already undone), to tell the user. */
    val favoriteFailures: Flow<FavoriteFailure> = favoriteFailureChannel.receiveAsFlow()

    init {
        viewModelScope.launch {
            getHistory().collect { analyses ->
                if (hasLoadedOnce) state.update { it.copy(analyses = it.analyses.copy(content = analyses)) }
            }
        }
        refresh()
    }

    /** Reloads the list (first load, pull to refresh and "Réessayer"). */
    fun refresh() {
        viewModelScope.launch {
            state.update { it.copy(analyses = it.analyses.refreshing()) }
            val outcome = getHistory.refresh()
            hasLoadedOnce = true
            state.update {
                when (outcome) {
                    is Outcome.Success -> it.copy(analyses = ContentState(content = getHistory().first(), isLoading = false))
                    is Outcome.Failure -> it.copy(analyses = it.analyses.copy(isLoading = false, isRefreshing = false, error = outcome.error))
                }
            }
        }
    }

    /**
     * Chooses the filter of the list.
     *
     * @param filter "Toutes" or "Favoris".
     */
    fun selectFilter(filter: AnalysisFilter) {
        state.update { it.copy(filter = filter) }
    }

    /**
     * Changes the text of the search field; the list follows after a short typing delay.
     *
     * @param query the text typed by the user.
     */
    fun updateQuery(query: String) {
        state.update { it.copy(query = query) }
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            delay(SearchText.TYPING_DELAY_MILLISECONDS)
            state.update { it.copy(appliedQuery = query.trim()) }
        }
    }

    /** Empties the search field and shows the whole list at once. */
    fun clearQuery() {
        searchJob?.cancel()
        state.update { it.copy(query = "", appliedQuery = "") }
    }

    /**
     * Adds or removes the star of an analysis. The list changes at once; when the server refuses, the star comes back
     * and [favoriteFailures] tells the user.
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
}
