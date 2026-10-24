package io.jauge.feature.twins.twinoffers

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Icon
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.jauge.core.common.link.WebLinks
import io.jauge.core.designsystem.component.EmptyState
import io.jauge.core.designsystem.component.GaugeLevel
import io.jauge.core.designsystem.component.GaugeSize
import io.jauge.core.designsystem.component.JaugeCard
import io.jauge.core.designsystem.component.JaugeChip
import io.jauge.core.designsystem.component.JaugeChipStyle
import io.jauge.core.designsystem.component.JaugeGauge
import io.jauge.core.designsystem.component.JaugeRefreshContainer
import io.jauge.core.designsystem.component.JaugeScreenScaffold
import io.jauge.core.designsystem.component.JaugeSwitch
import io.jauge.core.designsystem.component.LevelPill
import io.jauge.core.designsystem.component.RoundIconButton
import io.jauge.core.designsystem.component.SkeletonList
import io.jauge.core.designsystem.icon.JaugeIcons
import io.jauge.core.designsystem.theme.JaugeTheme
import io.jauge.core.domain.model.TwinOffer
import io.jauge.core.domain.model.TwinSort
import io.jauge.core.ui.error.FriendlyErrorView
import io.jauge.core.ui.error.toFriendlyMessage
import io.jauge.feature.twins.resources.Res
import io.jauge.feature.twins.resources.action_back
import io.jauge.feature.twins.resources.twin_offers_alert_message
import io.jauge.feature.twins.resources.twin_offers_alert_off
import io.jauge.feature.twins.resources.twin_offers_alert_on
import io.jauge.feature.twins.resources.twin_offers_alert_title
import io.jauge.feature.twins.resources.twin_offers_applicants
import io.jauge.feature.twins.resources.twin_offers_company_place
import io.jauge.feature.twins.resources.twin_offers_empty_action
import io.jauge.feature.twins.resources.twin_offers_empty_message
import io.jauge.feature.twins.resources.twin_offers_empty_title
import io.jauge.feature.twins.resources.twin_offers_headline
import io.jauge.feature.twins.resources.twin_offers_no_twins_message
import io.jauge.feature.twins.resources.twin_offers_no_twins_title
import io.jauge.feature.twins.resources.twin_offers_open_offer
import io.jauge.feature.twins.resources.twin_offers_overline
import io.jauge.feature.twins.resources.twin_offers_salary
import io.jauge.feature.twins.resources.twin_offers_sort_best_paid
import io.jauge.feature.twins.resources.twin_offers_sort_least_disputed
import io.jauge.feature.twins.resources.twin_offers_sort_most_recent
import io.jauge.feature.twins.resources.twin_offers_title
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

/**
 * Same job, fewer applicants, and the calm alert (mockup 06, tab "Veille").
 *
 * @param analysisId analysis whose twins are shown, null for the tab (most recent analysis).
 * @param onBack back action when opened from a verdict, null for the tab root.
 * @param onAnalyzeOffer opens the paste screen when the user has no analysis yet.
 */
@Composable
fun TwinOffersScreen(
    analysisId: String?,
    onBack: (() -> Unit)?,
    onAnalyzeOffer: () -> Unit,
) {
    val viewModel: TwinOffersViewModel = koinViewModel(key = "twins-$analysisId") { parametersOf(analysisId) }
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            val message = when (event) {
                is TwinOffersEvent.AlertChanged -> if (event.isOn) {
                    getString(Res.string.twin_offers_alert_on, event.threshold)
                } else {
                    getString(Res.string.twin_offers_alert_off)
                }
                is TwinOffersEvent.AlertFailed -> getString(event.error.toFriendlyMessage().title)
            }
            snackbarHostState.showSnackbar(message)
        }
    }
    TwinOffersLayout(
        state = state,
        snackbarHostState = snackbarHostState,
        onBack = onBack,
        onRefresh = viewModel::refresh,
        onSelectSort = viewModel::selectSort,
        onAlertChange = viewModel::setAlert,
        onAnalyzeOffer = onAnalyzeOffer,
    )
}

/**
 * Stateless layout of the twin offers screen, also used by screenshot tests.
 *
 * @param state observed state.
 * @param snackbarHostState host of the confirmations.
 * @param onBack back action, null for the tab root.
 * @param onRefresh reloads the screen.
 * @param onSelectSort changes the order.
 * @param onAlertChange turns the calm alert on or off.
 * @param onAnalyzeOffer opens the paste screen (empty state).
 */
@Composable
fun TwinOffersLayout(
    state: TwinOffersUiState,
    snackbarHostState: SnackbarHostState,
    onBack: (() -> Unit)?,
    onRefresh: () -> Unit,
    onSelectSort: (TwinSort) -> Unit,
    onAlertChange: (Boolean) -> Unit,
    onAnalyzeOffer: () -> Unit,
) {
    val twins = state.twins.content
    val uriHandler = LocalUriHandler.current
    JaugeScreenScaffold(
        showTopBar = onBack != null,
        title = stringResource(Res.string.twin_offers_title),
        navigationIcon = onBack?.let { back -> { RoundIconButton(JaugeIcons.Back, stringResource(Res.string.action_back), back) } },
        snackbarHostState = snackbarHostState,
    ) {
        JaugeRefreshContainer(isRefreshing = state.twins.isRefreshing, onRefresh = onRefresh, modifier = Modifier.fillMaxSize()) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(top = if (onBack == null) 12.dp else 4.dp, bottom = JaugeTheme.spacing.section),
                verticalArrangement = Arrangement.spacedBy(JaugeTheme.spacing.medium),
            ) {
                item(key = "header") {
                    Header(areaName = twins?.areaName, sort = state.sort, onSelectSort = onSelectSort, showSorts = !state.hasNoAnalysis)
                }
                when {
                    state.hasNoAnalysis -> item(key = "empty") {
                        EmptyState(
                            title = stringResource(Res.string.twin_offers_empty_title),
                            message = stringResource(Res.string.twin_offers_empty_message),
                            icon = JaugeIcons.Radar,
                            actionLabel = stringResource(Res.string.twin_offers_empty_action),
                            onAction = onAnalyzeOffer,
                        )
                    }
                    twins != null -> {
                        if (twins.offers.isEmpty()) {
                            item(key = "no-twins") {
                                EmptyState(
                                    title = stringResource(Res.string.twin_offers_no_twins_title),
                                    message = stringResource(Res.string.twin_offers_no_twins_message),
                                    icon = JaugeIcons.Radar,
                                )
                            }
                        }
                        items(twins.offers, key = { it.id }) { offer ->
                            TwinOfferCard(
                                offer = offer,
                                onClick = WebLinks.safeWebLinkOrNull(offer.url)?.let { url -> { runCatching { uriHandler.openUri(url) } } },
                                modifier = Modifier.padding(horizontal = JaugeTheme.spacing.screenGutter),
                            )
                        }
                        item(key = "alert") {
                            CalmAlertCard(
                                isOn = state.isAlertOn,
                                threshold = state.alertThreshold,
                                onChange = onAlertChange,
                                modifier = Modifier.padding(horizontal = JaugeTheme.spacing.screenGutter).padding(top = 2.dp),
                            )
                        }
                    }
                    state.twins.showsFullScreenError -> item(key = "error") {
                        FriendlyErrorView(error = state.twins.error!!, onAction = { onRefresh() })
                    }
                    else -> item(key = "loading") {
                        SkeletonList(rowCount = 4, modifier = Modifier.padding(horizontal = JaugeTheme.spacing.screenGutter))
                    }
                }
            }
        }
    }
}

private val TwinSort.label: StringResource
    get() = when (this) {
        TwinSort.LEAST_DISPUTED -> Res.string.twin_offers_sort_least_disputed
        TwinSort.BEST_PAID -> Res.string.twin_offers_sort_best_paid
        TwinSort.MOST_RECENT -> Res.string.twin_offers_sort_most_recent
    }

@Composable
private fun Header(areaName: String?, sort: TwinSort, onSelectSort: (TwinSort) -> Unit, showSorts: Boolean) {
    val colors = JaugeTheme.colors
    Column {
        Column(Modifier.padding(horizontal = JaugeTheme.spacing.screenGutter)) {
            if (areaName != null) {
                Text(
                    text = stringResource(Res.string.twin_offers_overline, areaName).uppercase(),
                    style = JaugeTheme.typography.overline,
                    color = colors.textSecondary,
                )
            }
            Text(
                text = stringResource(Res.string.twin_offers_headline),
                style = JaugeTheme.typography.headline,
                color = colors.textPrimary,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
        if (showSorts) {
            LazyRow(
                modifier = Modifier
                    .padding(top = 16.dp, bottom = 4.dp)
                    .fillMaxWidth()
                    .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
                    .drawWithContent {
                        drawContent()
                        // Fades the chips out on the right edge, as in the mockup.
                        drawRect(
                            brush = Brush.horizontalGradient(
                                0.8f to Color.Black,
                                1f to Color.Transparent,
                            ),
                            blendMode = BlendMode.DstIn,
                        )
                    },
                contentPadding = PaddingValues(horizontal = JaugeTheme.spacing.screenGutter),
                horizontalArrangement = Arrangement.spacedBy(JaugeTheme.spacing.small),
            ) {
                items(TwinSort.entries) { item ->
                    JaugeChip(
                        text = stringResource(item.label),
                        selected = item == sort,
                        onClick = { onSelectSort(item) },
                        style = JaugeChipStyle.FILTER,
                    )
                }
            }
        }
    }
}

@Composable
private fun TwinOfferCard(offer: TwinOffer, onClick: (() -> Unit)?, modifier: Modifier = Modifier) {
    val colors = JaugeTheme.colors
    val description = stringResource(Res.string.twin_offers_open_offer, offer.jobTitle)
    JaugeCard(
        modifier = modifier.fillMaxWidth().semantics { if (onClick != null) contentDescription = description },
        onClick = onClick,
        contentPadding = PaddingValues(14.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            JaugeGauge(score = offer.score, size = GaugeSize.MINI, needleColor = colors.textPrimary, animate = false)
            Column(Modifier.weight(1f)) {
                Text(
                    text = offer.jobTitle,
                    style = JaugeTheme.typography.bodyStrong,
                    color = colors.textPrimary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = stringResource(Res.string.twin_offers_company_place, offer.company, offer.place),
                    style = JaugeTheme.typography.label.copy(fontWeight = FontWeight.Normal),
                    color = colors.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp),
                )
                Row(modifier = Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    offer.salary?.takeIf { it.isValid }?.let { salary ->
                        Text(
                            text = stringResource(Res.string.twin_offers_salary, salary.minimumThousands, salary.maximumThousands),
                            style = JaugeTheme.typography.numberSmall.copy(fontSize = 12.5.sp),
                            color = colors.textPrimary,
                        )
                    }
                    Text(
                        text = stringResource(Res.string.twin_offers_applicants, offer.estimatedApplicants),
                        style = JaugeTheme.typography.label.copy(fontSize = 12.5.sp),
                        color = colors.textPrimary,
                    )
                }
            }
            LevelPill(level = GaugeLevel.valueOf(offer.level.name))
        }
    }
}

@Composable
private fun CalmAlertCard(isOn: Boolean, threshold: Int, onChange: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    val colors = JaugeTheme.colors
    JaugeCard(
        modifier = modifier.fillMaxWidth(),
        containerColor = colors.inverseSurface,
        borderColor = colors.inverseSurface,
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(JaugeIcons.Bell, contentDescription = null, tint = colors.calm.accent, modifier = Modifier.size(20.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = stringResource(Res.string.twin_offers_alert_title),
                    style = JaugeTheme.typography.body.copy(fontSize = 14.sp, fontWeight = FontWeight.Bold),
                    color = colors.onInverseSurface,
                )
                Text(
                    text = stringResource(Res.string.twin_offers_alert_message, threshold),
                    style = JaugeTheme.typography.body.copy(fontSize = 14.sp, fontWeight = FontWeight.Normal),
                    color = colors.textTertiary,
                )
            }
            Box { JaugeSwitch(checked = isOn, onCheckedChange = onChange) }
        }
    }
}
