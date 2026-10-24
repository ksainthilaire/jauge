package io.jauge.feature.verdict.details

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.jauge.core.designsystem.theme.JaugeTheme
import io.jauge.core.domain.model.SalaryInsight
import io.jauge.core.domain.model.TrendPoint

/**
 * Position of a salary on the scale of an insight, between 0 and 1.
 *
 * @param thousands the salary in thousands of euros.
 */
fun SalaryInsight.fractionOf(thousands: Number): Float {
    val span = (scaleMaximumThousands - scaleMinimumThousands).toFloat()
    if (span <= 0f) return 0.5f
    return ((thousands.toFloat() - scaleMinimumThousands) / span).coerceIn(0f, 1f)
}

/**
 * Salary scale of mockup 05: the whole scale, the middle half of the offers, the range of this offer and the median.
 *
 * @param insight the salary insight.
 * @param modifier modifier of the chart.
 */
@Composable
fun SalaryScaleBar(insight: SalaryInsight, modifier: Modifier = Modifier) {
    val colors = JaugeTheme.colors
    Canvas(modifier = modifier.fillMaxWidth().height(44.dp)) {
        val centerY = size.height / 2f
        val trackHeight = 12.dp.toPx()
        fun x(thousands: Number) = insight.fractionOf(thousands) * size.width
        roundedBar(0f, size.width, centerY, trackHeight, colors.backgroundAlternative)
        roundedBar(x(insight.middleHalf.minimumThousands), x(insight.middleHalf.maximumThousands), centerY, trackHeight, colors.textTertiary)
        insight.offerRange?.let { range ->
            val start = x(range.minimumThousands)
            val end = x(range.maximumThousands).coerceAtLeast(start + 20.dp.toPx())
            val ring = 1.dp.toPx()
            val border = 3.dp.toPx()
            val height = 20.dp.toPx()
            roundedBar(start - ring, end + ring, centerY, height + ring * 2, colors.crowded.accent)
            roundedBar(start, end, centerY, height, colors.surface)
            roundedBar(start + border, end - border, centerY, height - border * 2, colors.crowded.accent)
        }
        val medianX = x(insight.medianThousands)
        val lineWidth = 3.dp.toPx()
        drawRoundRect(
            color = colors.textPrimary,
            topLeft = Offset(medianX - lineWidth / 2f, centerY - 16.dp.toPx()),
            size = Size(lineWidth, 32.dp.toPx()),
            cornerRadius = CornerRadius(lineWidth / 2f),
        )
    }
}

private fun DrawScope.roundedBar(start: Float, end: Float, centerY: Float, height: Float, color: androidx.compose.ui.graphics.Color) {
    if (end <= start) return
    drawRoundRect(
        color = color,
        topLeft = Offset(start, centerY - height / 2f),
        size = Size(end - start, height),
        cornerRadius = CornerRadius(height / 2f),
    )
}

/**
 * Line chart of the applicants per offer over 12 months (mockup 05): a base line, a dashed middle line, the curve
 * and a coral dot on the last month.
 *
 * @param points monthly points, oldest first.
 * @param description spoken description of the chart.
 * @param modifier modifier of the chart.
 */
@Composable
fun TrendLineChart(points: List<TrendPoint>, description: String, modifier: Modifier = Modifier) {
    val colors = JaugeTheme.colors
    Canvas(modifier = modifier.fillMaxWidth().height(64.dp).semantics { contentDescription = description }) {
        val lineColor = colors.outline
        val baseY = size.height - 4.dp.toPx()
        val topY = 8.dp.toPx()
        drawLine(lineColor, Offset(0f, baseY), Offset(size.width, baseY), strokeWidth = 1.dp.toPx())
        val middleY = (topY + baseY) / 2f + 4.dp.toPx()
        drawLine(
            lineColor,
            Offset(0f, middleY),
            Offset(size.width, middleY),
            strokeWidth = 1.dp.toPx(),
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 4.dp.toPx())),
        )
        if (points.size < 2) return@Canvas
        val values = points.map { it.applicantsPerOffer.toFloat() }
        val minimum = values.min()
        val maximum = values.max()
        val span = (maximum - minimum).takeIf { it > 0f } ?: 1f
        // The curve keeps a margin above the base line, as in the mockup.
        val curveBottom = baseY - 14.dp.toPx()
        val endPadding = 6.dp.toPx()
        val stepX = (size.width - endPadding) / (values.size - 1)
        val path = Path()
        var last = Offset.Zero
        values.forEachIndexed { index, value ->
            val x = index * stepX
            val y = curveBottom - (value - minimum) / span * (curveBottom - topY)
            if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
            last = Offset(x, y)
        }
        drawPath(path, colors.textPrimary, style = Stroke(width = 2.2.dp.toPx(), join = StrokeJoin.Round, cap = StrokeCap.Round))
        drawCircle(colors.surface, radius = 7.dp.toPx(), center = last)
        drawCircle(colors.crowded.accent, radius = 5.dp.toPx(), center = last)
    }
}
