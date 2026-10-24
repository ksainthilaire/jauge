package io.jauge.core.designsystem.component

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import io.jauge.core.designsystem.theme.JaugePalette
import kotlin.math.PI
import kotlin.math.atan2

/**
 * The Jauge logo (`logo.svg`): an ink rounded square with the three segment gauge and its needle. Decorative: give
 * the surrounding text the meaning.
 *
 * @param modifier modifier of the logo; give it a square size.
 */
@Composable
fun JaugeLogo(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val unit = size.minDimension / LOGO_VIEW_BOX
        fun point(x: Float, y: Float) = Offset(x * unit, y * unit)
        drawRoundRect(
            color = JaugePalette.Ink,
            size = Size(LOGO_VIEW_BOX * unit, LOGO_VIEW_BOX * unit),
            cornerRadius = CornerRadius(30f * unit),
        )
        val center = point(60f, 76f)
        val radius = 34f * unit
        val stroke = Stroke(width = 11f * unit, cap = StrokeCap.Round)
        fun angleOf(x: Float, y: Float): Float = degrees(atan2(y * unit - center.y, x * unit - center.x))
        val segments = listOf(
            Triple(JaugePalette.Mint, angleOf(26f, 76f), angleOf(36.5f, 51f)),
            Triple(JaugePalette.Amber, angleOf(44f, 44f), angleOf(72f, 40f)),
            Triple(JaugePalette.Coral, angleOf(81f, 45f), angleOf(94f, 76f)),
        )
        segments.forEach { (color, start, end) ->
            var sweep = end - start
            if (sweep < 0f) sweep += 360f
            drawArc(
                color = color,
                startAngle = start,
                sweepAngle = sweep,
                useCenter = false,
                topLeft = Offset(center.x - radius, center.y - radius),
                size = Size(radius * 2, radius * 2),
                style = stroke,
            )
        }
        drawLine(JaugePalette.Paper, center, point(84f, 56f), strokeWidth = 7f * unit, cap = StrokeCap.Round)
        drawCircle(JaugePalette.Paper, radius = 9f * unit, center = center)
        drawCircle(JaugePalette.Ink, radius = 3.5f * unit, center = center)
    }
}

private const val LOGO_VIEW_BOX = 120f

private fun degrees(radians: Float): Float = (radians * 180.0 / PI).toFloat()
