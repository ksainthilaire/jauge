package io.jauge.feature.account.email

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import io.jauge.core.designsystem.component.GaugeFrame
import io.jauge.core.designsystem.component.drawJaugeArc
import io.jauge.core.designsystem.theme.JaugePalette

/**
 * Brand illustration of the "Regarde ta boîte mail" step: an open envelope with a letter that carries a small calm
 * gauge, a mint glow and a check badge. Drawn in Compose with the brand palette, decorative (no description).
 *
 * @param modifier modifier of the illustration; give it a width and a height (about 2:1).
 */
@Composable
fun MailIllustration(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val unit = size.height / VIEW_HEIGHT
        val centerX = size.width / 2f
        fun x(value: Float) = centerX + value * unit
        fun y(value: Float) = value * unit

        // Mint glow behind the envelope.
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(JaugePalette.Mint.copy(alpha = 0.26f), Color.Transparent),
                center = Offset(centerX, y(96f)),
                radius = 104f * unit,
            ),
            radius = 104f * unit,
            center = Offset(centerX, y(96f)),
        )

        val envelopeLeft = x(-84f)
        val envelopeRight = x(84f)
        val envelopeTop = y(70f)
        val envelopeBottom = y(166f)
        val corner = CornerRadius(14f * unit)

        // Open flap, behind the letter.
        val flap = Path().apply {
            moveTo(envelopeLeft + 4f * unit, envelopeTop + 6f * unit)
            lineTo(centerX, y(26f))
            lineTo(envelopeRight - 4f * unit, envelopeTop + 6f * unit)
            close()
        }
        drawPath(flap, JaugePalette.Ink3)
        drawPath(flap, JaugePalette.Ink2, style = Stroke(width = 2f * unit, join = StrokeJoin.Round))

        // Back of the envelope.
        drawRoundRect(
            color = JaugePalette.Ink3,
            topLeft = Offset(envelopeLeft, envelopeTop),
            size = Size(envelopeRight - envelopeLeft, envelopeBottom - envelopeTop),
            cornerRadius = corner,
        )

        // The letter, with a small gauge pointing to "calme".
        val letterLeft = x(-60f)
        val letterTop = y(14f)
        drawRoundRect(
            color = JaugePalette.Paper,
            topLeft = Offset(letterLeft, letterTop),
            size = Size(120f * unit, 120f * unit),
            cornerRadius = CornerRadius(12f * unit),
        )
        drawLetterGauge(center = Offset(centerX, y(66f)), radius = 30f * unit, unit = unit)
        drawRoundRect(
            color = JaugePalette.Line,
            topLeft = Offset(x(-38f), y(80f)),
            size = Size(76f * unit, 6f * unit),
            cornerRadius = CornerRadius(3f * unit),
        )

        // Front pocket of the envelope, over the bottom of the letter.
        val pocket = Path().apply {
            moveTo(envelopeLeft, envelopeTop + 14f * unit)
            lineTo(centerX, y(124f))
            lineTo(envelopeRight, envelopeTop + 14f * unit)
            lineTo(envelopeRight, envelopeBottom - 14f * unit)
            quadraticTo(envelopeRight, envelopeBottom, envelopeRight - 14f * unit, envelopeBottom)
            lineTo(envelopeLeft + 14f * unit, envelopeBottom)
            quadraticTo(envelopeLeft, envelopeBottom, envelopeLeft, envelopeBottom - 14f * unit)
            close()
        }
        drawPath(pocket, JaugePalette.Ink2)
        drawPath(pocket, JaugePalette.Ink3.copy(alpha = 0.9f), style = Stroke(width = 1.5f * unit, join = StrokeJoin.Round))
        val fold = Path().apply {
            moveTo(envelopeLeft + 10f * unit, envelopeBottom - 8f * unit)
            lineTo(centerX, y(132f))
            lineTo(envelopeRight - 10f * unit, envelopeBottom - 8f * unit)
        }
        drawPath(fold, JaugePalette.Ink3, style = Stroke(width = 2f * unit, cap = StrokeCap.Round, join = StrokeJoin.Round))

        // Check badge: the link is on its way.
        val badgeCenter = Offset(x(76f), y(72f))
        drawCircle(JaugePalette.Ink, radius = 19f * unit, center = badgeCenter)
        drawCircle(JaugePalette.Mint, radius = 15f * unit, center = badgeCenter)
        val check = Path().apply {
            moveTo(badgeCenter.x - 6.5f * unit, badgeCenter.y + 0.5f * unit)
            lineTo(badgeCenter.x - 1.5f * unit, badgeCenter.y + 5.5f * unit)
            lineTo(badgeCenter.x + 7f * unit, badgeCenter.y - 5f * unit)
        }
        drawPath(check, JaugePalette.Ink, style = Stroke(width = 3.2f * unit, cap = StrokeCap.Round, join = StrokeJoin.Round))

        // Small sparks in the gauge colors.
        drawCircle(JaugePalette.Amber, radius = 3.5f * unit, center = Offset(x(-96f), y(40f)))
        drawCircle(JaugePalette.Coral, radius = 2.5f * unit, center = Offset(x(-78f), y(18f)))
        drawCircle(JaugePalette.Mint, radius = 3f * unit, center = Offset(x(100f), y(30f)))
    }
}

/** The brand gauge of the letter, cursor on the calm side. */
private fun DrawScope.drawLetterGauge(center: Offset, radius: Float, unit: Float) {
    drawJaugeArc(
        frame = GaugeFrame(center = center, radius = radius, extent = 6f * unit),
        score = 22f,
        strokeWidth = 7f * unit,
        cursorRadius = 6f * unit,
        calm = JaugePalette.Mint,
        disputed = JaugePalette.Amber,
        crowded = JaugePalette.Coral,
        contentColor = JaugePalette.Ink,
        surfaceColor = JaugePalette.Paper,
        trackColor = JaugePalette.Ink.copy(alpha = 0.08f),
        showTicks = false,
    )
}

private const val VIEW_HEIGHT = 176f
