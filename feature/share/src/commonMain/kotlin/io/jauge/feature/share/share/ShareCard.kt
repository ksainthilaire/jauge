package io.jauge.feature.share.share

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.jauge.core.designsystem.component.GaugeLevel
import io.jauge.core.designsystem.component.GaugeSize
import io.jauge.core.designsystem.component.JaugeGauge
import io.jauge.core.designsystem.component.colors
import io.jauge.core.designsystem.icon.JaugeIcons
import io.jauge.core.designsystem.theme.JaugeTheme
import io.jauge.core.domain.model.Analysis
import io.jauge.core.domain.model.ShareFormat
import io.jauge.core.domain.model.ShareLinks
import io.jauge.core.domain.model.ShareOptions
import io.jauge.core.domain.model.TwinOffer
import io.jauge.feature.share.common.formatGrouped
import io.jauge.feature.share.common.formatSigned
import io.jauge.feature.share.common.joinWithDots
import io.jauge.feature.share.common.labelOrNull
import io.jauge.feature.share.resources.Res
import io.jauge.feature.share.resources.salary_range_thousands
import io.jauge.feature.share.resources.share_card_applicants_caption
import io.jauge.feature.share.resources.share_card_applicants_value
import io.jauge.feature.share.resources.share_card_brand
import io.jauge.feature.share.resources.share_card_date
import io.jauge.feature.share.resources.share_card_description
import io.jauge.feature.share.resources.share_card_footer
import io.jauge.feature.share.resources.share_card_link_caption
import io.jauge.feature.share.resources.share_card_link_hint
import io.jauge.feature.share.resources.share_card_salary_caption
import io.jauge.feature.share.resources.share_card_salary_value
import io.jauge.feature.share.resources.share_card_score_suffix
import io.jauge.feature.share.resources.share_card_trend_caption
import io.jauge.feature.share.resources.share_card_trend_value
import io.jauge.feature.share.resources.share_card_twin_detail
import io.jauge.feature.share.resources.share_card_twin_title
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.resources.stringResource

/**
 * The share card of mockup 07, always on the dark brand background. Its pixels become the shared image.
 *
 * @param analysis the shared analysis.
 * @param calmTwin calm twin offer shown at the bottom, null to hide it.
 * @param options format and options.
 * @param modifier modifier of the card (its size is set by the format).
 * @param animateGauge false to draw the gauge at its final position (screenshots and image capture).
 */
@Composable
fun ShareCard(
    analysis: Analysis,
    calmTwin: TwinOffer?,
    options: ShareOptions,
    modifier: Modifier = Modifier,
    animateGauge: Boolean = false,
) {
    JaugeTheme(darkTheme = true) {
        val colors = JaugeTheme.colors
        val glow = colors.crowded.accent
        val description = stringResource(Res.string.share_card_description, analysis.offer.jobTitle, analysis.score)
        Column(
            modifier = modifier
                .clip(JaugeTheme.radii.run { RoundedCornerShape(large) })
                .background(colors.background)
                .drawBehind {
                    // Soft coral glow in the top right corner.
                    drawCircle(
                        brush = Brush.radialGradient(
                            listOf(glow.copy(alpha = 0.35f), Color.Transparent),
                            center = Offset(size.width - 30.dp.toPx(), 30.dp.toPx()),
                            radius = 90.dp.toPx(),
                        ),
                        radius = 90.dp.toPx(),
                        center = Offset(size.width - 30.dp.toPx(), 30.dp.toPx()),
                    )
                }
                .padding(18.dp)
                .semantics { contentDescription = description },
        ) {
            CardHeader(analysis)
            when (options.format) {
                ShareFormat.LINK -> LinkCardBody(analysis, options)
                else -> GaugeCardBody(analysis, calmTwin, options, animateGauge)
            }
        }
    }
}

@Composable
private fun CardHeader(analysis: Analysis) {
    val colors = JaugeTheme.colors
    val date = analysis.createdAt.toLocalDateTime(TimeZone.currentSystemDefault()).date
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        JaugeGauge(
            score = 72,
            size = GaugeSize.MINI,
            needleColor = colors.textPrimary,
            dimInactiveSegments = false,
            animate = false,
            modifier = Modifier.size(24.dp, 17.dp),
        )
        Text(
            text = stringResource(Res.string.share_card_brand),
            style = JaugeTheme.typography.sectionTitle.copy(fontSize = 15.sp, lineHeight = 18.sp),
            color = colors.textPrimary,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = stringResource(Res.string.share_card_date, date.day.twoDigits(), (date.month.ordinal + 1).twoDigits(), (date.year % 100).twoDigits()),
            style = JaugeTheme.typography.numberCaption.copy(fontSize = 11.sp, letterSpacing = 0.sp),
            color = colors.textSecondary,
        )
    }
}

private fun Int.twoDigits(): String = toString().padStart(2, '0')

@Composable
private fun OfferTitle(analysis: Analysis, options: ShareOptions) {
    val colors = JaugeTheme.colors
    val offer = analysis.offer
    Text(
        text = offer.jobTitle,
        style = JaugeTheme.typography.title.copy(fontSize = 21.sp, lineHeight = 23.sp),
        color = colors.textPrimary,
        maxLines = if (options.format == ShareFormat.SQUARE) 1 else 2,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.padding(top = 14.dp),
    )
    Text(
        text = joinWithDots(offer.company.takeUnless { options.hideCompany }, offer.city, offer.contractType.labelOrNull()),
        style = JaugeTheme.typography.caption,
        color = colors.textSecondary,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.padding(top = 4.dp),
    )
}

@Composable
private fun ColumnScope.GaugeCardBody(analysis: Analysis, calmTwin: TwinOffer?, options: ShareOptions, animateGauge: Boolean) {
    val colors = JaugeTheme.colors
    val isStory = options.format == ShareFormat.STORY
    val level = GaugeLevel.valueOf(analysis.level.name)
    OfferTitle(analysis, options)
    val score = buildAnnotatedString {
        withStyle(JaugeTheme.typography.numberLarge.toSpanStyle().copy(fontSize = 32.sp, letterSpacing = 0.sp)) {
            append(analysis.score.toString())
        }
        withStyle(SpanStyle(fontSize = 13.sp, color = colors.textSecondary)) {
            append(stringResource(Res.string.share_card_score_suffix))
        }
        withStyle(SpanStyle(fontSize = 14.sp, fontWeight = FontWeight.Bold, color = level.colors.accent)) {
            append(stringResource(level.label).lowercase())
        }
    }
    if (isStory) {
        JaugeGauge(
            score = analysis.score,
            size = GaugeSize.MEDIUM,
            needleColor = colors.textPrimary,
            animate = animateGauge,
            modifier = Modifier.padding(top = 8.dp).fillMaxWidth().height(104.dp),
        )
        Text(
            text = score,
            style = JaugeTheme.typography.body,
            color = colors.textPrimary,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    } else {
        Row(modifier = Modifier.padding(top = 10.dp), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            JaugeGauge(
                score = analysis.score,
                size = GaugeSize.MEDIUM,
                needleColor = colors.textPrimary,
                animate = animateGauge,
                modifier = Modifier.size(124.dp, 64.dp),
            )
            Text(text = score, style = JaugeTheme.typography.body, color = colors.textPrimary)
        }
    }
    Row(modifier = Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        StatBox(
            value = stringResource(Res.string.share_card_applicants_value, formatGrouped(analysis.applicants.estimate)),
            caption = stringResource(Res.string.share_card_applicants_caption),
            modifier = Modifier.weight(1f),
        )
        analysis.estimatedSalary?.takeIf { it.isValid }?.let { salary ->
            StatBox(
                value = stringResource(Res.string.share_card_salary_value, salary.minimumThousands, salary.maximumThousands),
                caption = stringResource(Res.string.share_card_salary_caption),
                modifier = Modifier.weight(1f),
            )
        }
        StatBox(
            value = stringResource(Res.string.share_card_trend_value, formatSigned(analysis.trendThreeMonthsPercent)),
            caption = stringResource(Res.string.share_card_trend_caption),
            modifier = Modifier.weight(1f),
        )
    }
    if (isStory && calmTwin != null) {
        CalmTwinBox(calmTwin)
    }
    Spacer(Modifier.weight(1f))
    Footer()
}

@Composable
private fun StatBox(value: String, caption: String, modifier: Modifier = Modifier) {
    val colors = JaugeTheme.colors
    Column(
        modifier = modifier.background(colors.surface, RoundedCornerShape(12.dp)).padding(horizontal = 4.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(value, style = JaugeTheme.typography.numberSmall.copy(fontSize = 14.sp), color = colors.textPrimary, maxLines = 1)
        Text(
            caption,
            style = JaugeTheme.typography.caption.copy(fontSize = 11.sp, lineHeight = 14.sp),
            color = colors.textSecondary,
            maxLines = 1,
            modifier = Modifier.padding(top = 2.dp),
        )
    }
}

@Composable
private fun CalmTwinBox(twin: TwinOffer) {
    val colors = JaugeTheme.colors
    val shape = RoundedCornerShape(12.dp)
    Row(
        modifier = Modifier
            .padding(top = 10.dp)
            .fillMaxWidth()
            .background(colors.calm.accent.copy(alpha = 0.12f), shape)
            .border(1.dp, colors.calm.accent.copy(alpha = 0.35f), shape)
            .padding(horizontal = 11.dp, vertical = 9.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        JaugeGauge(score = twin.score, size = GaugeSize.MINI, needleColor = colors.textPrimary, animate = false, modifier = Modifier.size(38.dp, 26.dp))
        Column {
            Text(
                text = stringResource(Res.string.share_card_twin_title, twin.distanceKilometers),
                style = JaugeTheme.typography.caption.copy(fontWeight = FontWeight.Bold),
                color = colors.calm.accent,
            )
            val salary = twin.salary?.takeIf { it.isValid }?.let {
                stringResource(Res.string.salary_range_thousands, it.minimumThousands, it.maximumThousands)
            }
            Text(
                text = joinWithDots(stringResource(Res.string.share_card_twin_detail, twin.estimatedApplicants), salary),
                style = JaugeTheme.typography.caption,
                color = colors.textSecondary,
            )
        }
    }
}

@Composable
private fun ColumnScope.LinkCardBody(analysis: Analysis, options: ShareOptions) {
    val colors = JaugeTheme.colors
    OfferTitle(analysis, options)
    Spacer(Modifier.weight(1f))
    Row(
        modifier = Modifier.fillMaxWidth().background(colors.surface, RoundedCornerShape(14.dp)).padding(14.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(36.dp).background(colors.action, RoundedCornerShape(11.dp)), contentAlignment = Alignment.Center) {
            Icon(JaugeIcons.Link, contentDescription = null, tint = colors.onAction, modifier = Modifier.size(20.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(stringResource(Res.string.share_card_link_caption), style = JaugeTheme.typography.caption, color = colors.textSecondary)
            Text(
                text = ShareLinks.forAnalysis(analysis.id).removePrefix("https://"),
                style = JaugeTheme.typography.numberSmall,
                color = colors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
    Text(
        text = stringResource(Res.string.share_card_link_hint),
        style = JaugeTheme.typography.caption,
        color = colors.textSecondary,
        modifier = Modifier.padding(top = 10.dp),
    )
    Spacer(Modifier.weight(1f))
    Footer()
}

@Composable
private fun Footer() {
    Text(
        text = stringResource(Res.string.share_card_footer),
        style = JaugeTheme.typography.caption.copy(fontSize = 11.sp),
        color = JaugeTheme.colors.textSecondary,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth(),
    )
}

