package io.jauge.feature.verdict.methodology

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.jauge.core.designsystem.component.JaugeButton
import io.jauge.core.designsystem.component.JaugeButtonSize
import io.jauge.core.designsystem.component.JaugeButtonStyle
import io.jauge.core.designsystem.component.JaugeCard
import io.jauge.core.designsystem.component.JaugePill
import io.jauge.core.designsystem.component.JaugeRefreshContainer
import io.jauge.core.designsystem.component.JaugeScreenScaffold
import io.jauge.core.designsystem.component.JaugeTextField
import io.jauge.core.designsystem.component.RoundIconButton
import io.jauge.core.designsystem.component.SkeletonCard
import io.jauge.core.designsystem.component.skeleton
import io.jauge.core.designsystem.icon.JaugeIcons
import io.jauge.core.designsystem.theme.JaugeTheme
import io.jauge.core.designsystem.theme.LevelColors
import io.jauge.core.domain.model.Methodology
import io.jauge.core.domain.model.Reliability
import io.jauge.core.ui.error.FriendlyErrorView
import io.jauge.core.ui.error.toFriendlyMessage
import io.jauge.feature.verdict.common.FractionLabel
import io.jauge.feature.verdict.common.LabelAnchor
import io.jauge.feature.verdict.common.formatGrouped
import io.jauge.feature.verdict.common.label
import io.jauge.feature.verdict.resources.Res
import io.jauge.feature.verdict.resources.action_back
import io.jauge.feature.verdict.resources.approximately_number
import io.jauge.feature.verdict.resources.methodology_applicants_title
import io.jauge.feature.verdict.resources.methodology_disclaimer
import io.jauge.feature.verdict.resources.methodology_feedback_button
import io.jauge.feature.verdict.resources.methodology_feedback_cancel
import io.jauge.feature.verdict.resources.methodology_feedback_dialog_message
import io.jauge.feature.verdict.resources.methodology_feedback_dialog_title
import io.jauge.feature.verdict.resources.methodology_feedback_field_label
import io.jauge.feature.verdict.resources.methodology_feedback_field_placeholder
import io.jauge.feature.verdict.resources.methodology_feedback_invalid
import io.jauge.feature.verdict.resources.methodology_feedback_send
import io.jauge.feature.verdict.resources.methodology_feedback_thanks
import io.jauge.feature.verdict.resources.methodology_headline
import io.jauge.feature.verdict.resources.methodology_level_high
import io.jauge.feature.verdict.resources.methodology_level_low
import io.jauge.feature.verdict.resources.methodology_level_medium
import io.jauge.feature.verdict.resources.methodology_margin
import io.jauge.feature.verdict.resources.methodology_range_caption
import io.jauge.feature.verdict.resources.methodology_step_one
import io.jauge.feature.verdict.resources.methodology_step_one_bold
import io.jauge.feature.verdict.resources.methodology_step_three
import io.jauge.feature.verdict.resources.methodology_step_three_bold
import io.jauge.feature.verdict.resources.methodology_step_two
import io.jauge.feature.verdict.resources.methodology_step_two_bold
import io.jauge.feature.verdict.resources.methodology_title
import io.jauge.feature.verdict.resources.methodology_why_bold
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

/**
 * How the estimate is built and how reliable it is (mockup 09).
 *
 * @param analysisId identifier of the analysis.
 * @param onBack called when the user leaves the screen.
 */
@Composable
fun MethodologyScreen(
    analysisId: String,
    onBack: () -> Unit,
) {
    val viewModel: MethodologyViewModel = koinViewModel(key = "methodology-$analysisId") { parametersOf(analysisId) }
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            val message = when (event) {
                MethodologyEvent.FeedbackSent -> getString(Res.string.methodology_feedback_thanks)
                is MethodologyEvent.FeedbackFailed -> getString(event.error.toFriendlyMessage().title)
            }
            snackbarHostState.showSnackbar(message)
        }
    }
    MethodologyLayout(
        state = state,
        snackbarHostState = snackbarHostState,
        onBack = onBack,
        onRefresh = viewModel::refresh,
        onOpenFeedback = viewModel::openFeedback,
        onFeedbackTextChange = viewModel::onFeedbackTextChange,
        onSendFeedback = viewModel::sendFeedback,
        onDismissFeedback = viewModel::dismissFeedback,
    )
}

/**
 * Stateless layout of the methodology screen, also used by screenshot tests.
 *
 * @param state observed state.
 * @param snackbarHostState host of the confirmations.
 * @param onBack back action.
 * @param onRefresh reloads the methodology.
 * @param onOpenFeedback opens the "vrai chiffre" dialog.
 * @param onFeedbackTextChange updates the typed number.
 * @param onSendFeedback sends the typed number.
 * @param onDismissFeedback closes the dialog.
 */
@Composable
fun MethodologyLayout(
    state: MethodologyUiState,
    snackbarHostState: SnackbarHostState,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onOpenFeedback: () -> Unit,
    onFeedbackTextChange: (String) -> Unit,
    onSendFeedback: () -> Unit,
    onDismissFeedback: () -> Unit,
) {
    val content = state.methodology
    val methodology = content.content
    JaugeScreenScaffold(
        title = stringResource(Res.string.methodology_title),
        navigationIcon = { RoundIconButton(JaugeIcons.Back, stringResource(Res.string.action_back), onBack) },
        snackbarHostState = snackbarHostState,
        bottomBar = if (methodology != null) {
            {
                JaugeButton(
                    text = stringResource(Res.string.methodology_feedback_button),
                    onClick = onOpenFeedback,
                    style = JaugeButtonStyle.OUTLINED,
                    size = JaugeButtonSize.MEDIUM,
                    modifier = Modifier.fillMaxWidth().background(JaugeTheme.colors.surface, JaugeTheme.radii.buttonShape),
                )
            }
        } else {
            null
        },
    ) {
        JaugeRefreshContainer(isRefreshing = content.isRefreshing, onRefresh = onRefresh, modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = JaugeTheme.spacing.screenGutter)
                    .padding(top = 8.dp, bottom = JaugeTheme.spacing.extraLarge),
            ) {
                when {
                    methodology != null -> MethodologyContent(methodology)
                    content.showsFullScreenError -> FriendlyErrorView(error = content.error!!, onAction = { onRefresh() })
                    else -> {
                        Box(Modifier.fillMaxWidth(0.7f).height(60.dp).skeleton())
                        SkeletonCard(modifier = Modifier.fillMaxWidth().padding(top = 14.dp), height = 90.dp)
                        SkeletonCard(modifier = Modifier.fillMaxWidth().padding(top = 14.dp), height = 140.dp)
                    }
                }
            }
        }
    }
    if (state.feedback.isOpen) {
        FeedbackDialog(
            feedback = state.feedback,
            onTextChange = onFeedbackTextChange,
            onSend = onSendFeedback,
            onDismiss = onDismissFeedback,
        )
    }
}

@Composable
private fun MethodologyContent(methodology: Methodology) {
    val colors = JaugeTheme.colors
    val typography = JaugeTheme.typography
    val estimate = formatGrouped(methodology.applicants.estimate)
    Text(
        text = stringResource(Res.string.methodology_headline, estimate),
        style = typography.headline.copy(fontSize = 28.sp, lineHeight = 30.sp),
        color = colors.textPrimary,
    )
    RangeCard(methodology)
    Column(modifier = Modifier.padding(top = 14.dp), verticalArrangement = Arrangement.spacedBy(11.dp)) {
        MethodStep(1, boldText(stringResource(Res.string.methodology_step_one_bold), stringResource(Res.string.methodology_step_one)))
        MethodStep(
            2,
            boldText(
                stringResource(Res.string.methodology_step_two_bold, formatGrouped(methodology.comparableOfferCount), methodology.regionName),
                stringResource(Res.string.methodology_step_two),
            ),
        )
        MethodStep(
            3,
            boldText(
                stringResource(Res.string.methodology_step_three_bold),
                stringResource(Res.string.methodology_step_three, methodology.reliability.typicalMarginPercent),
            ),
        )
    }
    ReliabilityCard(methodology)
    Row(modifier = Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Icon(JaugeIcons.Info, contentDescription = null, tint = colors.textSecondary, modifier = Modifier.padding(top = 1.dp).size(16.dp))
        Text(
            text = stringResource(Res.string.methodology_disclaimer, formatGrouped(methodology.salarySourceCount)),
            style = typography.caption.copy(lineHeight = 17.sp),
            color = colors.textSecondary,
        )
    }
}

@Composable
private fun boldText(bold: String, rest: String): AnnotatedString = buildAnnotatedString {
    withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = JaugeTheme.colors.textPrimary)) { append(bold) }
    append(rest)
}

@Composable
private fun reliabilityColors(reliability: Reliability): LevelColors = when (reliability) {
    Reliability.HIGH -> JaugeTheme.colors.calm
    Reliability.MEDIUM -> JaugeTheme.colors.disputed
    Reliability.LOW -> JaugeTheme.colors.crowded
}

@Composable
private fun RangeCard(methodology: Methodology) {
    val colors = JaugeTheme.colors
    val applicants = methodology.applicants
    val levelColors = reliabilityColors(methodology.reliability)
    // The scale extends the range by 60 % of its width on each side, as in the mockup (160 to 260 on 100 to 320).
    val span = (applicants.high - applicants.low).coerceAtLeast(1).toFloat()
    val scaleMinimum = applicants.low - span * 0.6f
    val scaleMaximum = applicants.high + span * 0.6f
    fun fraction(value: Int) = ((value - scaleMinimum) / (scaleMaximum - scaleMinimum)).coerceIn(0f, 1f)
    JaugeCard(
        modifier = Modifier.fillMaxWidth().padding(top = 14.dp),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(Res.string.methodology_applicants_title),
                style = JaugeTheme.typography.bodyStrong.copy(fontSize = 14.5.sp),
                color = colors.textPrimary,
                modifier = Modifier.weight(1f),
            )
            JaugePill(text = methodology.reliability.label(), containerColor = levelColors.container, contentColor = levelColors.content)
        }
        val surface = colors.surface
        val track = colors.backgroundAlternative
        val knob = colors.textPrimary
        Canvas(modifier = Modifier.fillMaxWidth().padding(top = 10.dp).height(40.dp)) {
            val centerY = size.height / 2f
            val trackHeight = 10.dp.toPx()
            drawRoundRect(track, Offset(0f, centerY - trackHeight / 2f), Size(size.width, trackHeight), CornerRadius(trackHeight / 2f))
            val start = fraction(applicants.low) * size.width
            val end = fraction(applicants.high) * size.width
            val rangeHeight = 16.dp.toPx()
            val border = 2.dp.toPx()
            drawRoundRect(levelColors.accent, Offset(start, centerY - rangeHeight / 2f), Size(end - start, rangeHeight), CornerRadius(rangeHeight / 2f))
            drawRoundRect(
                levelColors.container,
                Offset(start + border, centerY - rangeHeight / 2f + border),
                Size(end - start - border * 2, rangeHeight - border * 2),
                CornerRadius(rangeHeight / 2f - border),
            )
            val knobX = fraction(applicants.estimate) * size.width
            drawCircle(knob, radius = 13.dp.toPx(), center = Offset(knobX, centerY))
            drawCircle(surface, radius = 12.dp.toPx(), center = Offset(knobX, centerY))
            drawCircle(knob, radius = 8.dp.toPx(), center = Offset(knobX, centerY))
        }
        val mono = JaugeTheme.typography.numberCaption.copy(fontSize = 12.sp, letterSpacing = 0.sp)
        Box(Modifier.fillMaxWidth()) {
            FractionLabel(fraction(applicants.low), LabelAnchor.CENTER) {
                Text(formatGrouped(applicants.low), style = mono, color = colors.textSecondary)
            }
            FractionLabel(fraction(applicants.estimate), LabelAnchor.CENTER) {
                Text(
                    text = stringResource(Res.string.approximately_number, formatGrouped(applicants.estimate)),
                    style = mono.copy(fontWeight = FontWeight.Bold),
                    color = colors.textPrimary,
                )
            }
            FractionLabel(fraction(applicants.high), LabelAnchor.CENTER) {
                Text(formatGrouped(applicants.high), style = mono, color = colors.textSecondary)
            }
        }
        Text(
            text = stringResource(Res.string.methodology_range_caption),
            style = JaugeTheme.typography.caption.copy(fontSize = 12.5.sp, lineHeight = 17.sp),
            color = colors.textSecondary,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}

@Composable
private fun MethodStep(number: Int, text: AnnotatedString) {
    val colors = JaugeTheme.colors
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(
            modifier = Modifier.padding(top = 1.dp).size(24.dp).background(colors.neutralAction, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(number.toString(), style = JaugeTheme.typography.numberSmall.copy(fontSize = 12.sp), color = colors.onNeutralAction)
        }
        Text(
            text = text,
            style = JaugeTheme.typography.bodySmall.copy(lineHeight = 19.sp),
            color = colors.textPrimary,
            modifier = Modifier.weight(1f),
        )
    }
}

private val Reliability.levelName: StringResource
    get() = when (this) {
        Reliability.HIGH -> Res.string.methodology_level_high
        Reliability.MEDIUM -> Res.string.methodology_level_medium
        Reliability.LOW -> Res.string.methodology_level_low
    }

@Composable
private fun ReliabilityCard(methodology: Methodology) {
    val colors = JaugeTheme.colors
    JaugeCard(
        modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Reliability.entries.forEach { reliability ->
                val selected = reliability == methodology.reliability
                val levelColors = reliabilityColors(reliability)
                val shape = RoundedCornerShape(12.dp)
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .background(if (selected) levelColors.container else colors.background, shape)
                        .border(if (selected) 2.dp else 1.dp, if (selected) levelColors.accent else colors.outline, shape)
                        .padding(horizontal = 6.dp, vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = stringResource(reliability.levelName),
                        style = JaugeTheme.typography.label.copy(fontWeight = FontWeight.Bold),
                        color = colors.textPrimary,
                    )
                    Text(
                        text = stringResource(Res.string.methodology_margin, reliability.typicalMarginPercent),
                        style = JaugeTheme.typography.numberCaption.copy(letterSpacing = 0.sp),
                        color = if (selected) levelColors.content else colors.textSecondary,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
            }
        }
        if (methodology.reliabilityReasons.isNotEmpty()) {
            val level = stringResource(methodology.reliability.levelName).lowercase()
            Text(
                text = boldText(
                    stringResource(Res.string.methodology_why_bold, level),
                    " " + methodology.reliabilityReasons.joinToString(" ") { it.trim() },
                ),
                style = JaugeTheme.typography.caption.copy(fontSize = 12.5.sp, lineHeight = 17.sp),
                color = colors.textPrimary,
                modifier = Modifier.padding(top = 10.dp),
            )
        }
    }
}

@Composable
private fun FeedbackDialog(
    feedback: FeedbackState,
    onTextChange: (String) -> Unit,
    onSend: () -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = JaugeTheme.colors
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colors.surface,
        title = {
            Text(stringResource(Res.string.methodology_feedback_dialog_title), style = JaugeTheme.typography.sectionTitle, color = colors.textPrimary)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = stringResource(Res.string.methodology_feedback_dialog_message),
                    style = JaugeTheme.typography.bodySmall,
                    color = colors.textSecondary,
                )
                JaugeTextField(
                    value = feedback.text,
                    onValueChange = onTextChange,
                    label = stringResource(Res.string.methodology_feedback_field_label),
                    placeholder = stringResource(Res.string.methodology_feedback_field_placeholder),
                    monospace = true,
                    errorMessage = if (feedback.isInvalid) stringResource(Res.string.methodology_feedback_invalid) else null,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Send),
                    enabled = !feedback.isSending,
                )
            }
        },
        confirmButton = {
            JaugeButton(
                text = stringResource(Res.string.methodology_feedback_send),
                onClick = onSend,
                size = JaugeButtonSize.MEDIUM,
                isLoading = feedback.isSending,
            )
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(Res.string.methodology_feedback_cancel), style = JaugeTheme.typography.label, color = colors.textSecondary)
            }
        },
    )
}
