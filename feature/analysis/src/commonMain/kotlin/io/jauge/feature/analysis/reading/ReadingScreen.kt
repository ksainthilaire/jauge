package io.jauge.feature.analysis.reading

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.jauge.core.designsystem.component.JaugeProgressBar
import io.jauge.core.designsystem.component.JaugeTopBar
import io.jauge.core.designsystem.component.RoundIconButton
import io.jauge.core.designsystem.icon.JaugeIcons
import io.jauge.core.designsystem.theme.JaugePalette
import io.jauge.core.designsystem.theme.JaugeTheme
import io.jauge.core.domain.model.AnalysisProgress
import io.jauge.core.domain.model.OfferSource
import io.jauge.core.domain.model.ReadingStep
import io.jauge.core.domain.model.ReadingStepKind
import io.jauge.core.domain.model.ReadingStepStatus
import io.jauge.core.ui.error.FriendlyAction
import io.jauge.core.ui.error.FriendlyErrorView
import io.jauge.feature.analysis.resources.Res
import io.jauge.feature.analysis.resources.reading_close
import io.jauge.feature.analysis.resources.reading_found_published
import io.jauge.feature.analysis.resources.reading_found_salary
import io.jauge.feature.analysis.resources.reading_found_title
import io.jauge.feature.analysis.resources.reading_image_description
import io.jauge.feature.analysis.resources.reading_link_label
import io.jauge.feature.analysis.resources.reading_manual_label
import io.jauge.feature.analysis.resources.reading_percent
import io.jauge.feature.analysis.resources.reading_published_days
import io.jauge.feature.analysis.resources.reading_published_today
import io.jauge.feature.analysis.resources.reading_published_yesterday
import io.jauge.feature.analysis.resources.reading_salary_estimated_bold
import io.jauge.feature.analysis.resources.reading_salary_estimated_end
import io.jauge.feature.analysis.resources.reading_salary_estimated_start
import io.jauge.feature.analysis.resources.reading_salary_none
import io.jauge.feature.analysis.resources.reading_salary_shown
import io.jauge.feature.analysis.resources.reading_step_applicants
import io.jauge.feature.analysis.resources.reading_step_applicants_hint
import io.jauge.feature.analysis.resources.reading_step_comparison
import io.jauge.feature.analysis.resources.reading_step_comparison_hint
import io.jauge.feature.analysis.resources.reading_step_position_done
import io.jauge.feature.analysis.resources.reading_step_position_hint
import io.jauge.feature.analysis.resources.reading_step_position_running
import io.jauge.feature.analysis.resources.reading_step_skills_done
import io.jauge.feature.analysis.resources.reading_step_skills_hint
import io.jauge.feature.analysis.resources.reading_step_skills_running
import io.jauge.feature.analysis.resources.reading_title
import io.jauge.feature.analysis.resources.reading_wait
import org.jetbrains.compose.resources.ExperimentalResourceApi
import org.jetbrains.compose.resources.decodeToImageBitmap
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

/**
 * Reading of the offer (mockup 03), always dark: the offer being read, the steps, what was already found and the
 * progress. Opens the verdict when the analysis is ready, or the unreadable offer screen.
 *
 * @param requestId request kept by the previous screen.
 * @param onClose close button.
 * @param onCompleted opens the verdict of the finished analysis.
 * @param onUnreadable opens the unreadable offer screen, with the company that could still be read.
 * @param onOpenPaywall opens Jauge Plus when the free analysis is used.
 */
@Composable
fun ReadingScreen(
    requestId: String,
    onClose: () -> Unit,
    onCompleted: (analysisId: String) -> Unit,
    onUnreadable: (recognizedCompany: String?) -> Unit,
    onOpenPaywall: () -> Unit,
) {
    val viewModel: ReadingViewModel = koinViewModel(key = requestId) { parametersOf(requestId) }
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(state.completedAnalysisId, state.unreadable) {
        state.completedAnalysisId?.let { analysisId ->
            viewModel.onNavigated()
            onCompleted(analysisId)
        }
        state.unreadable?.let { unreadable ->
            viewModel.onNavigated()
            onUnreadable(unreadable.recognizedCompany)
        }
    }
    ReadingContent(
        state = state,
        onClose = onClose,
        onErrorAction = { action ->
            when (action) {
                FriendlyAction.RETRY -> viewModel.retry()
                FriendlyAction.OPEN_PAYWALL -> onOpenPaywall()
                else -> onClose()
            }
        },
    )
}

/**
 * Stateless content of the reading screen, also used by the screenshot tests.
 *
 * @param state state to draw.
 * @param onClose close button.
 * @param onErrorAction action of a friendly error.
 * @param modifier modifier of the screen.
 * @param animate false to freeze the scan line (screenshots).
 */
@Composable
fun ReadingContent(
    state: ReadingUiState,
    onClose: () -> Unit,
    onErrorAction: (FriendlyAction) -> Unit,
    modifier: Modifier = Modifier,
    animate: Boolean = true,
) {
    JaugeTheme(darkTheme = true) {
        val colors = JaugeTheme.colors
        val gutter = JaugeTheme.spacing.screenGutter
        Column(modifier = modifier.fillMaxSize().background(colors.background).navigationBarsPadding()) {
            JaugeTopBar(
                title = stringResource(Res.string.reading_title),
                navigationIcon = {
                    RoundIconButton(icon = JaugeIcons.Close, contentDescription = stringResource(Res.string.reading_close), onClick = onClose)
                },
            )
            val error = state.error
            if (error != null) {
                Column(modifier = Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                    FriendlyErrorView(error = error, onAction = onErrorAction)
                }
                return@Column
            }
            Column(
                modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = gutter).padding(top = 10.dp),
            ) {
                state.source?.let { OfferPreview(source = it, animate = animate) }
                val progress = state.progress
                ReadingSteps(steps = progress?.steps.orEmpty(), modifier = Modifier.padding(top = 20.dp))
                if (progress != null && (progress.publishedDaysAgo != null || progress.hasDisplayedSalary != null)) {
                    FoundBox(progress = progress, modifier = Modifier.padding(top = 16.dp, bottom = 12.dp))
                }
            }
            val percent = state.progress?.percent ?: 0
            Column(modifier = Modifier.padding(horizontal = gutter).padding(top = 8.dp, bottom = 24.dp)) {
                JaugeProgressBar(progress = percent / 100f)
                Row(modifier = Modifier.fillMaxWidth().padding(top = 10.dp)) {
                    Text(
                        text = stringResource(Res.string.reading_wait),
                        style = JaugeTheme.typography.label.copy(fontWeight = FontWeight.Normal),
                        color = colors.textSecondary,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = stringResource(Res.string.reading_percent, percent),
                        style = JaugeTheme.typography.numberSmall.copy(fontWeight = FontWeight.Medium),
                        color = colors.textSecondary,
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalResourceApi::class)
@Composable
private fun OfferPreview(source: OfferSource, animate: Boolean) {
    val shape = RoundedCornerShape(20.dp)
    val image: ImageBitmap? = remember(source) {
        (source as? OfferSource.Image)?.let { runCatching { it.bytes.decodeToImageBitmap() }.getOrNull() }
    }
    val transition = rememberInfiniteTransition(label = "scan")
    val animatedScan by transition.animateFloat(
        initialValue = 0.08f,
        targetValue = 0.92f,
        animationSpec = infiniteRepeatable(tween(durationMillis = 1600, easing = LinearEasing), RepeatMode.Reverse),
        label = "scanPosition",
    )
    val scanPosition = if (animate) animatedScan else 0.58f
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .rotate(-1.2f)
            .shadow(18.dp, shape, ambientColor = Color.Black.copy(alpha = 0.4f), spotColor = Color.Black.copy(alpha = 0.4f))
            .clip(shape)
            .background(JaugePalette.OffWhite)
            .drawWithContent {
                drawContent()
                val lineHeight = 2.dp.toPx()
                drawRect(
                    brush = Brush.horizontalGradient(listOf(Color.Transparent, JaugePalette.Coral, Color.Transparent)),
                    topLeft = Offset(0f, size.height * scanPosition),
                    size = Size(size.width, lineHeight),
                )
                drawRect(
                    brush = Brush.verticalGradient(
                        listOf(Color.Transparent, JaugePalette.Coral.copy(alpha = 0.25f), Color.Transparent),
                        startY = size.height * scanPosition - 18.dp.toPx(),
                        endY = size.height * scanPosition + 18.dp.toPx(),
                    ),
                    topLeft = Offset(0f, size.height * scanPosition - 18.dp.toPx()),
                    size = Size(size.width, 36.dp.toPx()),
                )
            },
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            when {
                image != null -> Image(
                    bitmap = image,
                    contentDescription = stringResource(Res.string.reading_image_description),
                    contentScale = ContentScale.Crop,
                    alignment = Alignment.TopCenter,
                    modifier = Modifier.fillMaxWidth().height(230.dp),
                )
                source is OfferSource.Link -> PreviewText(
                    label = stringResource(Res.string.reading_link_label),
                    value = source.url.removePrefix("https://").removePrefix("http://"),
                    monospace = true,
                )
                source is OfferSource.Manual -> PreviewText(
                    label = stringResource(Res.string.reading_manual_label),
                    value = source.jobTitle + "\n" + source.city,
                    monospace = false,
                )
                else -> Box(Modifier.fillMaxWidth().height(120.dp))
            }
        }
    }
}

@Composable
private fun PreviewText(label: String, value: String, monospace: Boolean) {
    Column(modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp).padding(18.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier.size(34.dp).background(Color(0xFFD9CFC0), RoundedCornerShape(9.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    if (monospace) JaugeIcons.Link else JaugeIcons.Document,
                    contentDescription = null,
                    tint = JaugePalette.Ink,
                    modifier = Modifier.size(18.dp),
                )
            }
            Text(text = label, style = JaugeTheme.typography.caption.copy(fontSize = 12.5.sp), color = Color(0xFF777777))
        }
        Text(
            text = value,
            style = if (monospace) {
                JaugeTheme.typography.numberSmall.copy(fontSize = 13.5.sp, fontWeight = FontWeight.Medium, lineHeight = 20.sp)
            } else {
                JaugeTheme.typography.bodyStrong.copy(fontSize = 19.sp, lineHeight = 24.sp, fontWeight = FontWeight.ExtraBold)
            },
            color = Color(0xFF2A2A2A),
            modifier = Modifier.padding(top = 12.dp),
        )
    }
}

@Composable
private fun ReadingSteps(steps: List<ReadingStep>, modifier: Modifier = Modifier) {
    val byKind = steps.associateBy { it.kind }
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(11.dp)) {
        ReadingStepKind.entries.forEach { kind ->
            val step = byKind[kind] ?: ReadingStep(kind, ReadingStepStatus.PENDING, null)
            StepRow(step)
        }
    }
}

@Composable
private fun StepRow(step: ReadingStep) {
    val colors = JaugeTheme.colors
    val done = step.status == ReadingStepStatus.DONE
    val running = step.status == ReadingStepStatus.RUNNING
    val title = when (step.kind) {
        ReadingStepKind.POSITION_AND_PLACE ->
            stringResource(if (done) Res.string.reading_step_position_done else Res.string.reading_step_position_running)
        ReadingStepKind.SKILLS -> {
            val count = step.detail?.split(',')?.count { it.isNotBlank() } ?: 0
            if (done && count > 0) pluralStringResource(Res.plurals.reading_step_skills_done, count, count) else stringResource(Res.string.reading_step_skills_running)
        }
        ReadingStepKind.COMPARISON -> stringResource(Res.string.reading_step_comparison)
        ReadingStepKind.APPLICANTS -> stringResource(Res.string.reading_step_applicants)
    }
    val hint = step.detail?.takeIf { it.isNotBlank() } ?: stringResource(
        when (step.kind) {
            ReadingStepKind.POSITION_AND_PLACE -> Res.string.reading_step_position_hint
            ReadingStepKind.SKILLS -> Res.string.reading_step_skills_hint
            ReadingStepKind.COMPARISON -> Res.string.reading_step_comparison_hint
            ReadingStepKind.APPLICANTS -> Res.string.reading_step_applicants_hint
        },
    )
    Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        Box(
            modifier = Modifier
                .size(26.dp)
                .background(if (done) colors.calm.accent else Color.Transparent, CircleShape)
                .border(2.dp, if (done) colors.calm.accent else if (running) colors.action else colors.outline, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            when {
                done -> Icon(JaugeIcons.Check, contentDescription = null, tint = colors.background, modifier = Modifier.size(15.dp))
                running -> CircularProgressIndicator(
                    modifier = Modifier.size(14.dp),
                    color = colors.action,
                    trackColor = colors.outline,
                    strokeWidth = 2.5.dp,
                )
            }
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = JaugeTheme.typography.body.copy(fontSize = 15.5.sp, fontWeight = FontWeight.SemiBold),
                color = if (done || running) colors.textPrimary else colors.textSecondary,
            )
            Text(
                text = hint,
                style = JaugeTheme.typography.label.copy(fontWeight = FontWeight.Normal),
                color = colors.textSecondary,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}

@Composable
private fun FoundBox(progress: AnalysisProgress.Reading, modifier: Modifier = Modifier) {
    val colors = JaugeTheme.colors
    val shape = RoundedCornerShape(18.dp)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(colors.surface, shape)
            .border(1.dp, colors.outline, shape)
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Text(
            text = stringResource(Res.string.reading_found_title),
            style = JaugeTheme.typography.numberCaption.copy(fontWeight = FontWeight.Bold, letterSpacing = 0.1.em),
            color = colors.textSecondary,
        )
        Row(modifier = Modifier.padding(top = 10.dp)) {
            FoundValue(
                label = stringResource(Res.string.reading_found_published),
                value = progress.publishedDaysAgo?.let { days ->
                    when (days) {
                        0 -> stringResource(Res.string.reading_published_today)
                        1 -> stringResource(Res.string.reading_published_yesterday)
                        else -> stringResource(Res.string.reading_published_days, days)
                    }
                }.orEmpty(),
                valueColor = colors.textPrimary,
                modifier = Modifier.weight(1f),
            )
            FoundValue(
                label = stringResource(Res.string.reading_found_salary),
                value = when (progress.hasDisplayedSalary) {
                    true -> stringResource(Res.string.reading_salary_shown)
                    false -> stringResource(Res.string.reading_salary_none)
                    null -> ""
                },
                valueColor = if (progress.hasDisplayedSalary == false) colors.disputed.accent else colors.textPrimary,
                modifier = Modifier.weight(1f),
            )
        }
        if (progress.hasDisplayedSalary == false) {
            Box(Modifier.fillMaxWidth().padding(vertical = 9.dp).height(1.dp).background(colors.outline))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(JaugeIcons.Euro, contentDescription = null, tint = colors.disputed.accent, modifier = Modifier.size(18.dp))
                Text(
                    text = buildAnnotatedString {
                        append(stringResource(Res.string.reading_salary_estimated_start))
                        withStyle(SpanStyle(color = colors.textPrimary, fontWeight = FontWeight.Bold)) {
                            append(stringResource(Res.string.reading_salary_estimated_bold))
                        }
                        append(stringResource(Res.string.reading_salary_estimated_end))
                    },
                    style = JaugeTheme.typography.bodySmall.copy(lineHeight = 18.sp),
                    color = colors.textSecondary,
                )
            }
        }
    }
}

@Composable
private fun FoundValue(label: String, value: String, valueColor: Color, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Text(text = label, style = JaugeTheme.typography.caption.copy(fontSize = 12.5.sp), color = JaugeTheme.colors.textSecondary)
        Text(
            text = value,
            style = JaugeTheme.typography.bodyStrong,
            color = valueColor,
            modifier = Modifier.padding(top = 2.dp),
        )
    }
}
