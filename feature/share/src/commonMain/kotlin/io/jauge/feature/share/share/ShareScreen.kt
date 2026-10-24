package io.jauge.feature.share.share

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.jauge.core.designsystem.component.GaugeLevel
import io.jauge.core.designsystem.component.JaugeChip
import io.jauge.core.designsystem.component.JaugeChipStyle
import io.jauge.core.designsystem.component.JaugeRefreshContainer
import io.jauge.core.designsystem.component.JaugeSwitch
import io.jauge.core.designsystem.component.JaugeTopBar
import io.jauge.core.designsystem.component.RoundIconButton
import io.jauge.core.designsystem.component.skeleton
import io.jauge.core.designsystem.icon.JaugeIcons
import io.jauge.core.designsystem.theme.JaugeTheme
import io.jauge.core.domain.model.Analysis
import io.jauge.core.domain.model.Share
import io.jauge.core.domain.model.ShareFormat
import io.jauge.core.ui.error.FriendlyErrorView
import io.jauge.core.ui.error.toFriendlyMessage
import io.jauge.feature.share.platform.NativeSharer
import io.jauge.feature.share.platform.encodeToPng
import io.jauge.feature.share.platform.rememberNativeSharer
import io.jauge.feature.share.resources.Res
import io.jauge.feature.share.resources.action_close
import io.jauge.feature.share.resources.share_destination_copy
import io.jauge.feature.share.resources.share_destination_messages
import io.jauge.feature.share.resources.share_destination_others
import io.jauge.feature.share.resources.share_destination_save
import io.jauge.feature.share.resources.share_format_link
import io.jauge.feature.share.resources.share_format_square
import io.jauge.feature.share.resources.share_format_story
import io.jauge.feature.share.resources.share_message
import io.jauge.feature.share.resources.share_message_image_saved
import io.jauge.feature.share.resources.share_message_link_copied
import io.jauge.feature.share.resources.share_message_save_unavailable
import io.jauge.feature.share.resources.share_message_unavailable
import io.jauge.feature.share.resources.share_option_hide_company
import io.jauge.feature.share.resources.share_option_show_calm_twin
import io.jauge.feature.share.resources.share_title
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

/**
 * Share card and link https://jauge.io/a/{analysisId} (mockup 07).
 *
 * @param analysisId shared analysis.
 * @param onBack called when the user leaves the screen.
 */
@Composable
fun ShareScreen(
    analysisId: String,
    onBack: () -> Unit,
) {
    val viewModel: ShareViewModel = koinViewModel(key = "share-$analysisId") { parametersOf(analysisId) }
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val sharer = rememberNativeSharer()
    val cardLayer = rememberGraphicsLayer()
    LaunchedEffect(viewModel, sharer) {
        viewModel.events.collect { event ->
            when (event) {
                is ShareEvent.Ready -> {
                    val analysis = viewModel.uiState.value.analysis.content ?: return@collect
                    val format = viewModel.uiState.value.options.format
                    val message = send(event.destination, event.share, analysis, format, sharer, cardLayer)
                    message?.let { snackbarHostState.showSnackbar(getString(it)) }
                }
                is ShareEvent.Failed -> snackbarHostState.showSnackbar(getString(event.error.toFriendlyMessage().title))
            }
        }
    }
    ShareLayout(
        state = state,
        snackbarHostState = snackbarHostState,
        cardLayer = cardLayer,
        onBack = onBack,
        onRefresh = viewModel::refresh,
        onSelectFormat = viewModel::selectFormat,
        onHideCompanyChange = viewModel::setCompanyHidden,
        onShowCalmTwinChange = viewModel::setCalmTwinShown,
        onDestination = viewModel::prepare,
    )
}

/**
 * Sends a registered share to its destination and returns the confirmation to show, if any.
 *
 * The image is the card currently drawn on screen, recorded in [cardLayer].
 */
private suspend fun send(
    destination: ShareDestination,
    share: Share,
    analysis: Analysis,
    format: ShareFormat,
    sharer: NativeSharer,
    cardLayer: GraphicsLayer,
): StringResource? {
    val level = GaugeLevel.valueOf(analysis.level.name)
    val text = getString(Res.string.share_message, analysis.offer.jobTitle, analysis.score, getString(level.label).lowercase(), share.url)
    val withImage = format != ShareFormat.LINK
    suspend fun card(): ByteArray? = if (withImage) runCatching { cardLayer.toImageBitmap().encodeToPng() }.getOrNull() else null
    return when (destination) {
        ShareDestination.COPY -> {
            sharer.copyText(share.url)
            Res.string.share_message_link_copied
        }
        ShareDestination.MESSAGES -> if (sharer.sendMessage(text, card())) null else Res.string.share_message_unavailable
        ShareDestination.SAVE -> {
            val png = card() ?: return Res.string.share_message_unavailable
            when {
                sharer.saveImage(png, "jauge-${analysis.id}") -> Res.string.share_message_image_saved
                sharer.shareImage(png, text) -> Res.string.share_message_save_unavailable
                else -> Res.string.share_message_unavailable
            }
        }
        ShareDestination.OTHERS -> {
            val png = card()
            val opened = if (png != null) sharer.shareImage(png, text) else sharer.shareText(text)
            if (opened) null else Res.string.share_message_unavailable
        }
    }
}

/**
 * Stateless layout of the share screen, also used by screenshot tests.
 *
 * @param state observed state.
 * @param snackbarHostState host of the confirmations.
 * @param cardLayer layer recording the card, turned into the shared image.
 * @param onBack closes the screen.
 * @param onRefresh reloads the analysis.
 * @param onSelectFormat changes the format.
 * @param onHideCompanyChange hides or shows the company.
 * @param onShowCalmTwinChange shows or hides the calm twin.
 * @param onDestination sends the share to a destination.
 */
@Composable
fun ShareLayout(
    state: ShareUiState,
    snackbarHostState: SnackbarHostState,
    cardLayer: GraphicsLayer,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onSelectFormat: (ShareFormat) -> Unit,
    onHideCompanyChange: (Boolean) -> Unit,
    onShowCalmTwinChange: (Boolean) -> Unit,
    onDestination: (ShareDestination) -> Unit,
) {
    val colors = JaugeTheme.colors
    val analysis = state.analysis.content
    Box(Modifier.fillMaxSize().background(colors.backgroundAlternative)) {
        Column(Modifier.fillMaxSize()) {
            JaugeTopBar(
                title = stringResource(Res.string.share_title),
                navigationIcon = { RoundIconButton(JaugeIcons.Close, stringResource(Res.string.action_close), onBack) },
            )
            JaugeRefreshContainer(isRefreshing = state.analysis.isRefreshing, onRefresh = onRefresh, modifier = Modifier.weight(1f)) {
                Column(
                    modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(top = 8.dp, bottom = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    when {
                        analysis != null -> ShareEditor(state, analysis, cardLayer, onSelectFormat, onHideCompanyChange, onShowCalmTwinChange)
                        state.analysis.showsFullScreenError -> FriendlyErrorView(error = state.analysis.error!!, onAction = { onRefresh() })
                        else -> Box(Modifier.size(290.dp, 440.dp).skeleton(RoundedCornerShape(JaugeTheme.radii.large)))
                    }
                }
            }
            if (analysis != null) {
                Destinations(
                    format = state.options.format,
                    pendingDestination = state.pendingDestination,
                    onDestination = onDestination,
                    modifier = Modifier
                        .navigationBarsPadding()
                        .padding(horizontal = JaugeTheme.spacing.screenGutter)
                        .padding(top = 8.dp, bottom = JaugeTheme.spacing.extraLarge),
                )
            }
        }
        SnackbarHost(snackbarHostState, Modifier.align(Alignment.BottomCenter).padding(bottom = 110.dp))
    }
}

@Composable
private fun ShareEditor(
    state: ShareUiState,
    analysis: Analysis,
    cardLayer: GraphicsLayer,
    onSelectFormat: (ShareFormat) -> Unit,
    onHideCompanyChange: (Boolean) -> Unit,
    onShowCalmTwinChange: (Boolean) -> Unit,
) {
    val colors = JaugeTheme.colors
    val cardHeight = if (state.options.format == ShareFormat.STORY) 440.dp else 290.dp
    Box(
        modifier = Modifier
            .size(290.dp, cardHeight)
            .shadow(16.dp, RoundedCornerShape(JaugeTheme.radii.large), ambientColor = colors.textPrimary.copy(alpha = 0.2f), spotColor = colors.textPrimary.copy(alpha = 0.35f))
            .drawWithContent {
                cardLayer.record { this@drawWithContent.drawContent() }
                drawLayer(cardLayer)
            },
    ) {
        ShareCard(
            analysis = analysis,
            calmTwin = state.calmTwin.takeIf { state.options.showCalmTwin },
            options = state.options,
            modifier = Modifier.fillMaxSize(),
        )
    }
    Row(modifier = Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        ShareFormat.entries.forEach { format ->
            JaugeChip(
                text = stringResource(format.label),
                selected = format == state.options.format,
                onClick = { onSelectFormat(format) },
                style = JaugeChipStyle.SEGMENT,
            )
        }
    }
    Column(
        modifier = Modifier
            .padding(horizontal = JaugeTheme.spacing.screenGutter)
            .padding(top = 12.dp)
            .fillMaxWidth()
            .background(colors.surface, JaugeTheme.radii.cardShape)
            .border(1.dp, colors.outline, JaugeTheme.radii.cardShape),
    ) {
        OptionRow(stringResource(Res.string.share_option_hide_company), state.options.hideCompany, onHideCompanyChange)
        if (state.calmTwin != null && state.options.format == ShareFormat.STORY) {
            HorizontalDivider(color = colors.outline)
            OptionRow(stringResource(Res.string.share_option_show_calm_twin), state.options.showCalmTwin, onShowCalmTwinChange)
        }
    }
}

private val ShareFormat.label: StringResource
    get() = when (this) {
        ShareFormat.STORY -> Res.string.share_format_story
        ShareFormat.SQUARE -> Res.string.share_format_square
        ShareFormat.LINK -> Res.string.share_format_link
    }

@Composable
private fun OptionRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
            .clickable(role = Role.Switch) { onCheckedChange(!checked) }
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = JaugeTheme.typography.body.copy(fontWeight = FontWeight.SemiBold),
            color = JaugeTheme.colors.textPrimary,
            modifier = Modifier.weight(1f),
        )
        JaugeSwitch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun Destinations(
    format: ShareFormat,
    pendingDestination: ShareDestination?,
    onDestination: (ShareDestination) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = JaugeTheme.colors
    Row(modifier = modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround) {
        DestinationButton(
            label = stringResource(Res.string.share_destination_messages),
            icon = JaugeIcons.Message,
            background = colors.calm.accent,
            content = colors.onAction,
            isPending = pendingDestination == ShareDestination.MESSAGES,
            onClick = { onDestination(ShareDestination.MESSAGES) },
        )
        DestinationButton(
            label = stringResource(Res.string.share_destination_copy),
            icon = JaugeIcons.Link,
            background = colors.neutralAction,
            content = colors.onNeutralAction,
            isPending = pendingDestination == ShareDestination.COPY,
            onClick = { onDestination(ShareDestination.COPY) },
        )
        if (format != ShareFormat.LINK) {
            DestinationButton(
                label = stringResource(Res.string.share_destination_save),
                icon = JaugeIcons.Download,
                background = colors.action,
                content = colors.onAction,
                isPending = pendingDestination == ShareDestination.SAVE,
                onClick = { onDestination(ShareDestination.SAVE) },
            )
        }
        DestinationButton(
            label = stringResource(Res.string.share_destination_others),
            icon = JaugeIcons.Share,
            background = colors.surface,
            content = colors.textPrimary,
            border = colors.outline,
            isPending = pendingDestination == ShareDestination.OTHERS,
            onClick = { onDestination(ShareDestination.OTHERS) },
        )
    }
}

@Composable
private fun DestinationButton(
    label: String,
    icon: ImageVector,
    background: Color,
    content: Color,
    isPending: Boolean,
    onClick: () -> Unit,
    border: Color = Color.Transparent,
) {
    val shape = RoundedCornerShape(17.dp)
    Column(
        modifier = Modifier.clickable(role = Role.Button, onClick = onClick).padding(4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        Box(
            modifier = Modifier.size(54.dp).background(background, shape).border(1.dp, border, shape),
            contentAlignment = Alignment.Center,
        ) {
            if (isPending) {
                CircularProgressIndicator(color = content, strokeWidth = 2.5.dp, modifier = Modifier.size(22.dp))
            } else {
                Icon(icon, contentDescription = null, tint = content, modifier = Modifier.size(24.dp))
            }
        }
        Text(label, style = JaugeTheme.typography.label.copy(fontSize = 12.sp), color = JaugeTheme.colors.textPrimary)
    }
}
