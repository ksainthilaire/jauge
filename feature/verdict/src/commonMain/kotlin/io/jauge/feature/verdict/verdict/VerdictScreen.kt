package io.jauge.feature.verdict.verdict

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.jauge.core.designsystem.component.FavoriteStarButton
import io.jauge.core.designsystem.component.GaugeLevel
import io.jauge.core.designsystem.component.GaugeSize
import io.jauge.core.designsystem.component.JaugeButton
import io.jauge.core.designsystem.component.JaugeButtonStyle
import io.jauge.core.designsystem.component.JaugeCard
import io.jauge.core.designsystem.component.JaugeGauge
import io.jauge.core.designsystem.component.JaugeRefreshContainer
import io.jauge.core.designsystem.component.JaugeScreenScaffold
import io.jauge.core.designsystem.component.LevelPill
import io.jauge.core.designsystem.component.MetricTile
import io.jauge.core.designsystem.component.RoundIconButton
import io.jauge.core.designsystem.component.SkeletonCard
import io.jauge.core.designsystem.component.skeleton
import io.jauge.core.designsystem.icon.JaugeIcons
import io.jauge.core.designsystem.theme.JaugeTheme
import io.jauge.core.domain.model.Analysis
import io.jauge.core.domain.model.TimingAdvice
import io.jauge.core.ui.error.FriendlyErrorView
import io.jauge.core.ui.state.ContentState
import io.jauge.feature.verdict.common.formatGrouped
import io.jauge.feature.verdict.common.joinWithDots
import io.jauge.feature.verdict.common.label
import io.jauge.feature.verdict.common.labelOrNull
import io.jauge.feature.verdict.common.signedPercent
import io.jauge.feature.verdict.resources.Res
import io.jauge.feature.verdict.resources.action_back
import io.jauge.feature.verdict.resources.action_share
import io.jauge.feature.verdict.resources.approximately_number
import io.jauge.feature.verdict.resources.months_short
import io.jauge.feature.verdict.resources.verdict_applicants_label
import io.jauge.feature.verdict.resources.verdict_applicants_range
import io.jauge.feature.verdict.resources.verdict_details
import io.jauge.feature.verdict.resources.verdict_favorite_add
import io.jauge.feature.verdict.resources.verdict_favorite_add_failed
import io.jauge.feature.verdict.resources.verdict_favorite_remove
import io.jauge.feature.verdict.resources.verdict_favorite_remove_failed
import io.jauge.feature.verdict.resources.verdict_estimate_line
import io.jauge.feature.verdict.resources.verdict_how
import io.jauge.feature.verdict.resources.verdict_less_than_offers
import io.jauge.feature.verdict.resources.verdict_more_than_offers
import io.jauge.feature.verdict.resources.verdict_salary_displayed
import io.jauge.feature.verdict.resources.verdict_salary_label
import io.jauge.feature.verdict.resources.verdict_salary_not_displayed
import io.jauge.feature.verdict.resources.verdict_salary_unknown
import io.jauge.feature.verdict.resources.verdict_screen_title
import io.jauge.feature.verdict.resources.verdict_timing_apply_before
import io.jauge.feature.verdict.resources.verdict_timing_detail
import io.jauge.feature.verdict.resources.verdict_title
import io.jauge.feature.verdict.resources.verdict_trend_caption
import io.jauge.feature.verdict.resources.verdict_trend_label
import io.jauge.feature.verdict.resources.verdict_twins_label
import io.jauge.feature.verdict.resources.verdict_twins_radius
import io.jauge.feature.verdict.resources.verdict_value_unknown
import io.jauge.feature.verdict.resources.weekdays
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.resources.stringArrayResource
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

/**
 * Competition gauge 0 to 100 and key figures of an analysis (mockup 04).
 *
 * @param analysisId identifier of the analysis.
 * @param onBack called when the user leaves the screen.
 * @param onShare opens the share card of the analysis.
 * @param onOpenDetails opens the details (salaries, skills, trend).
 * @param onOpenMethodology opens "Comment on estime".
 * @param onOpenTwins opens the twin offers of the analysis.
 * @param onOpenTrend opens the details on the trend section.
 */
@Composable
fun VerdictScreen(
    analysisId: String,
    onBack: () -> Unit,
    onShare: (String) -> Unit,
    onOpenDetails: (String) -> Unit,
    onOpenMethodology: (String) -> Unit,
    onOpenTwins: (String) -> Unit,
    onOpenTrend: (String) -> Unit,
) {
    val viewModel: VerdictViewModel = koinViewModel(key = "verdict-$analysisId") { parametersOf(analysisId) }
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val isFavorite by viewModel.isFavorite.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(viewModel) {
        viewModel.favoriteFailures.collect { failure ->
            snackbarHostState.showSnackbar(
                getString(if (failure.wasAdding) Res.string.verdict_favorite_add_failed else Res.string.verdict_favorite_remove_failed),
            )
        }
    }
    VerdictLayout(
        state = state,
        isFavorite = isFavorite,
        onToggleFavorite = viewModel::toggleFavorite,
        snackbarHostState = snackbarHostState,
        onBack = onBack,
        onRefresh = viewModel::refresh,
        onShare = { onShare(analysisId) },
        onOpenDetails = { onOpenDetails(analysisId) },
        onOpenMethodology = { onOpenMethodology(analysisId) },
        onOpenTwins = { onOpenTwins(analysisId) },
        onOpenTrend = { onOpenTrend(analysisId) },
    )
}

/**
 * Stateless layout of the verdict screen, also used by screenshot tests.
 *
 * @param state observed state.
 * @param onBack back action.
 * @param onRefresh reloads the analysis.
 * @param onShare opens the share card.
 * @param onOpenDetails opens the details.
 * @param onOpenMethodology opens the methodology.
 * @param onOpenTwins opens the twin offers.
 * @param onOpenTrend opens the details on the trend.
 * @param animateGauge false to draw the gauge at its final position (screenshots).
 * @param isFavorite true when the analysis is a favorite (filled star).
 * @param onToggleFavorite star tapped, with its new value.
 * @param snackbarHostState host of the short messages (star refused by the server).
 */
@Composable
fun VerdictLayout(
    state: ContentState<Analysis>,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onShare: () -> Unit,
    onOpenDetails: () -> Unit,
    onOpenMethodology: () -> Unit,
    onOpenTwins: () -> Unit,
    onOpenTrend: () -> Unit,
    animateGauge: Boolean = true,
    isFavorite: Boolean = false,
    onToggleFavorite: (Boolean) -> Unit = {},
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
) {
    val analysis = state.content
    JaugeScreenScaffold(
        snackbarHostState = snackbarHostState,
        title = analysis?.let { titleOf(it) } ?: stringResource(Res.string.verdict_title),
        navigationIcon = { RoundIconButton(JaugeIcons.Back, stringResource(Res.string.action_back), onBack) },
        action = if (analysis != null) {
            { RoundIconButton(JaugeIcons.Share, stringResource(Res.string.action_share), onShare) }
        } else {
            null
        },
        bottomBar = if (analysis != null) {
            {
                Row(horizontalArrangement = Arrangement.spacedBy(JaugeTheme.spacing.medium)) {
                    JaugeButton(
                        text = stringResource(Res.string.verdict_details),
                        onClick = onOpenDetails,
                        style = JaugeButtonStyle.OUTLINED,
                        modifier = Modifier
                            .weight(1f)
                            .background(JaugeTheme.colors.surface, JaugeTheme.radii.buttonShape),
                    )
                    JaugeButton(
                        text = stringResource(Res.string.action_share),
                        onClick = onShare,
                        leadingIcon = JaugeIcons.Share,
                        modifier = Modifier.weight(1.4f),
                    )
                }
            }
        } else {
            null
        },
    ) {
        JaugeRefreshContainer(
            isRefreshing = state.isRefreshing,
            onRefresh = onRefresh,
            modifier = Modifier.fillMaxSize(),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = JaugeTheme.spacing.screenGutter)
                    .padding(top = 6.dp, bottom = JaugeTheme.spacing.extraLarge),
            ) {
                when {
                    analysis != null -> VerdictContent(
                        analysis = analysis,
                        onOpenMethodology = onOpenMethodology,
                        onOpenTwins = onOpenTwins,
                        onOpenTrend = onOpenTrend,
                        animateGauge = animateGauge,
                        isFavorite = isFavorite,
                        onToggleFavorite = onToggleFavorite,
                    )
                    state.showsFullScreenError -> FriendlyErrorView(error = state.error!!, onAction = { onRefresh() })
                    else -> VerdictSkeleton()
                }
            }
        }
    }
}

@Composable
private fun titleOf(analysis: Analysis): String {
    val date = analysis.createdAt.toLocalDateTime(TimeZone.currentSystemDefault()).date
    val months = stringArrayResource(Res.array.months_short)
    val month = months.getOrElse(date.month.ordinal) { "" }
    return stringResource(Res.string.verdict_screen_title, date.day, month)
}

@Composable
private fun VerdictContent(
    analysis: Analysis,
    onOpenMethodology: () -> Unit,
    onOpenTwins: () -> Unit,
    onOpenTrend: () -> Unit,
    animateGauge: Boolean,
    isFavorite: Boolean,
    onToggleFavorite: (Boolean) -> Unit,
) {
    val colors = JaugeTheme.colors
    val offer = analysis.offer
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = joinWithDots(offer.company, offer.city, offer.contractType.labelOrNull()),
                style = JaugeTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                color = colors.textSecondary,
            )
            Text(
                text = offer.jobTitle,
                style = JaugeTheme.typography.title,
                color = colors.textPrimary,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
        FavoriteStarButton(
            isFavorite = isFavorite,
            onToggle = onToggleFavorite,
            contentDescription = stringResource(if (isFavorite) Res.string.verdict_favorite_remove else Res.string.verdict_favorite_add),
        )
    }
    VerdictGaugeCard(analysis = analysis, onOpenMethodology = onOpenMethodology, animateGauge = animateGauge)
    MetricGrid(analysis = analysis, onOpenTwins = onOpenTwins, onOpenTrend = onOpenTrend)
    // The advice written by the server replaces the computed timing sentence; without it the screen keeps it.
    val advice = analysis.advice
    if (advice != null) AdviceCard(advice.text) else analysis.timingAdvice?.let { TimingAdviceCard(it) }
}

/**
 * Advice of the server (`result.advice.text`) in the card of the timing advice: its first sentence in bold, like
 * "Postule avant jeudi.".
 */
@Composable
private fun AdviceCard(text: String) {
    val colors = JaugeTheme.colors
    val firstSentenceEnd = text.indexOf(". ").takeIf { it > 0 }?.plus(1) ?: text.length
    JaugeCard(
        modifier = Modifier.fillMaxWidth().padding(top = JaugeTheme.spacing.medium),
        containerColor = colors.disputed.container,
        borderColor = colors.disputed.accent.copy(alpha = 0.35f),
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 13.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(JaugeIcons.Clock, contentDescription = null, tint = colors.disputed.content, modifier = Modifier.size(20.dp))
            Text(
                text = buildAnnotatedString {
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(text.substring(0, firstSentenceEnd)) }
                    append(text.substring(firstSentenceEnd))
                },
                style = JaugeTheme.typography.body.copy(fontSize = 14.sp, lineHeight = 19.sp, fontWeight = FontWeight.Normal),
                color = colors.textPrimary,
            )
        }
    }
}

@Composable
private fun VerdictGaugeCard(analysis: Analysis, onOpenMethodology: () -> Unit, animateGauge: Boolean) {
    val colors = JaugeTheme.colors
    val level = GaugeLevel.valueOf(analysis.level.name)
    // The verdict card follows the theme: ink on a light screen, a raised dark card on a dark screen (never a bright
    // cream block at night).
    JaugeCard(
        modifier = Modifier.fillMaxWidth().padding(top = 14.dp),
        containerColor = colors.emphasisSurface,
        borderColor = colors.emphasisOutline,
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 18.dp),
    ) {
        // The score lives inside the arc, the cursor rides the arc: nothing ever covers the number, and the arc keeps
        // its margin at any width and text size.
        JaugeGauge(
            score = analysis.score,
            size = GaugeSize.LARGE,
            contentColor = colors.onEmphasisSurface,
            surfaceColor = colors.emphasisSurface,
            showOutOf = true,
            animate = animateGauge,
            modifier = Modifier.fillMaxWidth().widthIn(max = GAUGE_MAXIMUM_WIDTH).align(Alignment.CenterHorizontally),
        )
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            val comparison = if (analysis.percentileInRegion >= 50) {
                stringResource(Res.string.verdict_more_than_offers, analysis.percentileInRegion, analysis.regionName)
            } else {
                stringResource(Res.string.verdict_less_than_offers, 100 - analysis.percentileInRegion, analysis.regionName)
            }
            LevelPill(
                level = level,
                text = joinWithDots(stringResource(level.label), comparison),
                showDot = true,
                modifier = Modifier.padding(top = 4.dp),
            )
            Text(
                text = buildAnnotatedString {
                    append(stringResource(Res.string.verdict_estimate_line, analysis.reliability.label().lowercase()))
                    withStyle(SpanStyle(color = colors.onEmphasisSurface, textDecoration = TextDecoration.Underline)) {
                        append(stringResource(Res.string.verdict_how))
                    }
                },
                style = JaugeTheme.typography.caption,
                color = colors.onEmphasisSurfaceSecondary,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .padding(top = 8.dp)
                    .clickable(role = Role.Button, onClick = onOpenMethodology),
            )
        }
    }
}

@Composable
private fun MetricGrid(analysis: Analysis, onOpenTwins: () -> Unit, onOpenTrend: () -> Unit) {
    val colors = JaugeTheme.colors
    val spacing = JaugeTheme.spacing.medium
    Column(modifier = Modifier.padding(top = spacing), verticalArrangement = Arrangement.spacedBy(spacing)) {
        Row(horizontalArrangement = Arrangement.spacedBy(spacing)) {
            MetricTile(
                label = stringResource(Res.string.verdict_applicants_label),
                value = stringResource(Res.string.approximately_number, formatGrouped(analysis.applicants.estimate)),
                caption = stringResource(
                    Res.string.verdict_applicants_range,
                    formatGrouped(analysis.applicants.low),
                    formatGrouped(analysis.applicants.high),
                ),
                modifier = Modifier.weight(1f),
            )
            val salary = analysis.estimatedSalary?.takeIf { it.isValid }
            MetricTile(
                label = stringResource(Res.string.verdict_salary_label),
                value = salary?.label() ?: stringResource(Res.string.verdict_value_unknown),
                caption = when {
                    salary == null -> stringResource(Res.string.verdict_salary_unknown)
                    analysis.offer.displayedSalary != null -> stringResource(Res.string.verdict_salary_displayed)
                    else -> stringResource(Res.string.verdict_salary_not_displayed)
                },
                modifier = Modifier.weight(1f),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(spacing)) {
            MetricTile(
                label = stringResource(Res.string.verdict_twins_label),
                value = formatGrouped(analysis.twinOfferCount),
                caption = stringResource(Res.string.verdict_twins_radius, analysis.twinRadiusKilometers),
                onClick = onOpenTwins,
                modifier = Modifier.weight(1f),
            )
            val trend = analysis.trendThreeMonthsPercent
            MetricTile(
                label = stringResource(Res.string.verdict_trend_label),
                value = signedPercent(trend),
                caption = stringResource(Res.string.verdict_trend_caption),
                valueColor = when {
                    trend > 0 -> colors.crowded.content
                    trend < 0 -> colors.calm.content
                    else -> colors.textPrimary
                },
                onClick = onOpenTrend,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun TimingAdviceCard(advice: TimingAdvice) {
    val colors = JaugeTheme.colors
    val weekdays = stringArrayResource(Res.array.weekdays)
    val day = weekdays.getOrElse(advice.applyBefore.ordinal) { "" }
    JaugeCard(
        modifier = Modifier.fillMaxWidth().padding(top = JaugeTheme.spacing.medium),
        containerColor = colors.disputed.container,
        borderColor = colors.disputed.accent.copy(alpha = 0.35f),
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 13.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(JaugeIcons.Clock, contentDescription = null, tint = colors.disputed.content, modifier = Modifier.size(20.dp))
            Text(
                text = buildAnnotatedString {
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                        append(stringResource(Res.string.verdict_timing_apply_before, day))
                    }
                    append(stringResource(Res.string.verdict_timing_detail, advice.shareOfApplicationsPercent, advice.withinDays))
                },
                style = JaugeTheme.typography.body.copy(fontSize = 14.sp, lineHeight = 19.sp, fontWeight = FontWeight.Normal),
                color = colors.textPrimary,
            )
        }
    }
}

@Composable
private fun VerdictSkeleton() {
    Box(Modifier.fillMaxWidth(0.5f).height(14.dp).skeleton())
    Box(Modifier.padding(top = 8.dp).fillMaxWidth(0.85f).height(26.dp).skeleton())
    SkeletonCard(modifier = Modifier.fillMaxWidth().padding(top = 14.dp), height = 200.dp)
    Spacer(Modifier.height(JaugeTheme.spacing.medium))
    Row(horizontalArrangement = Arrangement.spacedBy(JaugeTheme.spacing.medium)) {
        Box(Modifier.weight(1f).height(96.dp).skeleton(JaugeTheme.radii.cardShape))
        Box(Modifier.weight(1f).height(96.dp).skeleton(JaugeTheme.radii.cardShape))
    }
    Spacer(Modifier.height(JaugeTheme.spacing.medium))
    Row(horizontalArrangement = Arrangement.spacedBy(JaugeTheme.spacing.medium)) {
        Box(Modifier.weight(1f).height(96.dp).skeleton(JaugeTheme.radii.cardShape))
        Box(Modifier.weight(1f).height(96.dp).skeleton(JaugeTheme.radii.cardShape))
    }
}

/** Space between the bottom of the gauge (needle pivot) and the score. */
/** Widest verdict gauge: on tablets and in landscape the arc stays a comfortable size. */
private val GAUGE_MAXIMUM_WIDTH = 360.dp
