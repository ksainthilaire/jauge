package io.jauge.core.designsystem.component

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * Where the half circle of a gauge sits inside its drawing area.
 *
 * @property center center of the circle (the middle of the flat side of the half circle).
 * @property radius radius of the middle of the arc stroke.
 * @property extent how far the drawing goes beyond [radius] on each side (half the stroke, or the cursor radius).
 */
data class GaugeFrame(val center: Offset, val radius: Float, val extent: Float)

/**
 * Pure geometry and colors of the gauge, shared by every gauge of the app and tested on their own. A score goes from 0
 * (left end of the half circle) to 100 (right end), through 50 at the top.
 */
object GaugeGeometry {

    /** Lowest score. */
    const val MINIMUM_SCORE = 0f

    /** Highest score. */
    const val MAXIMUM_SCORE = 100f

    /**
     * Share of the arc covered by a score, between 0 and 1 (values outside 0 to 100 are clamped).
     *
     * @param score the score.
     */
    fun fractionOf(score: Float): Float = (score.coerceIn(MINIMUM_SCORE, MAXIMUM_SCORE) - MINIMUM_SCORE) / (MAXIMUM_SCORE - MINIMUM_SCORE)

    /**
     * Mathematical angle of a score in degrees, counter clockwise from the right: 180 at 0, 90 at 50, 0 at 100.
     *
     * @param score the score.
     */
    fun angleDegrees(score: Float): Float = 180f - fractionOf(score) * 180f

    /**
     * Angle of a score for `drawArc`, in degrees, clockwise from the right as on screen: -180 at 0, -90 at 50, 0 at 100.
     *
     * @param score the score.
     */
    fun screenAngleDegrees(score: Float): Float = -angleDegrees(score)

    /**
     * Point of the arc at a score.
     *
     * @param center center of the circle.
     * @param radius distance from the center.
     * @param score the score.
     */
    fun pointAt(center: Offset, radius: Float, score: Float): Offset {
        val radians = angleDegrees(score) * PI.toFloat() / 180f
        return Offset(center.x + radius * cos(radians), center.y - radius * sin(radians))
    }

    /**
     * Height of the drawing for a given width, so that the half circle, its stroke and its cursor fit with [margin]
     * around them.
     *
     * @param width available width.
     * @param extent how far the drawing goes beyond the radius (half the stroke or the cursor radius).
     * @param margin free space kept on every side.
     */
    fun heightFor(width: Float, extent: Float, margin: Float): Float {
        val radius = max(0f, width / 2f - extent - margin)
        return radius + 2f * extent + 2f * margin
    }

    /**
     * Largest half circle that fits inside a [width] x [height] area with [margin] on every side, centered horizontally
     * and vertically: the arc, its round ends and its cursor are never cut, whatever the size given by the layout.
     *
     * @param width width of the drawing area.
     * @param height height of the drawing area.
     * @param extent how far the drawing goes beyond the radius (half the stroke or the cursor radius).
     * @param margin free space kept on every side.
     */
    fun fit(width: Float, height: Float, extent: Float, margin: Float): GaugeFrame {
        val radius = max(0f, min(width / 2f - extent - margin, height - 2f * extent - 2f * margin))
        val usedHeight = radius + 2f * extent
        val top = (height - usedHeight) / 2f
        return GaugeFrame(center = Offset(width / 2f, top + extent + radius), radius = radius, extent = extent)
    }

    /**
     * Factor applied to the stroke and the cursor when the layout squeezes the gauge: the radius stays at least
     * [MINIMUM_RADIUS_TO_EXTENT] times the extent, so that a short area shows a thinner arc rather than a crowded one.
     * 1 when the gauge has the room it needs.
     *
     * @param width width of the drawing area.
     * @param height height of the drawing area.
     * @param extent how far the drawing goes beyond the radius at full size.
     * @param margin free space kept on every side.
     */
    fun thicknessScale(width: Float, height: Float, extent: Float, margin: Float): Float {
        if (extent <= 0f) return 1f
        // Largest radius when the extent is e: min(w / 2 - e - m, h - 2e - m * 2). Solve radius = k * e for e.
        val fromWidth = (width / 2f - margin) / (MINIMUM_RADIUS_TO_EXTENT + 1f)
        val fromHeight = (height - 2f * margin) / (MINIMUM_RADIUS_TO_EXTENT + 2f)
        return (min(fromWidth, fromHeight) / extent).coerceIn(0f, 1f)
    }

    /**
     * Free area inside the half circle where the score is written: it touches neither the arc nor the cursor, at any
     * score. It stands on the flat side of the half circle and goes down to the bottom of the round ends of the arc,
     * which are far on the sides.
     *
     * @param frame the frame of the gauge.
     * @param gap space kept between the inner edge of the arc and the area.
     */
    fun scoreArea(frame: GaugeFrame, gap: Float): Rect {
        val innerRadius = max(0f, frame.radius - frame.extent - gap)
        // A rectangle of half width 0.7 r rising 0.64 r above the flat side stays inside the inner circle
        // (0.7 x 0.7 + 0.64 x 0.64 < 1), and the cursor only rides the arc.
        val halfWidth = innerRadius * SCORE_AREA_HALF_WIDTH
        val bottom = frame.center.y + frame.extent
        val height = innerRadius * SCORE_AREA_HEIGHT + frame.extent
        return Rect(frame.center.x - halfWidth, bottom - height, frame.center.x + halfWidth, bottom)
    }

    /**
     * Color of the arc at a score: mint, then amber, then coral, with soft transitions centered on the level
     * thresholds (33/34 and 66/67), so that the color under the cursor always agrees with the level word.
     *
     * @param score the score.
     * @param calm color of the calm level.
     * @param disputed color of the disputed level.
     * @param crowded color of the crowded level.
     */
    fun colorAt(score: Float, calm: Color, disputed: Color, crowded: Color): Color {
        val value = score.coerceIn(MINIMUM_SCORE, MAXIMUM_SCORE)
        return when {
            value <= CALM_UNTIL -> calm
            value < DISPUTED_FROM -> lerp(calm, disputed, (value - CALM_UNTIL) / (DISPUTED_FROM - CALM_UNTIL))
            value <= DISPUTED_UNTIL -> disputed
            value < CROWDED_FROM -> lerp(disputed, crowded, (value - DISPUTED_UNTIL) / (CROWDED_FROM - DISPUTED_UNTIL))
            else -> crowded
        }
    }

    /**
     * Color stops of a sweep gradient centered on the gauge, drawing [colorAt] along the half circle. The sweep starts
     * on the right and turns clockwise: the arc runs from 0.5 (score 0) to 1.0 (score 100). The round ends of the arc
     * spill a little past both; the stops keep them in their own color.
     *
     * @param calm color of the calm level.
     * @param disputed color of the disputed level.
     * @param crowded color of the crowded level.
     */
    fun sweepStops(calm: Color, disputed: Color, crowded: Color): Array<Pair<Float, Color>> = arrayOf(
        0f to crowded,
        0.25f to crowded,
        0.2501f to calm,
        sweepFraction(CALM_UNTIL) to calm,
        sweepFraction(DISPUTED_FROM) to disputed,
        sweepFraction(DISPUTED_UNTIL) to disputed,
        sweepFraction(CROWDED_FROM) to crowded,
        1f to crowded,
    )

    private fun sweepFraction(score: Float): Float = 0.5f + fractionOf(score) / 2f

    /** Smallest ratio between the radius of the arc and its extent before the stroke and the cursor shrink. */
    const val MINIMUM_RADIUS_TO_EXTENT = 5f

    private const val CALM_UNTIL = 22f
    private const val DISPUTED_FROM = 44f
    private const val DISPUTED_UNTIL = 56f
    private const val CROWDED_FROM = 78f
    private const val SCORE_AREA_HALF_WIDTH = 0.7f
    private const val SCORE_AREA_HEIGHT = 0.64f
}
