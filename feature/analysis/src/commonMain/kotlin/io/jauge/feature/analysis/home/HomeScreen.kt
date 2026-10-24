package io.jauge.feature.analysis.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.jauge.core.designsystem.component.EmptyState
import io.jauge.core.designsystem.component.JaugeButton
import io.jauge.core.designsystem.component.JaugeButtonSize
import io.jauge.core.designsystem.component.JaugeButtonStyle
import io.jauge.core.designsystem.component.JaugeCard
import io.jauge.core.designsystem.component.JaugeRefreshContainer
import io.jauge.core.designsystem.component.SkeletonList
import io.jauge.core.designsystem.icon.JaugeIcons
import io.jauge.core.designsystem.theme.JaugePalette
import io.jauge.core.designsystem.theme.JaugeTheme
import io.jauge.core.domain.model.AnalysisSummary
import io.jauge.core.ui.error.FriendlyAction
import io.jauge.core.ui.error.FriendlyErrorView
import io.jauge.core.ui.error.toFriendlyMessage
import io.jauge.feature.analysis.components.AnalysisListCard
import io.jauge.feature.analysis.picker.rememberImagePicker
import io.jauge.feature.analysis.resources.Res
import io.jauge.feature.analysis.resources.home_analyze
import io.jauge.feature.analysis.resources.home_empty_message
import io.jauge.feature.analysis.resources.home_empty_title
import io.jauge.feature.analysis.resources.home_greeting
import io.jauge.feature.analysis.resources.home_greeting_anonymous
import io.jauge.feature.analysis.resources.home_headline
import io.jauge.feature.analysis.resources.home_history_title
import io.jauge.feature.analysis.resources.home_image_unreadable
import io.jauge.feature.analysis.resources.home_link_label
import io.jauge.feature.analysis.resources.home_link_placeholder
import io.jauge.feature.analysis.resources.home_photo
import io.jauge.feature.analysis.resources.home_screenshot
import io.jauge.feature.analysis.resources.home_see_all
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

/** Number of analyses shown on the home screen; the others are behind "Tout voir". */
private const val HOME_HISTORY_COUNT = 3

/**
 * Home screen: paste an offer, pick a screenshot or take a photo, and the last analyses (mockup 02). Tab root.
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
    LaunchedEffect(state.startedRequestId) {
        state.startedRequestId?.let { requestId ->
            viewModel.onReadingOpened()
            onStartReading(requestId)
        }
    }
    HomeContent(
        state = state,
        onLinkChange = viewModel::onLinkChange,
        onAnalyzeLink = viewModel::analyzeLink,
        onPickScreenshot = picker::pickScreenshot,
        onTakePhoto = picker::takePhoto,
        onOpenAnalysis = onOpenAnalysis,
        onOpenHistory = onOpenHistory,
        onRefresh = viewModel::refresh,
        onErrorAction = { action -> if (action == FriendlyAction.OPEN_PAYWALL) onOpenPaywall() else viewModel.refresh() },
    )
}

/**
 * Stateless content of the home screen, also used by the screenshot tests.
 *
 * @param state state to draw.
 * @param onLinkChange link typed or pasted.
 * @param onAnalyzeLink "Jauger cette offre".
 * @param onPickScreenshot "Une capture".
 * @param onTakePhoto "Photographier".
 * @param onOpenAnalysis tap on an analysis.
 * @param onOpenHistory "Tout voir".
 * @param onRefresh pull to refresh.
 * @param onErrorAction action of a friendly error.
 * @param modifier modifier of the screen.
 * @param animateGauges false to draw the gauges at once.
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
                        text = (state.firstName?.let { stringResource(Res.string.home_greeting, it) }
                            ?: stringResource(Res.string.home_greeting_anonymous)).uppercase(),
                        style = typography.overline,
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
            PasteCard(
                link = state.link,
                errorMessage = state.linkError?.let { stringResource(it.toFriendlyMessage().message) },
                onLinkChange = onLinkChange,
                onAnalyze = onAnalyzeLink,
                modifier = Modifier.padding(top = 20.dp),
            )
            Row(modifier = Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SourceCard(
                    text = stringResource(Res.string.home_screenshot),
                    icon = JaugeIcons.Image,
                    iconColor = colors.calm.content,
                    iconBackground = colors.calm.container,
                    onClick = onPickScreenshot,
                    modifier = Modifier.weight(1f),
                )
                SourceCard(
                    text = stringResource(Res.string.home_photo),
                    icon = JaugeIcons.Camera,
                    iconColor = colors.disputed.content,
                    iconBackground = colors.disputed.container,
                    onClick = onTakePhoto,
                    modifier = Modifier.weight(1f),
                )
            }
            if (state.isImageUnreadable) {
                Text(
                    text = stringResource(Res.string.home_image_unreadable),
                    style = typography.bodySmall,
                    color = colors.crowded.content,
                    modifier = Modifier.padding(top = 10.dp, start = 4.dp),
                )
            }
            HistorySection(state = state, onOpenAnalysis = onOpenAnalysis, onOpenHistory = onOpenHistory, onErrorAction = onErrorAction, animateGauges = animateGauges)
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
        )
    }
}

@Composable
private fun PasteCard(
    link: String,
    errorMessage: String?,
    onLinkChange: (String) -> Unit,
    onAnalyze: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = JaugeTheme.colors
    val typography = JaugeTheme.typography
    JaugeCard(
        modifier = modifier.fillMaxWidth(),
        borderColor = if (errorMessage != null) colors.crowded.accent else colors.textPrimary,
        borderWidth = 2.dp,
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(JaugeIcons.Link, contentDescription = null, tint = colors.textSecondary, modifier = Modifier.size(17.dp))
            Text(text = stringResource(Res.string.home_link_label), style = typography.label, color = colors.textSecondary)
        }
        val textStyle = typography.numberSmall.copy(fontSize = 13.5.sp, fontWeight = FontWeight.Medium, lineHeight = 19.5.sp, color = colors.textPrimary)
        BasicTextField(
            value = link,
            onValueChange = onLinkChange,
            textStyle = textStyle,
            cursorBrush = SolidColor(colors.action),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go),
            keyboardActions = KeyboardActions(onGo = { onAnalyze() }),
            maxLines = 3,
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            decorationBox = { inner ->
                Box {
                    if (link.isEmpty()) {
                        Text(text = stringResource(Res.string.home_link_placeholder), style = textStyle.copy(color = colors.textTertiary))
                    }
                    inner()
                }
            },
        )
        if (errorMessage != null) {
            Text(
                text = errorMessage,
                style = typography.bodySmall,
                color = colors.crowded.content,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
        JaugeButton(
            text = stringResource(Res.string.home_analyze),
            onClick = onAnalyze,
            style = JaugeButtonStyle.NEUTRAL,
            size = JaugeButtonSize.MEDIUM,
            trailingIcon = JaugeIcons.ArrowRight,
            modifier = Modifier.fillMaxWidth().padding(top = 14.dp),
        )
    }
}

@Composable
private fun SourceCard(
    text: String,
    icon: ImageVector,
    iconColor: Color,
    iconBackground: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    JaugeCard(modifier = modifier, onClick = onClick, contentPadding = PaddingValues(horizontal = 12.dp, vertical = 14.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier.size(36.dp).background(iconBackground, RoundedCornerShape(JaugeTheme.radii.iconTile)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, contentDescription = null, tint = iconColor, modifier = Modifier.size(20.dp))
            }
            Text(
                text = text,
                style = JaugeTheme.typography.body.copy(fontWeight = FontWeight.SemiBold, fontSize = 14.5.sp),
                color = JaugeTheme.colors.textPrimary,
                // Two lines with large text sizes (accessibility settings) rather than a cut word.
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
