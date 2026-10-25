package io.jauge.feature.analysis.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import io.jauge.feature.analysis.favorites.FavoriteFailureMessages
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarHost
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.jauge.core.designsystem.component.EmptyState
import io.jauge.core.designsystem.component.JaugeRefreshContainer
import io.jauge.core.designsystem.component.SkeletonList
import io.jauge.core.designsystem.theme.JaugePalette
import io.jauge.core.designsystem.theme.JaugeTheme
import io.jauge.core.domain.model.AnalysisSummary
import io.jauge.core.ui.error.FriendlyAction
import io.jauge.core.ui.error.FriendlyErrorView
import io.jauge.feature.analysis.components.AnalysisListCard
import io.jauge.feature.analysis.picker.rememberImagePicker
import io.jauge.feature.analysis.resources.Res
import io.jauge.feature.analysis.resources.home_empty_message
import io.jauge.feature.analysis.resources.home_empty_title
import io.jauge.feature.analysis.resources.home_greeting
import io.jauge.feature.analysis.resources.home_greeting_anonymous
import io.jauge.feature.analysis.resources.home_greeting_evening
import io.jauge.feature.analysis.resources.home_greeting_evening_anonymous
import io.jauge.feature.analysis.resources.home_headline
import io.jauge.feature.analysis.resources.home_history_title
import io.jauge.feature.analysis.resources.home_see_all
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

/** Number of analyses shown on the home screen; the others are behind "Tout voir". */
private const val HOME_HISTORY_COUNT = 3

/**
 * Home screen: one composer to paste a link, pick a screenshot or take a photo, and the last analyses (mockup 02). Tab root.
 *
 * @param onStartReading opens the reading screen for a request.
 * @param onOpenAnalysis opens the verdict of an analysis.
 * @param onOpenHistory "Tout voir".
 * @param onOpenPaywall opens Jauge Plus when the free analysis is used.
 */
@Composable
fun HomeScreen(
    onStartReading: (String) -> Unit,
    onOpenAnalysis: (String) -> Unit,
    onOpenHistory: () -> Unit,
    onOpenPaywall: () -> Unit,
) {
    val viewModel: HomeViewModel = koinViewModel()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val picker = rememberImagePicker(onResult = viewModel::onImagePicked)
    val clipboard = rememberClipboardReader()
    // Back to the front: greeting of the moment and what the system says about the clipboard (never its content).
    val isWindowFocused = LocalWindowInfo.current.isWindowFocused
    LaunchedEffect(isWindowFocused) {
        if (isWindowFocused) {
            viewModel.updateGreeting()
            viewModel.onClipboardChecked(clipboard.mayContainLink())
        }
    }
    LaunchedEffect(state.startedRequestId) {
        state.startedRequestId?.let { requestId ->
            viewModel.onReadingOpened()
            onStartReading(requestId)
        }
    }
    val snackbarHostState = remember { SnackbarHostState() }
    FavoriteFailureMessages(failures = viewModel.favoriteFailures, snackbarHostState = snackbarHostState)
    Box(modifier = Modifier.fillMaxSize()) {
        HomeContent(
            state = state,
            onLinkChange = viewModel::onLinkChange,
            onAnalyzeLink = viewModel::analyzeLink,
            onPaste = { viewModel.onPasted(clipboard.readText()) },
            onClearLink = viewModel::clearLink,
            onPickScreenshot = picker::pickScreenshot,
            onTakePhoto = picker::takePhoto,
            onOpenAnalysis = onOpenAnalysis,
            onOpenHistory = onOpenHistory,
            onRefresh = viewModel::refresh,
            onErrorAction = { action -> if (action == FriendlyAction.OPEN_PAYWALL) onOpenPaywall() else viewModel.refresh() },
            onToggleFavorite = viewModel::toggleFavorite,
        )
        SnackbarHost(hostState = snackbarHostState, modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 16.dp))
    }
}

/**
 * Stateless content of the home screen, also used by the screenshot tests.
 *
 * @param state state to draw.
 * @param onLinkChange link typed or pasted.
 * @param onAnalyzeLink "Jauger".
 * @param onPickScreenshot "Capture".
 * @param onTakePhoto "Photo".
 * @param onOpenAnalysis tap on an analysis.
 * @param onOpenHistory "Tout voir".
 * @param onRefresh pull to refresh.
 * @param onErrorAction action of a friendly error.
 * @param modifier modifier of the screen.
 * @param animateGauges false to draw the gauges at once.
 * @param onToggleFavorite star of an analysis tapped, with its new value.
 * @param onPaste "Coller": reads the clipboard.
 * @param onClearLink cross of the link field.
 */
@Composable
fun HomeContent(
    state: HomeUiState,
    onLinkChange: (String) -> Unit,
    onAnalyzeLink: () -> Unit,
    onPickScreenshot: () -> Unit,
    onTakePhoto: () -> Unit,
    onOpenAnalysis: (String) -> Unit,
    onOpenHistory: () -> Unit,
    onRefresh: () -> Unit,
    onErrorAction: (FriendlyAction) -> Unit,
    modifier: Modifier = Modifier,
    animateGauges: Boolean = true,
    onToggleFavorite: (String, Boolean) -> Unit = { _, _ -> },
    onPaste: () -> Unit = {},
    onClearLink: () -> Unit = {},
) {
    val colors = JaugeTheme.colors
    val typography = JaugeTheme.typography
    val gutter = JaugeTheme.spacing.screenGutter
    JaugeRefreshContainer(
        isRefreshing = state.history.isRefreshing,
        onRefresh = onRefresh,
        modifier = modifier.fillMaxSize().background(colors.background),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = gutter)
                .padding(top = 12.dp, bottom = 24.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = greeting(state),
                        style = typography.body.copy(fontSize = 16.sp),
                        color = colors.textSecondary,
                    )
                    Text(
                        text = stringResource(Res.string.home_headline),
                        style = typography.headline,
                        color = colors.textPrimary,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }
                if (state.initials.isNotEmpty()) {
                    Box(
                        modifier = Modifier.size(46.dp).background(JaugePalette.Amber, CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = state.initials,
                            style = typography.title.copy(fontSize = 18.sp, letterSpacing = 0.sp),
                            color = JaugePalette.Ink,
                        )
                    }
                }
            }
            OfferInput(
                state = state,
                onLinkChange = onLinkChange,
                onClearLink = onClearLink,
                onPaste = onPaste,
                onAnalyze = onAnalyzeLink,
                onPickScreenshot = onPickScreenshot,
                onTakePhoto = onTakePhoto,
                modifier = Modifier.padding(top = 20.dp),
            )
            HistorySection(
                state = state,
                onOpenAnalysis = onOpenAnalysis,
                onOpenHistory = onOpenHistory,
                onErrorAction = onErrorAction,
                animateGauges = animateGauges,
                onToggleFavorite = onToggleFavorite,
            )
        }
    }
}

@Composable
private fun HistorySection(
    state: HomeUiState,
    onOpenAnalysis: (String) -> Unit,
    onOpenHistory: () -> Unit,
    onErrorAction: (FriendlyAction) -> Unit,
    animateGauges: Boolean,
    onToggleFavorite: (String, Boolean) -> Unit,
) {
    val colors = JaugeTheme.colors
    val history = state.history
    val analyses: List<AnalysisSummary> = history.content.orEmpty()
    Row(modifier = Modifier.fillMaxWidth().padding(top = 26.dp), verticalAlignment = Alignment.Bottom) {
        Text(
            text = stringResource(Res.string.home_history_title),
            style = JaugeTheme.typography.sectionTitle,
            color = colors.textPrimary,
            modifier = Modifier.weight(1f),
        )
        if (analyses.isNotEmpty()) {
            Text(
                text = stringResource(Res.string.home_see_all),
                style = JaugeTheme.typography.label.copy(fontSize = 14.sp),
                color = colors.textSecondary,
                modifier = Modifier.clickable(role = Role.Button, onClick = onOpenHistory).padding(4.dp),
            )
        }
    }
    when {
        history.isLoading && analyses.isEmpty() -> SkeletonList(rowCount = HOME_HISTORY_COUNT, modifier = Modifier.padding(top = 12.dp))
        // An empty list that failed to refresh is unknown, not empty: show the friendly error, never "no analysis yet".
        history.error != null && analyses.isEmpty() -> FriendlyErrorView(error = history.error!!, onAction = onErrorAction)
        analyses.isEmpty() -> EmptyState(
            title = stringResource(Res.string.home_empty_title),
            message = stringResource(Res.string.home_empty_message),
        )
        else -> AnalysisListCard(
            analyses = analyses.take(HOME_HISTORY_COUNT),
            onOpenAnalysis = onOpenAnalysis,
            animateGauges = animateGauges,
            modifier = Modifier.padding(top = 12.dp),
            onToggleFavorite = onToggleFavorite,
        )
    }
}

/** "Bonjour Inès" during the day, "Bonsoir Inès" from 18 h, without the name when signed out. */
@Composable
private fun greeting(state: HomeUiState): String {
    val evening = state.greetingMoment == GreetingMoment.EVENING
    return state.firstName?.let { stringResource(if (evening) Res.string.home_greeting_evening else Res.string.home_greeting, it) }
        ?: stringResource(if (evening) Res.string.home_greeting_evening_anonymous else Res.string.home_greeting_anonymous)
}
