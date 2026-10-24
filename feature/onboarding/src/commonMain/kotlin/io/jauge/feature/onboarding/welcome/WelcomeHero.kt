package io.jauge.feature.onboarding.welcome

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.jauge.core.designsystem.theme.JaugePalette
import io.jauge.core.designsystem.theme.JaugeTheme
import io.jauge.feature.onboarding.resources.Res
import io.jauge.feature.onboarding.resources.welcome_calm_badge
import io.jauge.feature.onboarding.resources.welcome_crowded_badge
import org.jetbrains.compose.resources.stringResource
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** Width of the illustration in the mockup, in dp. */
private const val HERO_WIDTH = 393f

/** Height of the illustration in the mockup, in dp. */
private const val HERO_HEIGHT = 330f

/**
 * Illustration of the welcome screen (mockup 01): a large gauge on a coral halo, with a calm and a crowded badge.
 * The needle moves to [needleDegrees] when the page changes.
 *
 * @param needleDegrees angle of the needle, 180 pointing left, 0 pointing right (40 in the mockup).
 * @param modifier modifier of the illustration.
 */
@Composable
fun WelcomeHero(needleDegrees: Float, modifier: Modifier = Modifier) {
    val animatedDegrees by animateFloatAsState(needleDegrees, tween(durationMillis = 700), label = "needle")
    BoxWithConstraints(modifier = modifier.fillMaxWidth().height(HERO_HEIGHT.dp)) {
        val scale = maxWidth.value / HERO_WIDTH
        Canvas(modifier = Modifier.fillMaxSize()) {
            val unit = size.width / HERO_WIDTH
            drawRect(
                brush = Brush.radialGradient(
                    colors = listOf(JaugePalette.Coral.copy(alpha = 0.25f), JaugePalette.Coral.copy(alpha = 0f)),
                    center = Offset(size.width * 0.5f, size.height * 0.62f),
                    radius = size.width * 0.48f,
                ),
            )
            val center = Offset(196.5f * unit, 250f * unit)
            val radius = 150f * unit
            val stroke = Stroke(width = 30f * unit, cap = StrokeCap.Round)
            val topLeft = Offset(center.x - radius, center.y - radius)
            val arcSize = Size(radius * 2, radius * 2)
            listOf(
                Triple(JaugePalette.Mint, -180f, 45f),
                Triple(JaugePalette.Amber, -122.2f, 55.8f),
                Triple(JaugePalette.Coral, -52f, 52f),
            ).forEach { (color, start, sweep) ->
                drawArc(color, start, sweep, useCenter = false, topLeft = topLeft, size = arcSize, style = stroke)
            }
            val radians = animatedDegrees * PI / 180.0
            val tip = Offset(
                x = center.x + (118f * unit * cos(radians)).toFloat(),
                y = center.y - (118f * unit * sin(radians)).toFloat(),
            )
            drawLine(JaugePalette.Paper, center, tip, strokeWidth = 9f * unit, cap = StrokeCap.Round)
            drawCircle(JaugePalette.Paper, radius = 20f * unit, center = center)
            drawCircle(JaugePalette.Ink, radius = 7f * unit, center = center)
        }
        HeroBadge(
            text = stringResource(Res.string.welcome_calm_badge, 12),
            color = JaugePalette.Mint,
            modifier = Modifier.offset(x = (18 * scale).dp, y = (56 * scale).dp),
            width = (108 * scale).dp,
        )
        HeroBadge(
            text = stringResource(Res.string.welcome_crowded_badge, 210),
            color = JaugePalette.Coral,
            modifier = Modifier.offset(x = (268 * scale).dp, y = (40 * scale).dp),
            width = (108 * scale).dp,
        )
    }
}

@Composable
private fun HeroBadge(text: String, color: Color, width: Dp, modifier: Modifier = Modifier) {
    val shape = JaugeTheme.radii.pillShape
    Box(
        modifier = modifier
            .size(width = width, height = 26.dp)
            .background(JaugePalette.Ink2, shape)
            .border(1.dp, JaugePalette.Ink3, shape)
            .padding(horizontal = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = JaugeTheme.typography.numberSmall.copy(fontSize = 12.sp),
            color = color,
            maxLines = 1,
            softWrap = false,
        )
    }
}
