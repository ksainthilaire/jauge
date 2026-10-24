package io.jauge.feature.analysis.history

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.jauge.core.designsystem.component.EmptyState
import io.jauge.core.designsystem.component.JaugeRefreshContainer
import io.jauge.core.designsystem.component.JaugeScreenScaffold
import io.jauge.core.designsystem.component.RoundIconButton
import io.jauge.core.designsystem.component.SkeletonList
import io.jauge.core.designsystem.icon.JaugeIcons
import io.jauge.core.designsystem.theme.JaugeTheme
import io.jauge.core.domain.model.AnalysisSummary
import io.jauge.core.ui.error.FriendlyAction
import io.jauge.core.ui.error.FriendlyErrorView
import io.jauge.core.ui.state.ContentState
import io.jauge.feature.analysis.components.AnalysisListCard
import io.jauge.feature.analysis.resources.Res
import io.jauge.feature.analysis.resources.history_back
import io.jauge.feature.analysis.resources.history_title
import io.jauge.feature.analysis.resources.home_empty_message
import io.jauge.feature.analysis.resources.home_empty_title
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

/**
 * Every analysis of the user ("Mes offres jaugées", "Tout voir" on mockup 02), with pull to refresh.
 *
 * @param onBack back button.
 * @param onOpenAnalysis opens the verdict of an analysis.
 * @param onOpenPaywall opens Jauge Plus when an error asks for it.
 */
@Composable
fun HistoryScreen(onBack: () -> Unit, onOpenAnalysis: (String) -> Unit, onOpenPaywall: () -> Unit) {
    val viewModel: HistoryViewModel = koinViewModel()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    HistoryContent(
        state = state,
        onBack = onBack,
        onOpenAnalysis = onOpenAnalysis,
        onRefresh = viewModel::refresh,
        onErrorAction = { action -> if (action == FriendlyAction.OPEN_PAYWALL) onOpenPaywall() else viewModel.refresh() },
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
 * @param modifier modifier of the screen.
 */
@Composable
fun HistoryContent(
    state: ContentState<List<AnalysisSummary>>,
    onBack: () -> Unit,
    onOpenAnalysis: (String) -> Unit,
    onRefresh: () -> Unit,
    onErrorAction: (FriendlyAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    JaugeScreenScaffold(
        modifier = modifier,
        title = stringResource(Res.string.history_title),
        navigationIcon = { RoundIconButton(JaugeIcons.Back, stringResource(Res.string.history_back), onBack) },
    ) {
        JaugeRefreshContainer(isRefreshing = state.isRefreshing, onRefresh = onRefresh, modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = JaugeTheme.spacing.screenGutter)
                    .padding(top = 12.dp, bottom = 24.dp),
            ) {
                val analyses = state.content
                when {
                    state.isLoading -> SkeletonList(rowCount = 5)
                    state.showsFullScreenError -> FriendlyErrorView(error = state.error!!, onAction = onErrorAction)
                    analyses.isNullOrEmpty() -> EmptyState(
                        title = stringResource(Res.string.home_empty_title),
                        message = stringResource(Res.string.home_empty_message),
                    )
                    else -> AnalysisListCard(analyses = analyses, onOpenAnalysis = onOpenAnalysis)
                }
            }
        }
    }
}
