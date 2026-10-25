package io.jauge.core.designsystem.component

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GaugeGeometryTest {

    private val calm = Color(0xFF3CCB9F)
    private val disputed = Color(0xFFFFB23F)
    private val crowded = Color(0xFFFF5B3A)

    private fun assertClose(expected: Float, actual: Float, tolerance: Float = 0.01f) =
        assertTrue(abs(expected - actual) <= tolerance, "expected $expected, was $actual")

    @Test
    fun angleGoesFromLeftToRightThroughTheTop() {
        assertClose(180f, GaugeGeometry.angleDegrees(0f))
        assertClose(90f, GaugeGeometry.angleDegrees(50f))
        assertClose(0f, GaugeGeometry.angleDegrees(100f))
        assertClose(180f - 78f * 1.8f, GaugeGeometry.angleDegrees(78f))
        assertClose(-90f, GaugeGeometry.screenAngleDegrees(50f))
    }

    @Test
    fun scoresOutsideTheScaleAreClamped() {
        assertClose(180f, GaugeGeometry.angleDegrees(-20f))
        assertClose(0f, GaugeGeometry.angleDegrees(140f))
        assertClose(0f, GaugeGeometry.fractionOf(-1f))
        assertClose(1f, GaugeGeometry.fractionOf(101f))
    }

    @Test
    fun pointsAtTheBoundsSitOnTheFlatSide() {
        val center = Offset(100f, 100f)
        val left = GaugeGeometry.pointAt(center, 50f, 0f)
        val top = GaugeGeometry.pointAt(center, 50f, 50f)
        val right = GaugeGeometry.pointAt(center, 50f, 100f)
        assertClose(50f, left.x); assertClose(100f, left.y)
        assertClose(100f, top.x); assertClose(50f, top.y)
        assertClose(150f, right.x); assertClose(100f, right.y)
    }

    @Test
    fun arcAlwaysFitsWithItsMargin() {
        // Wide and short, narrow and tall, the phone verdict card: the drawing never leaves the area.
        listOf(
            listOf(800f, 150f, 15f, 6f),
            listOf(120f, 400f, 15f, 6f),
            listOf(330f, 190f, 15f, 6f),
            listOf(52f, 31f, 4f, 1f),
        ).forEach { (width, height, extent, margin) ->
            val frame = GaugeGeometry.fit(width, height, extent, margin)
            assertTrue(frame.center.x - frame.radius - extent >= margin - 0.01f, "left cut in $width x $height")
            assertTrue(frame.center.x + frame.radius + extent <= width - margin + 0.01f, "right cut in $width x $height")
            assertTrue(frame.center.y - frame.radius - extent >= margin - 0.01f, "top cut in $width x $height")
            assertTrue(frame.center.y + extent <= height - margin + 0.01f, "bottom cut in $width x $height")
        }
    }

    @Test
    fun naturalHeightUsesTheWholeWidth() {
        val height = GaugeGeometry.heightFor(width = 300f, extent = 15f, margin = 6f)
        val frame = GaugeGeometry.fit(300f, height, 15f, 6f)
        assertClose(300f / 2f - 15f - 6f, frame.radius)
    }

    @Test
    fun scoreAreaStaysClearOfTheArcAndTheCursor() {
        val frame = GaugeGeometry.fit(330f, GaugeGeometry.heightFor(330f, 15f, 6f), 15f, 6f)
        val area = GaugeGeometry.scoreArea(frame, gap = 4f)
        val innerRadius = frame.radius - frame.extent
        // Every corner above the flat side is inside the inner edge of the arc.
        listOf(Offset(area.left, area.top), Offset(area.right, area.top)).forEach { corner ->
            assertTrue((corner - frame.center).getDistance() < innerRadius, "corner $corner touches the arc")
        }
        // At 0 and 100 the cursor sits on the flat side, left and right of the area.
        (0..100 step 5).forEach { score ->
            val cursor = GaugeGeometry.pointAt(frame.center, frame.radius, score.toFloat())
            val closestX = cursor.x.coerceIn(area.left, area.right)
            val closestY = cursor.y.coerceIn(area.top, area.bottom)
            assertTrue((cursor - Offset(closestX, closestY)).getDistance() > frame.extent, "cursor at $score covers the score")
        }
    }

    @Test
    fun colorsFollowTheLevels() {
        assertEquals(calm, GaugeGeometry.colorAt(0f, calm, disputed, crowded))
        assertEquals(calm, GaugeGeometry.colorAt(19f, calm, disputed, crowded))
        assertEquals(disputed, GaugeGeometry.colorAt(50f, calm, disputed, crowded))
        assertEquals(crowded, GaugeGeometry.colorAt(78f, calm, disputed, crowded))
        assertEquals(crowded, GaugeGeometry.colorAt(100f, calm, disputed, crowded))
        assertEquals(crowded, GaugeGeometry.colorAt(130f, calm, disputed, crowded))
        assertEquals(calm, GaugeGeometry.colorAt(-4f, calm, disputed, crowded))
    }

    @Test
    fun levelThresholdsFallInTheMiddleOfTheColorTransitions() {
        val atCalmEdge = GaugeGeometry.colorAt(33f, calm, disputed, crowded)
        assertTrue(atCalmEdge != calm && atCalmEdge != disputed)
        val atCrowdedEdge = GaugeGeometry.colorAt(67f, calm, disputed, crowded)
        assertTrue(atCrowdedEdge != disputed && atCrowdedEdge != crowded)
    }

    @Test
    fun sweepStopsAreOrderedAndCoverTheArc() {
        val stops = GaugeGeometry.sweepStops(calm, disputed, crowded)
        assertEquals(0f, stops.first().first)
        assertEquals(1f, stops.last().first)
        stops.toList().zipWithNext().forEach { (a, b) -> assertTrue(a.first <= b.first) }
        // Score 0 starts at half the sweep in mint, score 100 ends the sweep in coral.
        assertEquals(calm, stops.first { it.first >= 0.5f }.second)
        assertEquals(crowded, stops.last().second)
    }

    @Test
    fun squeezedGaugesGetAThinnerArcNotACrowdedOne() {
        assertClose(1f, GaugeGeometry.thicknessScale(330f, 200f, 15f, 6f))
        val scale = GaugeGeometry.thicknessScale(330f, 60f, 15f, 6f)
        assertTrue(scale < 1f)
        val frame = GaugeGeometry.fit(330f, 60f, 15f * scale, 6f)
        assertTrue(frame.radius >= GaugeGeometry.MINIMUM_RADIUS_TO_EXTENT * frame.extent - 0.01f)
    }
}
