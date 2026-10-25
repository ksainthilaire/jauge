package io.jauge.core.designsystem.component

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.constrainHeight
import androidx.compose.ui.unit.constrainWidth
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import io.jauge.core.designsystem.motion.rememberReducedMotion
import io.jauge.core.designsystem.resources.Res
import io.jauge.core.designsystem.resources.gauge_description
import io.jauge.core.designsystem.resources.gauge_out_of
import io.jauge.core.designsystem.theme.JaugeTheme
import org.jetbrains.compose.resources.stringResource
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Size presets of [JaugeGauge]. Every preset keeps the whole arc, its round ends and its cursor inside the drawing
 * with [margin] around them, whatever size the layout gives.
 *
 * @property strokeWidth thickness of the arc.
 * @property cursorRadius radius of the cursor that rides the arc.
 * @property margin free space around the drawing.
 * @property defaultWidth width used when the layout leaves the width open.
 * @property showTicks true to draw the small graduation dots inside the arc (every 10 points).
 * @property maximumScoreSize largest size of the score written inside the arc (it shrinks to fit).
 */
enum class GaugeSize(
    val strokeWidth: Dp,
    val cursorRadius: Dp,
    val margin: Dp,
    val defaultWidth: Dp,
    val showTicks: Boolean,
    val maximumScoreSize: TextUnit,
) {
    /** Hero of the welcome screen (mockup 01). */
    HERO(strokeWidth = 24.dp, cursorRadius = 20.dp, margin = 8.dp, defaultWidth = 300.dp, showTicks = true, maximumScoreSize = 72.sp),

    /** Large gauge of the verdict card (mockup 04). */
    LARGE(strokeWidth = 16.dp, cursorRadius = 15.dp, margin = 6.dp, defaultWidth = 300.dp, showTicks = true, maximumScoreSize = 64.sp),

    /** Medium gauge of the share card and of the brand card of the account screen. */
    MEDIUM(strokeWidth = 11.dp, cursorRadius = 10.dp, margin = 3.dp, defaultWidth = 150.dp, showTicks = false, maximumScoreSize = 34.sp),

    /** Mini gauge of list rows: the score stays readable inside the arc. */
    MINI(strokeWidth = 4.5.dp, cursorRadius = 4.dp, margin = 1.dp, defaultWidth = 52.dp, showTicks = false, maximumScoreSize = 13.sp),
}

/**
 * The competition gauge, heart of the Jauge brand: a half circle colored from mint (calm) through amber (disputed) to
 * coral (crowded), filled up to the score, with a round cursor that rides the arc and the score written in the free
 * middle. The cursor never passes over the score, and the arc is never cut: the drawing fits the space it gets.
 *
 * When it appears, the arc sweeps from 0 to the score while the number counts up; no motion when the person reduces
 * animations or when [animate] is false.
 *
 * @param score competition score between 0 and 100 (clamped).
 * @param modifier modifier of the gauge. Without a height, the gauge takes the height its width needs.
 * @param size size preset.
 * @param contentColor color of the score, the cursor center and the graduations (the text color of the card).
 * @param surfaceColor color of the card behind the gauge, used inside the cursor.
 * @param showScore false to draw the arc alone (small brand marks, illustrations).
 * @param showOutOf true to write "/100" after the score.
 * @param animate false to draw the final state at once (screenshots, previews, lists).
 */
@Composable
fun JaugeGauge(
    score: Int,
    modifier: Modifier = Modifier,
    size: GaugeSize = GaugeSize.LARGE,
    contentColor: Color = JaugeTheme.colors.textPrimary,
    surfaceColor: Color = JaugeTheme.colors.surface,
    showScore: Boolean = true,
    showOutOf: Boolean = false,
    animate: Boolean = true,
) {
    val preset = size
    val clampedScore = score.coerceIn(0, 100)
    val level = GaugeLevel.fromScore(clampedScore)
    val reducedMotion = rememberReducedMotion()
    val shouldAnimate = animate && !reducedMotion
    val animatedScore = remember { Animatable(if (shouldAnimate) 0f else clampedScore.toFloat()) }
    LaunchedEffect(clampedScore, shouldAnimate) {
        if (shouldAnimate) {
            animatedScore.animateTo(clampedScore.toFloat(), tween(durationMillis = SWEEP_DURATION_MILLIS, easing = SweepEasing))
        } else {
            animatedScore.snapTo(clampedScore.toFloat())
        }
    }
    val colors = JaugeTheme.colors
    val calm = colors.calm.accent
    val disputed = colors.disputed.accent
    val crowded = colors.crowded.accent
    val description = stringResource(Res.string.gauge_description, clampedScore, stringResource(level.label))
    val outOf = stringResource(Res.string.gauge_out_of)
    val density = LocalDensity.current
    val presetExtent = with(density) { max(preset.cursorRadius.toPx(), preset.strokeWidth.toPx() / 2f) }
    val margin = with(density) { preset.margin.toPx() }
    val numberStyle = JaugeTheme.typography.numberLarge

    BoxWithConstraints(
        modifier = modifier
            .gaugeLayout(preset, presetExtent, margin)
            .clearAndSetSemantics { contentDescription = description },
    ) {
        // In an area squeezed by the layout, the stroke and the cursor shrink with the arc instead of crowding it.
        val scale = GaugeGeometry.thicknessScale(constraints.maxWidth.toFloat(), constraints.maxHeight.toFloat(), presetExtent, margin)
        val extent = presetExtent * scale
        val frame = GaugeGeometry.fit(constraints.maxWidth.toFloat(), constraints.maxHeight.toFloat(), extent, margin)
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawJaugeArc(
                frame = frame,
                score = animatedScore.value,
                strokeWidth = preset.strokeWidth.toPx() * scale,
                cursorRadius = preset.cursorRadius.toPx() * scale,
                calm = calm,
                disputed = disputed,
                crowded = crowded,
                contentColor = contentColor,
                surfaceColor = surfaceColor,
                trackColor = contentColor.copy(alpha = TRACK_ALPHA),
                showTicks = preset.showTicks,
            )
        }
        if (showScore) {
            val gap = with(density) { (if (preset.showTicks) TICKS_INSET + 6.dp else preset.strokeWidth * 0.2f).toPx() }
            val area = GaugeGeometry.scoreArea(frame, gap)
            BasicText(
                text = buildAnnotatedString {
                    append(animatedScore.value.roundToInt().toString())
                    if (showOutOf) {
                        withStyle(SpanStyle(fontSize = 0.34.em, color = contentColor.copy(alpha = 0.55f), letterSpacing = 0.sp)) { append(outOf) }
                    }
                },
                style = numberStyle.copy(color = contentColor, lineHeight = 1.1.em, textAlign = TextAlign.Center),
                maxLines = 1,
                softWrap = false,
                autoSize = TextAutoSize.StepBased(minFontSize = 6.sp, maxFontSize = preset.maximumScoreSize, stepSize = 0.5.sp),
                modifier = Modifier
                    .offset { IntOffset(area.left.roundToInt(), area.top.roundToInt()) }
                    .size(with(density) { area.width.toDp() }, with(density) { area.height.toDp() })
                    .alignContentAtBottom(),
            )
        }
    }
}

/**
 * Draws the gauge arc of the brand inside [frame]: the track, the colored part from 0 to [score], the graduations and
 * the cursor. Shared by [JaugeGauge] and the illustrations that draw a gauge themselves.
 *
 * @param frame where the half circle sits, from [GaugeGeometry.fit].
 * @param score the score shown, between 0 and 100.
 * @param strokeWidth thickness of the arc.
 * @param cursorRadius radius of the cursor; 0 for no cursor.
 * @param calm color of the calm level.
 * @param disputed color of the disputed level.
 * @param crowded color of the crowded level.
 * @param contentColor color of the cursor center and the graduations.
 * @param surfaceColor color inside the cursor (the card behind the gauge).
 * @param trackColor color of the part of the arc above the score.
 * @param showTicks true to draw the graduation dots.
 */
fun DrawScope.drawJaugeArc(
    frame: GaugeFrame,
    score: Float,
    strokeWidth: Float,
    cursorRadius: Float,
    calm: Color,
    disputed: Color,
    crowded: Color,
    contentColor: Color,
    surfaceColor: Color,
    trackColor: Color,
    showTicks: Boolean,
) {
    if (frame.radius <= 0f) return
    val topLeft = Offset(frame.center.x - frame.radius, frame.center.y - frame.radius)
    val arcSize = Size(frame.radius * 2f, frame.radius * 2f)
    val sweep = Brush.sweepGradient(colorStops = GaugeGeometry.sweepStops(calm, disputed, crowded), center = frame.center)
    val style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
    // Track: the whole scale, faint, so that the levels still to come stay readable.
    drawArc(trackColor, -180f, 180f, useCenter = false, topLeft = topLeft, size = arcSize, style = style)
    drawArc(sweep, -180f, 180f, useCenter = false, topLeft = topLeft, size = arcSize, alpha = SCALE_ALPHA, style = style)
    if (showTicks) {
        val tickRadius = frame.radius - strokeWidth / 2f - TICKS_INSET.toPx()
        for (step in 0..10) {
            val major = step % 5 == 0
            drawCircle(
                color = contentColor.copy(alpha = if (major) 0.45f else 0.2f),
                radius = (if (major) 2.dp else 1.4.dp).toPx(),
                center = GaugeGeometry.pointAt(frame.center, tickRadius, step * 10f),
            )
        }
    }
    val fraction = GaugeGeometry.fractionOf(score)
    if (fraction > 0f) {
        drawArc(sweep, -180f, 180f * fraction, useCenter = false, topLeft = topLeft, size = arcSize, style = style)
    }
    if (cursorRadius > 0f) {
        val point = GaugeGeometry.pointAt(frame.center, frame.radius, score)
        val accent = GaugeGeometry.colorAt(score, calm, disputed, crowded)
        drawCircle(accent.copy(alpha = 0.22f), radius = cursorRadius * 1.4f, center = point)
        drawCircle(surfaceColor, radius = cursorRadius, center = point)
        drawCircle(accent, radius = cursorRadius * 0.82f, center = point, style = Stroke(cursorRadius * 0.36f))
        drawCircle(contentColor, radius = cursorRadius * 0.26f, center = point)
    }
}

/**
 * Takes the default width of the preset (or the width forced by the caller, `fillMaxWidth` or `width`) and the height
 * this width needs, within the constraints. A fixed height given by the caller is kept: the drawing then fits inside it.
 */
private fun Modifier.gaugeLayout(preset: GaugeSize, extent: Float, margin: Float): Modifier = layout { measurable, constraints ->
    val width = constraints.constrainWidth(preset.defaultWidth.roundToPx())
    val height = constraints.constrainHeight(GaugeGeometry.heightFor(width.toFloat(), extent, margin).roundToInt())
    val placeable = measurable.measure(Constraints.fixed(width, height))
    layout(width, height) { placeable.place(0, 0) }
}

/** Lays the text at the bottom center of the area given to it: the digits stand on the flat side of the half circle. */
private fun Modifier.alignContentAtBottom(): Modifier = layout { measurable, constraints ->
    val placeable = measurable.measure(constraints.copy(minWidth = 0, minHeight = 0))
    layout(constraints.maxWidth, constraints.maxHeight) {
        placeable.place((constraints.maxWidth - placeable.width) / 2, constraints.maxHeight - placeable.height)
    }
}

/** Duration of the sweep from 0 to the score. */
private const val SWEEP_DURATION_MILLIS = 1100

/** Calm deceleration of the sweep: the cursor settles on its value. */
private val SweepEasing = CubicBezierEasing(0.22f, 1f, 0.36f, 1f)

/** Opacity of the neutral track. */
private const val TRACK_ALPHA = 0.08f

/** Opacity of the colored scale drawn over the track. */
private const val SCALE_ALPHA = 0.22f

/** Distance between the inner edge of the arc and the graduation dots. */
private val TICKS_INSET = 9.dp
