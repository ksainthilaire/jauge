package io.jauge.core.designsystem.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.github.takahirom.roborazzi.captureRoboImage
import io.jauge.core.designsystem.theme.JaugeTheme
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Three design directions for the gauge, drawn side by side (scores 19, 50, 78) in light and dark themes, into
 * `docs/design/gauge-pistes`. Kept as the record of the choice explained in `docs/design/gauge.md`.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w393dp-h900dp-xhdpi")
class GaugeDirectionsScreenshotTest {

    private enum class Direction { CURSOR, SEGMENTS_TICK, GRADUATIONS }

    @Test
    fun directionsLight() = directions(darkTheme = false)

    @Test
    fun directionsDark() = directions(darkTheme = true)

    private fun directions(darkTheme: Boolean) {
        val directory = System.getProperty("jauge.screenshots.directory") + "/../design/gauge-pistes"
        Direction.entries.forEach { direction ->
            val name = "${direction.ordinal + 1}-${direction.name.lowercase()}-${if (darkTheme) "sombre" else "clair"}"
            captureRoboImage("$directory/$name.png") {
                JaugeTheme(darkTheme = darkTheme) {
                    Column(
                        modifier = Modifier.background(JaugeTheme.colors.background).padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Prototype(direction, score = 78, large = true)
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            listOf(0, 19, 50, 100).forEach { Prototype(direction, score = it, large = false) }
                        }
                    }
                }
            }
        }
    }

    @Composable
    private fun Prototype(direction: Direction, score: Int, large: Boolean) {
        val colors = JaugeTheme.colors
        val card = if (large) Modifier.fillMaxWidth() else Modifier.width(80.dp)
        Box(
            modifier = card.background(colors.emphasisSurface, RoundedCornerShape(if (large) 24.dp else 14.dp)).padding(if (large) 16.dp else 6.dp),
            contentAlignment = Alignment.BottomCenter,
        ) {
            Canvas(modifier = Modifier.fillMaxWidth().height(if (large) 190.dp else 40.dp)) {
                drawPrototype(direction, score.toFloat(), large, colors.calm.accent, colors.disputed.accent, colors.crowded.accent, colors.emphasisSurface, colors.onEmphasisSurface)
            }
            Text(
                text = buildAnnotatedString {
                    append(score.toString())
                    if (large) withStyle(SpanStyle(fontSize = 18.sp, color = colors.onEmphasisSurfaceSecondary)) { append(" /100") }
                },
                style = JaugeTheme.typography.numberLarge.copy(fontSize = if (large) 56.sp else 12.sp, lineHeight = if (large) 60.sp else 14.sp),
                color = colors.onEmphasisSurface,
                modifier = Modifier.padding(bottom = if (large) 22.dp else 2.dp),
            )
        }
    }

    private fun DrawScope.drawPrototype(
        direction: Direction,
        score: Float,
        large: Boolean,
        calm: Color,
        disputed: Color,
        crowded: Color,
        surface: Color,
        ink: Color,
    ) {
        val stroke = (if (large) 16.dp else 5.dp).toPx()
        val knob = (if (large) 15.dp else 5.dp).toPx()
        val margin = (if (large) 6.dp else 1.dp).toPx()
        val extent = when (direction) {
            Direction.CURSOR -> maxOf(knob, stroke / 2f)
            Direction.SEGMENTS_TICK -> stroke / 2f + (if (large) 7.dp else 2.dp).toPx()
            Direction.GRADUATIONS -> (if (large) 14.dp else 4.dp).toPx()
        }
        val frame = GaugeGeometry.fit(size.width, size.height, extent, margin)
        val topLeft = Offset(frame.center.x - frame.radius, frame.center.y - frame.radius)
        val arcSize = Size(frame.radius * 2f, frame.radius * 2f)
        val sweep = Brush.sweepGradient(colorStops = GaugeGeometry.sweepStops(calm, disputed, crowded), center = frame.center)
        val accent = GaugeGeometry.colorAt(score, calm, disputed, crowded)
        when (direction) {
            Direction.CURSOR -> {
                drawArc(sweep, -180f, 180f, false, topLeft, arcSize, alpha = 0.22f, style = Stroke(stroke, cap = StrokeCap.Round))
                if (large) {
                    for (step in 0..10) {
                        val inner = GaugeGeometry.pointAt(frame.center, frame.radius - stroke / 2f - 10.dp.toPx(), step * 10f)
                        drawCircle(ink.copy(alpha = if (step % 5 == 0) 0.5f else 0.22f), radius = if (step % 5 == 0) 2.dp.toPx() else 1.4.dp.toPx(), center = inner)
                    }
                }
                drawArc(sweep, -180f, 180f * GaugeGeometry.fractionOf(score), false, topLeft, arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
                val point = GaugeGeometry.pointAt(frame.center, frame.radius, score)
                drawCircle(surface, radius = knob, center = point)
                drawCircle(accent, radius = knob, center = point, style = Stroke(knob * 0.36f))
                drawCircle(ink, radius = knob * 0.28f, center = point)
            }
            Direction.SEGMENTS_TICK -> {
                val gap = if (large) 4f else 7f
                val level = GaugeLevel.fromScore(score.toInt()).ordinal
                listOf(Triple(calm, 180f, 120f), Triple(disputed, 120f, 60f), Triple(crowded, 60f, 0f)).forEachIndexed { index, (color, from, to) ->
                    val start = if (index == 0) from else from - gap
                    val end = if (index == 2) to else to + gap
                    drawArc(color.copy(alpha = if (index == level) 1f else 0.25f), -start, start - end, false, topLeft, arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
                }
                val outer = GaugeGeometry.pointAt(frame.center, frame.radius + extent, score)
                val inner = GaugeGeometry.pointAt(frame.center, frame.radius - extent - (if (large) 8.dp else 2.dp).toPx(), score)
                drawLine(surface, outer, inner, strokeWidth = (if (large) 9.dp else 3.dp).toPx(), cap = StrokeCap.Round)
                drawLine(ink, outer, inner, strokeWidth = (if (large) 4.dp else 1.6.dp).toPx(), cap = StrokeCap.Round)
            }
            Direction.GRADUATIONS -> {
                val count = if (large) 41 else 15
                for (index in 0 until count) {
                    val value = index * 100f / (count - 1)
                    val isMarker = kotlin.math.abs(value - score) <= 50f / (count - 1)
                    val reached = value <= score
                    val length = if (isMarker) extent * 2f else extent * 1.1f
                    val start = GaugeGeometry.pointAt(frame.center, frame.radius - length / 2f, value)
                    val end = GaugeGeometry.pointAt(frame.center, frame.radius + length / 2f, value)
                    val color = if (reached || isMarker) GaugeGeometry.colorAt(value, calm, disputed, crowded) else ink.copy(alpha = 0.14f)
                    drawLine(color, start, end, strokeWidth = (if (large) (if (isMarker) 6.dp else 4.dp) else 1.6.dp).toPx(), cap = StrokeCap.Round)
                }
            }
        }
    }
}
