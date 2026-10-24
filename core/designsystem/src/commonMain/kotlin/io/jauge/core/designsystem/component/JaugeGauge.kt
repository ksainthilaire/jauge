package io.jauge.core.designsystem.component

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.jauge.core.designsystem.resources.Res
import io.jauge.core.designsystem.resources.gauge_description
import io.jauge.core.designsystem.theme.JaugeTheme
import org.jetbrains.compose.resources.stringResource
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * Size presets of [JaugeGauge], taken from the mockups.
 *
 * @property strokeWidth thickness of the three arcs.
 * @property needleWidth thickness of the needle.
 * @property needleInset distance between the arcs and the tip of the needle.
 * @property gapDegrees gap between two segments, in degrees.
 */
enum class GaugeSize(
    val strokeWidth: Dp,
    val needleWidth: Dp,
    val needleInset: Dp,
    val gapDegrees: Float,
) {
    /** Large gauge of the verdict card (mockup 04). */
    LARGE(strokeWidth = 18.dp, needleWidth = 5.dp, needleInset = 10.dp, gapDegrees = 4f),

    /** Medium gauge for dark cards and the share card. */
    MEDIUM(strokeWidth = 13.dp, needleWidth = 4.dp, needleInset = 8.dp, gapDegrees = 4f),

    /** Mini gauge of list rows, 44 x 30 (mockup 02). */
    MINI(strokeWidth = 6.dp, needleWidth = 2.4.dp, needleInset = (-1).dp, gapDegrees = 7f),
}

/**
 * The competition gauge: a half circle in three segments (calm, disputed, crowded) and a needle pointing at the score.
 * The needle sweeps from 0 to the score when the gauge appears, then animates to every new score.
 *
 * @param score competition score between 0 and 100.
 * @param modifier modifier of the gauge; the gauge keeps a 2:1 ratio unless sized explicitly.
 * @param size size preset.
 * @param needleColor color of the needle and its pivot (paper on dark cards, ink on light rows).
 * @param dimInactiveSegments true to fade the segments that do not contain the score.
 * @param animate false to draw the final position at once (screenshots, previews, reduced motion).
 */
@Composable
fun JaugeGauge(
    score: Int,
    modifier: Modifier = Modifier,
    size: GaugeSize = GaugeSize.LARGE,
    needleColor: Color = JaugeTheme.colors.textPrimary,
    dimInactiveSegments: Boolean = true,
    animate: Boolean = true,
) {
    val clampedScore = score.coerceIn(0, 100)
    val level = GaugeLevel.fromScore(clampedScore)
    val animatedScore = remember { Animatable(if (animate) 0f else clampedScore.toFloat()) }
    LaunchedEffect(clampedScore, animate) {
        if (animate) {
            animatedScore.animateTo(clampedScore.toFloat(), tween(durationMillis = 900, easing = FastOutSlowInEasing))
        } else {
            animatedScore.snapTo(clampedScore.toFloat())
        }
    }
    val segmentColors = listOf(JaugeTheme.colors.calm.accent, JaugeTheme.colors.disputed.accent, JaugeTheme.colors.crowded.accent)
    val description = stringResource(Res.string.gauge_description, clampedScore, stringResource(level.label))
    val sizedModifier = if (size == GaugeSize.MINI) modifier.size(44.dp, 30.dp) else modifier.aspectRatio(2f)

    Canvas(modifier = sizedModifier.semantics { contentDescription = description }) {
        drawGauge(
            value = animatedScore.value,
            activeLevel = level,
            segmentColors = segmentColors,
            needleColor = needleColor,
            size = size,
            dimInactiveSegments = dimInactiveSegments,
        )
    }
}

private fun DrawScope.drawGauge(
    value: Float,
    activeLevel: GaugeLevel,
    segmentColors: List<Color>,
    needleColor: Color,
    size: GaugeSize,
    dimInactiveSegments: Boolean,
) {
    val strokeWidth = size.strokeWidth.toPx()
    val padding = 2.dp.toPx()
    val centerX = this.size.width / 2f
    val centerY = this.size.height - strokeWidth / 2f - padding
    val radius = min(centerX - strokeWidth / 2f - padding, centerY - strokeWidth / 2f - padding)
    val arcTopLeft = Offset(centerX - radius, centerY - radius)
    val arcSize = Size(radius * 2f, radius * 2f)
    val gap = size.gapDegrees

    // Segments go from 180 degrees (left) to 0 degrees (right), drawn clockwise in screen coordinates.
    val segments = listOf(180f to 120f, 120f to 60f, 60f to 0f)
    segments.forEachIndexed { index, (from, to) ->
        val start = if (index == 0) from else from - gap
        val end = if (index == segments.lastIndex) to else to + gap
        val isActive = index == activeLevel.ordinal
        drawArc(
            color = segmentColors[index].copy(alpha = if (dimInactiveSegments && !isActive) 0.28f else 1f),
            startAngle = -start,
            sweepAngle = start - end,
            useCenter = false,
            topLeft = arcTopLeft,
            size = arcSize,
            style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
        )
    }

    val angle = (180f - value * 1.8f) * PI.toFloat() / 180f
    val needleLength = radius - strokeWidth / 2f - size.needleInset.toPx()
    val needleWidth = size.needleWidth.toPx()
    val tip = Offset(centerX + needleLength * cos(angle), centerY - needleLength * sin(angle))
    drawLine(color = needleColor, start = Offset(centerX, centerY), end = tip, strokeWidth = needleWidth, cap = StrokeCap.Round)
    drawCircle(color = needleColor, radius = needleWidth * 1.6f, center = Offset(centerX, centerY))
}
