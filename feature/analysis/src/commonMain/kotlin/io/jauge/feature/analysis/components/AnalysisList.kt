package io.jauge.feature.analysis.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.jauge.core.designsystem.component.GaugeLevel
import io.jauge.core.designsystem.component.GaugeSize
import io.jauge.core.designsystem.component.JaugeCard
import io.jauge.core.designsystem.component.JaugeGauge
import io.jauge.core.designsystem.component.LevelPill
import io.jauge.core.designsystem.theme.JaugeTheme
import io.jauge.core.domain.model.AnalysisSummary
import io.jauge.feature.analysis.resources.Res
import io.jauge.feature.analysis.resources.age_days
import io.jauge.feature.analysis.resources.age_today
import io.jauge.feature.analysis.resources.age_weeks
import io.jauge.feature.analysis.resources.age_yesterday
import io.jauge.feature.analysis.resources.analysis_row_place
import io.jauge.feature.analysis.resources.analysis_row_place_with_age
import kotlinx.datetime.TimeZone
import kotlinx.datetime.daysUntil
import kotlinx.datetime.toLocalDateTime
import kotlinx.datetime.todayIn
import org.jetbrains.compose.resources.stringResource
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * Card listing analyses (mockup 02, "Mes offres jaugées"), one row per analysis separated by thin lines.
 *
 * @param analyses analyses to show, newest first.
 * @param onOpenAnalysis called with the identifier of the tapped analysis.
 * @param modifier modifier of the card.
 * @param animateGauges false to draw the gauges at once (screenshots).
 */
@Composable
fun AnalysisListCard(
    analyses: List<AnalysisSummary>,
    onOpenAnalysis: (String) -> Unit,
    modifier: Modifier = Modifier,
    animateGauges: Boolean = true,
) {
    JaugeCard(modifier = modifier.fillMaxWidth(), contentPadding = PaddingValues(0.dp)) {
        analyses.forEachIndexed { index, analysis ->
            AnalysisRow(analysis = analysis, onClick = { onOpenAnalysis(analysis.id) }, animateGauge = animateGauges)
            if (index < analyses.lastIndex) {
                HorizontalDivider(color = JaugeTheme.colors.outline, thickness = 1.dp)
            }
        }
    }
}

/**
 * One analysis: mini gauge, job title, company, place and age, level pill.
 *
 * @param analysis the analysis.
 * @param onClick called on tap.
 * @param modifier modifier of the row.
 * @param animateGauge false to draw the gauge at once.
 */
@Composable
fun AnalysisRow(analysis: AnalysisSummary, onClick: () -> Unit, modifier: Modifier = Modifier, animateGauge: Boolean = true) {
    val level = GaugeLevel.valueOf(analysis.level.name)
    Row(
        modifier = modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        JaugeGauge(score = analysis.score, size = GaugeSize.MINI, animate = animateGauge)
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = analysis.jobTitle,
                style = JaugeTheme.typography.bodyStrong,
                color = JaugeTheme.colors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = subtitleOf(analysis),
                style = JaugeTheme.typography.label.copy(fontWeight = FontWeight.Normal, fontSize = 13.sp),
                color = JaugeTheme.colors.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
        LevelPill(level = level)
    }
}

@Composable
private fun subtitleOf(analysis: AnalysisSummary): String {
    val age = ageLabel(analysis.createdAt)
    val company = analysis.company?.takeIf { it.isNotBlank() }
    return when {
        company == null -> stringResource(Res.string.analysis_row_place, analysis.place, age)
        else -> stringResource(Res.string.analysis_row_place_with_age, company, analysis.place, age)
    }
}

/**
 * Short age of an analysis: "aujourd'hui", "hier", "5 j", "3 sem.".
 *
 * @param createdAt creation time.
 * @param now current time, for tests and screenshots.
 */
@Composable
fun ageLabel(createdAt: Instant, now: Instant = Clock.System.now()): String {
    val timeZone = TimeZone.currentSystemDefault()
    val days = createdAt.toLocalDateTime(timeZone).date.daysUntil(now.toLocalDateTime(timeZone).date).coerceAtLeast(0)
    return when {
        days == 0 -> stringResource(Res.string.age_today)
        days == 1 -> stringResource(Res.string.age_yesterday)
        days < DAYS_PER_WEEK -> stringResource(Res.string.age_days, days)
        else -> stringResource(Res.string.age_weeks, days / DAYS_PER_WEEK)
    }
}

private const val DAYS_PER_WEEK = 7
