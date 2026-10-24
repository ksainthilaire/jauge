package io.jauge.feature.verdict.details

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.jauge.core.designsystem.component.JaugeCard
import io.jauge.core.designsystem.component.JaugeChip
import io.jauge.core.designsystem.component.JaugeChipStyle
import io.jauge.core.designsystem.component.JaugeRefreshContainer
import io.jauge.core.designsystem.component.JaugeScreenScaffold
import io.jauge.core.designsystem.component.RoundIconButton
import io.jauge.core.designsystem.component.SkeletonCard
import io.jauge.core.designsystem.icon.JaugeIcons
import io.jauge.core.designsystem.theme.JaugeTheme
import io.jauge.core.domain.model.Reliability
import io.jauge.core.domain.model.SalaryInsight
import io.jauge.core.domain.model.SalaryPosition
import io.jauge.core.domain.model.SkillDemand
import io.jauge.core.domain.model.SkillsInsight
import io.jauge.core.domain.model.TrendInsight
import io.jauge.core.ui.error.FriendlyErrorView
import io.jauge.core.ui.state.ContentState
import io.jauge.feature.verdict.common.FractionLabel
import io.jauge.feature.verdict.common.LabelAnchor
import io.jauge.feature.verdict.common.formatGrouped
import io.jauge.feature.verdict.common.label
import io.jauge.feature.verdict.common.signedPercent
import io.jauge.feature.verdict.resources.Res
import io.jauge.feature.verdict.resources.action_back
import io.jauge.feature.verdict.resources.details_methodology_caption
import io.jauge.feature.verdict.resources.details_methodology_title
import io.jauge.feature.verdict.resources.details_salary_above_median
import io.jauge.feature.verdict.resources.details_salary_around_median
import io.jauge.feature.verdict.resources.details_salary_below_median
import io.jauge.feature.verdict.resources.details_salary_estimated_between
import io.jauge.feature.verdict.resources.details_salary_median
import io.jauge.feature.verdict.resources.details_salary_middle_half
import io.jauge.feature.verdict.resources.details_salary_negotiate
import io.jauge.feature.verdict.resources.details_salary_negotiate_below
import io.jauge.feature.verdict.resources.details_salary_offer_count
import io.jauge.feature.verdict.resources.details_salary_range_bold
import io.jauge.feature.verdict.resources.details_salary_scale_bound
import io.jauge.feature.verdict.resources.details_salary_target_bold
import io.jauge.feature.verdict.resources.details_salary_this_offer
import io.jauge.feature.verdict.resources.details_salary_title
import io.jauge.feature.verdict.resources.details_salary_unknown
import io.jauge.feature.verdict.resources.details_section_unavailable
import io.jauge.feature.verdict.resources.details_skills_caption
import io.jauge.feature.verdict.resources.details_skills_empty
import io.jauge.feature.verdict.resources.details_skills_owned
import io.jauge.feature.verdict.resources.details_skills_title
import io.jauge.feature.verdict.resources.details_skills_to_add
import io.jauge.feature.verdict.resources.details_tab_salaries
import io.jauge.feature.verdict.resources.details_tab_skills
import io.jauge.feature.verdict.resources.details_tab_trend
import io.jauge.feature.verdict.resources.details_title
import io.jauge.feature.verdict.resources.details_trend_description
import io.jauge.feature.verdict.resources.details_trend_empty
import io.jauge.feature.verdict.resources.details_trend_month
import io.jauge.feature.verdict.resources.details_trend_title
import io.jauge.feature.verdict.resources.months_short
import io.jauge.feature.verdict.resources.percent_signed
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringArrayResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

/**
 * Salaries, skills and trend of an analysis (mockup 05).
 *
 * @param analysisId identifier of the analysis.
 * @param initialSection section to scroll to when the screen opens.
 * @param onBack called when the user leaves the screen.
 * @param onOpenMethodology opens "Comment on estime".
 */
@Composable
fun DetailsScreen(
    analysisId: String,
    initialSection: DetailsSection,
    onBack: () -> Unit,
    onOpenMethodology: (String) -> Unit,
) {
    val viewModel: DetailsViewModel = koinViewModel(key = "details-$analysisId") { parametersOf(analysisId) }
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    DetailsLayout(
        state = state,
        initialSection = initialSection,
        onBack = onBack,
        onRefresh = viewModel::refresh,
        onOpenMethodology = { onOpenMethodology(analysisId) },
    )
}

/**
 * Stateless layout of the details screen, also used by screenshot tests.
 *
 * @param state observed state.
 * @param initialSection section to scroll to first.
 * @param onBack back action.
 * @param onRefresh reloads every section.
 * @param onOpenMethodology opens the methodology.
 */
@Composable
fun DetailsLayout(
    state: DetailsUiState,
    initialSection: DetailsSection,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onOpenMethodology: () -> Unit,
) {
    val scrollState = rememberScrollState()
    val sectionOffsets = remember { mutableStateMapOf<DetailsSection, Int>() }
    var tappedSection by remember { mutableStateOf<DetailsSection?>(initialSection.takeIf { it != DetailsSection.SALARIES }) }
    val coroutineScope = rememberCoroutineScope()
    val selectedSection by remember {
        derivedStateOf {
            val tapped = tappedSection
            if (tapped != null && !scrollState.canScrollForward) {
                tapped
            } else {
                DetailsSection.entries.lastOrNull { section ->
                    (sectionOffsets[section] ?: Int.MAX_VALUE) <= scrollState.value + 24
                } ?: DetailsSection.SALARIES
            }
        }
    }
    LaunchedEffect(initialSection, sectionOffsets[initialSection]) {
        val offset = sectionOffsets[initialSection]
        if (initialSection != DetailsSection.SALARIES && offset != null && scrollState.value == 0) {
            scrollState.scrollTo(offset)
        }
    }

    JaugeScreenScaffold(
        title = stringResource(Res.string.details_title),
        navigationIcon = { RoundIconButton(JaugeIcons.Back, stringResource(Res.string.action_back), onBack) },
    ) {
        Row(
            modifier = Modifier.padding(horizontal = JaugeTheme.spacing.screenGutter).padding(top = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            DetailsSection.entries.forEach { section ->
                JaugeChip(
                    text = stringResource(section.label),
                    selected = section == selectedSection,
                    style = JaugeChipStyle.SEGMENT,
                    onClick = {
                        tappedSection = section
                        coroutineScope.launch { scrollState.animateScrollTo(sectionOffsets[section] ?: 0) }
                    },
                )
            }
        }
        JaugeRefreshContainer(
            isRefreshing = state.isRefreshing,
            onRefresh = onRefresh,
            modifier = Modifier.fillMaxSize().padding(top = JaugeTheme.spacing.medium),
        ) {
            DetailsSections(
                state = state,
                scrollState = scrollState,
                onSectionPositioned = { section, offset -> sectionOffsets[section] = offset },
                onRefresh = onRefresh,
                onOpenMethodology = onOpenMethodology,
            )
        }
    }
}

private val DetailsSection.label: StringResource
    get() = when (this) {
        DetailsSection.SALARIES -> Res.string.details_tab_salaries
        DetailsSection.SKILLS -> Res.string.details_tab_skills
        DetailsSection.TREND -> Res.string.details_tab_trend
    }

@Composable
private fun DetailsSections(
    state: DetailsUiState,
    scrollState: ScrollState,
    onSectionPositioned: (DetailsSection, Int) -> Unit,
    onRefresh: () -> Unit,
    onOpenMethodology: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(horizontal = JaugeTheme.spacing.screenGutter)
            .padding(bottom = JaugeTheme.spacing.section),
        verticalArrangement = Arrangement.spacedBy(JaugeTheme.spacing.medium),
    ) {
        if (state.showsFullScreenError) {
            FriendlyErrorView(error = state.salary.error!!, onAction = { onRefresh() })
            return@Column
        }
        val sectionModifier = { section: DetailsSection ->
            Modifier.onGloballyPositioned { onSectionPositioned(section, it.positionInParent().y.toInt()) }
        }
        SectionCard(state.salary, sectionModifier(DetailsSection.SALARIES), skeletonHeight = 150) { SalaryCardContent(it) }
        SectionCard(state.skills, sectionModifier(DetailsSection.SKILLS), skeletonHeight = 170) { SkillsCardContent(it) }
        SectionCard(state.trend, sectionModifier(DetailsSection.TREND), skeletonHeight = 90) { TrendCardContent(it) }
        state.reliability?.let { MethodologyLinkCard(it, onOpenMethodology) }
    }
}

@Composable
private fun <T> SectionCard(
    state: ContentState<T>,
    modifier: Modifier,
    skeletonHeight: Int,
    content: @Composable ColumnScope.(T) -> Unit,
) {
    val value = state.content
    when {
        value != null -> JaugeCard(modifier = modifier.fillMaxWidth()) { content(value) }
        state.isLoading -> SkeletonCard(modifier = modifier.fillMaxWidth(), height = skeletonHeight.dp)
        else -> JaugeCard(modifier = modifier.fillMaxWidth()) {
            Text(
                text = stringResource(Res.string.details_section_unavailable),
                style = JaugeTheme.typography.bodySmall,
                color = JaugeTheme.colors.textSecondary,
            )
        }
    }
}

@Composable
private fun CardHeader(title: String, trailing: @Composable () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = title,
            style = JaugeTheme.typography.bodyStrong,
            color = JaugeTheme.colors.textPrimary,
            modifier = Modifier.weight(1f),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        trailing()
    }
}

@Composable
private fun SalaryCardContent(insight: SalaryInsight) {
    val colors = JaugeTheme.colors
    val typography = JaugeTheme.typography
    val mono = typography.numberCaption.copy(fontSize = 12.sp, letterSpacing = 0.sp)
    CardHeader(stringResource(Res.string.details_salary_title, insight.areaName)) {
        Text(
            text = stringResource(Res.string.details_salary_offer_count, formatGrouped(insight.offerCount)),
            style = typography.caption,
            color = colors.textSecondary,
        )
    }
    val offerRange = insight.offerRange
    Box(Modifier.height(16.dp))
    if (offerRange != null) {
        FractionLabel(fraction = insight.fractionOf(offerRange.minimumThousands), anchor = LabelAnchor.START) {
            Text(
                text = stringResource(Res.string.details_salary_this_offer, offerRange.minimumThousands, offerRange.maximumThousands),
                style = typography.numberCaption.copy(fontWeight = FontWeight.Bold, letterSpacing = 0.sp),
                color = colors.crowded.content,
            )
        }
    }
    SalaryScaleBar(insight = insight)
    Box(Modifier.fillMaxWidth().padding(top = 2.dp)) {
        Text(stringResource(Res.string.details_salary_scale_bound, insight.scaleMinimumThousands), style = mono, color = colors.textSecondary)
        FractionLabel(fraction = insight.fractionOf(insight.medianThousands), anchor = LabelAnchor.CENTER) {
            Text(
                text = stringResource(Res.string.details_salary_median, insight.medianThousands),
                style = mono.copy(fontWeight = FontWeight.Bold),
                color = colors.textPrimary,
            )
        }
        Text(
            text = stringResource(Res.string.details_salary_scale_bound, insight.scaleMaximumThousands),
            style = mono,
            color = colors.textSecondary,
            modifier = Modifier.align(Alignment.CenterEnd),
        )
    }
    Row(
        modifier = Modifier.padding(top = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(18.dp, 8.dp).background(colors.textTertiary, RoundedCornerShape(4.dp)))
        Text(
            text = stringResource(Res.string.details_salary_middle_half, insight.areaName),
            style = typography.caption.copy(fontSize = 11.5.sp),
            color = colors.textSecondary,
        )
    }
    Text(
        text = salarySummary(insight),
        style = typography.bodySmall,
        color = colors.textPrimary,
        modifier = Modifier.padding(top = 12.dp),
    )
}

@Composable
private fun salarySummary(insight: SalaryInsight) = buildAnnotatedString {
    val range = insight.offerRange
    if (range == null) {
        append(stringResource(Res.string.details_salary_unknown))
        return@buildAnnotatedString
    }
    val bold = SpanStyle(fontWeight = FontWeight.Bold, color = JaugeTheme.colors.textPrimary)
    append(stringResource(Res.string.details_salary_estimated_between))
    withStyle(bold) { append(stringResource(Res.string.details_salary_range_bold, range.minimumThousands, range.maximumThousands)) }
    val position = insight.position
    append(
        when (position) {
            SalaryPosition.ABOVE_MEDIAN -> stringResource(Res.string.details_salary_above_median, insight.areaName)
            SalaryPosition.BELOW_MEDIAN -> stringResource(Res.string.details_salary_below_median, insight.areaName)
            else -> stringResource(Res.string.details_salary_around_median, insight.areaName)
        },
    )
    insight.negotiationTargetThousands?.let { target ->
        append(
            if (position == SalaryPosition.BELOW_MEDIAN) {
                stringResource(Res.string.details_salary_negotiate_below)
            } else {
                stringResource(Res.string.details_salary_negotiate)
            },
        )
        withStyle(bold) { append(stringResource(Res.string.details_salary_target_bold, target)) }
        append(".")
    }
}

@Composable
private fun SkillsCardContent(insight: SkillsInsight) {
    val colors = JaugeTheme.colors
    CardHeader(stringResource(Res.string.details_skills_title)) {
        Text(stringResource(Res.string.details_skills_caption), style = JaugeTheme.typography.caption, color = colors.textSecondary)
    }
    if (insight.skills.isEmpty()) {
        Text(
            text = stringResource(Res.string.details_skills_empty),
            style = JaugeTheme.typography.bodySmall,
            color = colors.textSecondary,
            modifier = Modifier.padding(top = 12.dp),
        )
    }
    insight.skills.forEach { SkillRow(it) }
}

@Composable
private fun SkillRow(skill: SkillDemand) {
    val colors = JaugeTheme.colors
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 12.dp).height(20.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = skill.name,
            style = JaugeTheme.typography.body.copy(fontSize = 14.sp),
            color = colors.textPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.width(112.dp),
        )
        Box(Modifier.weight(1f).height(8.dp).background(colors.backgroundAlternative, RoundedCornerShape(4.dp))) {
            Box(
                Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(skill.sharePercent / 100f)
                    .background(colors.textPrimary, RoundedCornerShape(4.dp)),
            )
        }
        Text(
            text = stringResource(Res.string.percent_signed, skill.sharePercent.toString()),
            style = JaugeTheme.typography.numberCaption.copy(fontSize = 12.sp, letterSpacing = 0.sp),
            color = colors.textSecondary,
            textAlign = androidx.compose.ui.text.style.TextAlign.End,
            modifier = Modifier.width(38.dp),
        )
        Box(Modifier.widthIn(min = 62.dp), contentAlignment = Alignment.CenterEnd) {
            when (skill.isOwned) {
                true -> {
                    val owned = stringResource(Res.string.details_skills_owned)
                    Icon(
                        JaugeIcons.Check,
                        contentDescription = owned,
                        tint = colors.calm.content,
                        modifier = Modifier.size(18.dp),
                    )
                }
                false -> Text(
                    text = stringResource(Res.string.details_skills_to_add),
                    style = JaugeTheme.typography.pill.copy(fontSize = 11.sp, lineHeight = 14.sp),
                    color = colors.disputed.content,
                    maxLines = 1,
                    modifier = Modifier
                        .background(colors.disputed.container, RoundedCornerShape(8.dp))
                        .padding(horizontal = 6.dp, vertical = 3.dp),
                )
                null -> Unit
            }
        }
    }
}

@Composable
private fun TrendCardContent(trend: TrendInsight) {
    val colors = JaugeTheme.colors
    val change = signedPercent(trend.changeThreeMonthsPercent)
    CardHeader(stringResource(Res.string.details_trend_title)) {
        Text(
            text = change,
            style = JaugeTheme.typography.numberSmall,
            color = if (trend.changeThreeMonthsPercent > 0) colors.crowded.content else colors.calm.content,
        )
    }
    if (trend.points.size < 2) {
        Text(
            text = stringResource(Res.string.details_trend_empty),
            style = JaugeTheme.typography.bodySmall,
            color = colors.textSecondary,
            modifier = Modifier.padding(top = 12.dp),
        )
        return
    }
    TrendLineChart(
        points = trend.points,
        description = stringResource(Res.string.details_trend_description, change),
        modifier = Modifier.padding(top = 4.dp),
    )
    val months = stringArrayResource(Res.array.months_short)
    val labels = listOf(trend.points.first(), trend.points[trend.points.size / 2], trend.points.last()).map { point ->
        stringResource(
            Res.string.details_trend_month,
            months.getOrElse(point.month.month.ordinal) { "" },
            (point.month.year % 100).toString().padStart(2, '0'),
        )
    }
    Row(modifier = Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        labels.forEach { label ->
            Text(
                text = label,
                style = JaugeTheme.typography.numberCaption.copy(fontSize = 11.sp, letterSpacing = 0.sp),
                color = colors.textSecondary,
            )
        }
    }
}

@Composable
private fun MethodologyLinkCard(reliability: Reliability, onOpenMethodology: () -> Unit) {
    val colors = JaugeTheme.colors
    JaugeCard(
        modifier = Modifier.fillMaxWidth(),
        onClick = onOpenMethodology,
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 9.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier.size(30.dp).background(colors.disputed.container, RoundedCornerShape(9.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(JaugeIcons.Info, contentDescription = null, tint = colors.disputed.content, modifier = Modifier.size(17.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(
                    text = stringResource(Res.string.details_methodology_title),
                    style = JaugeTheme.typography.bodyStrong.copy(fontSize = 13.5.sp),
                    color = colors.textPrimary,
                )
                Text(
                    text = stringResource(Res.string.details_methodology_caption, reliability.label(), reliability.typicalMarginPercent),
                    style = JaugeTheme.typography.caption.copy(fontSize = 12.5.sp),
                    color = colors.textSecondary,
                )
            }
            Icon(JaugeIcons.ChevronRight, contentDescription = null, tint = colors.textSecondary, modifier = Modifier.size(18.dp))
        }
    }
}
