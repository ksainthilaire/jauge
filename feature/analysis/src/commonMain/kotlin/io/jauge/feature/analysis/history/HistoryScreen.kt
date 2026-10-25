package io.jauge.feature.analysis.history

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.jauge.core.designsystem.component.EmptyState
import io.jauge.core.designsystem.component.JaugeChip
import io.jauge.core.designsystem.component.JaugeChipStyle
import io.jauge.core.designsystem.component.JaugeRefreshContainer
import io.jauge.core.designsystem.component.JaugeScreenScaffold
import io.jauge.core.designsystem.component.JaugeSearchField
import io.jauge.core.designsystem.component.RoundIconButton
import io.jauge.core.designsystem.component.SkeletonList
import io.jauge.core.designsystem.component.hideKeyboardOnScroll
import io.jauge.core.designsystem.icon.JaugeIcons
import io.jauge.core.designsystem.theme.JaugeTheme
import io.jauge.core.domain.usecase.AnalysisFilter
import io.jauge.core.ui.error.FriendlyAction
import io.jauge.core.ui.error.FriendlyErrorView
import io.jauge.feature.analysis.components.AnalysisListCard
import io.jauge.feature.analysis.favorites.FavoriteFailureMessages
import io.jauge.feature.analysis.resources.Res
import io.jauge.feature.analysis.resources.history_back
import io.jauge.feature.analysis.resources.history_favorites_empty_message
import io.jauge.feature.analysis.resources.history_favorites_empty_title
import io.jauge.feature.analysis.resources.history_filter_all
import io.jauge.feature.analysis.resources.history_filter_favorites
import io.jauge.feature.analysis.resources.history_search_clear
import io.jauge.feature.analysis.resources.history_search_empty_action
import io.jauge.feature.analysis.resources.history_search_empty_message
import io.jauge.feature.analysis.resources.history_search_empty_title
import io.jauge.feature.analysis.resources.history_search_placeholder
import io.jauge.feature.analysis.resources.history_title
import io.jauge.feature.analysis.resources.home_empty_message
import io.jauge.feature.analysis.resources.home_empty_title
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

/**
 * Every analysis of the user ("Mes offres jaugées", "Tout voir" on mockup 02), with pull to refresh, the filter
 * "Favoris" and a star on every analysis.
 *
 * @param onBack back button.
 * @param onOpenAnalysis opens the verdict of an analysis.
 * @param onOpenPaywall opens Jauge Plus when an error asks for it.
 */
@Composable
fun HistoryScreen(onBack: () -> Unit, onOpenAnalysis: (String) -> Unit, onOpenPaywall: () -> Unit) {
    val viewModel: HistoryViewModel = koinViewModel()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    FavoriteFailureMessages(failures = viewModel.favoriteFailures, snackbarHostState = snackbarHostState)
    HistoryContent(
        state = state,
        onBack = onBack,
        onOpenAnalysis = onOpenAnalysis,
        onRefresh = viewModel::refresh,
        onErrorAction = { action -> if (action == FriendlyAction.OPEN_PAYWALL) onOpenPaywall() else viewModel.refresh() },
        onSelectFilter = viewModel::selectFilter,
        onToggleFavorite = viewModel::toggleFavorite,
        onQueryChange = viewModel::updateQuery,
        onClearQuery = viewModel::clearQuery,
        snackbarHostState = snackbarHostState,
    )
}

/**
 * Stateless content of the list of analyses.
 *
 * @param state state to draw.
 * @param onBack back button.
 * @param onOpenAnalysis tap on an analysis.
 * @param onRefresh pull to refresh.
 * @param onErrorAction action of a friendly error.
 * @param onSelectFilter "Toutes" or "Favoris".
 * @param onToggleFavorite star of an analysis tapped, with its new value.
 * @param onQueryChange text typed in the search field (shown when the list holds more than 6 analyses).
 * @param onClearQuery empties the search field.
 * @param modifier modifier of the screen.
 * @param snackbarHostState host of the short messages (star refused by the server).
 * @param animateGauges false to draw the gauges at once (screenshots).
 */
@Composable
fun HistoryContent(
    state: HistoryUiState,
    onBack: () -> Unit,
    onOpenAnalysis: (String) -> Unit,
    onRefresh: () -> Unit,
    onErrorAction: (FriendlyAction) -> Unit,
    onSelectFilter: (AnalysisFilter) -> Unit,
    onToggleFavorite: (String, Boolean) -> Unit,
    onQueryChange: (String) -> Unit,
    onClearQuery: () -> Unit,
    modifier: Modifier = Modifier,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
    animateGauges: Boolean = true,
) {
    val analysesState = state.analyses
    JaugeScreenScaffold(
        modifier = modifier,
        snackbarHostState = snackbarHostState,
        title = stringResource(Res.string.history_title),
        navigationIcon = { RoundIconButton(JaugeIcons.Back, stringResource(Res.string.history_back), onBack) },
    ) {
        JaugeRefreshContainer(isRefreshing = analysesState.isRefreshing, onRefresh = onRefresh, modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .hideKeyboardOnScroll()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = JaugeTheme.spacing.screenGutter)
                    .padding(top = 12.dp, bottom = 24.dp),
            ) {
                val analyses = state.visibleAnalyses
                if (state.showsSearch && !analysesState.isLoading && !analysesState.showsFullScreenError) {
                    JaugeSearchField(
                        query = state.query,
                        onQueryChange = onQueryChange,
                        placeholder = stringResource(Res.string.history_search_placeholder),
                        clearDescription = stringResource(Res.string.history_search_clear),
                        onClear = onClearQuery,
                        modifier = Modifier.padding(bottom = 12.dp),
                    )
                }
                if (!analysesState.isLoading && !analysesState.showsFullScreenError && !analysesState.content.isNullOrEmpty()) {
                    Row(modifier = Modifier.padding(bottom = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        AnalysisFilter.entries.forEach { filter ->
                            JaugeChip(
                                text = stringResource(filter.label()),
                                selected = filter == state.filter,
                                onClick = { onSelectFilter(filter) },
                                style = JaugeChipStyle.FILTER,
                            )
                        }
                    }
                }
                when {
                    analysesState.isLoading -> SkeletonList(rowCount = 5)
                    analysesState.showsFullScreenError -> FriendlyErrorView(error = analysesState.error!!, onAction = onErrorAction)
                    analysesState.content.isNullOrEmpty() -> EmptyState(
                        title = stringResource(Res.string.home_empty_title),
                        message = stringResource(Res.string.home_empty_message),
                    )
                    state.hasNoSearchResult -> EmptyState(
                        title = stringResource(Res.string.history_search_empty_title, state.appliedQuery),
                        message = stringResource(Res.string.history_search_empty_message),
                        icon = JaugeIcons.Search,
                        actionLabel = stringResource(Res.string.history_search_empty_action),
                        onAction = onClearQuery,
                    )
                    analyses.isNullOrEmpty() -> EmptyState(
                        title = stringResource(Res.string.history_favorites_empty_title),
                        message = stringResource(Res.string.history_favorites_empty_message),
                    )
                    else -> AnalysisListCard(
                        analyses = analyses,
                        onOpenAnalysis = onOpenAnalysis,
                        animateGauges = animateGauges,
                        onToggleFavorite = onToggleFavorite,
                    )
                }
            }
        }
    }
}

private fun AnalysisFilter.label(): StringResource = when (this) {
    AnalysisFilter.ALL -> Res.string.history_filter_all
    AnalysisFilter.FAVORITES -> Res.string.history_filter_favorites
}
