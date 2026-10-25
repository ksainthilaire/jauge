package io.jauge.feature.onboarding.welcome

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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.jauge.core.designsystem.component.GaugeSize
import io.jauge.core.designsystem.component.JaugeGauge
import io.jauge.core.designsystem.theme.JaugePalette
import io.jauge.core.designsystem.theme.JaugeTheme
import io.jauge.feature.onboarding.resources.Res
import io.jauge.feature.onboarding.resources.welcome_calm_badge
import io.jauge.feature.onboarding.resources.welcome_crowded_badge
import org.jetbrains.compose.resources.stringResource

/** Width of the illustration in the mockup, in dp. */
private const val HERO_WIDTH = 393f

/** Height of the illustration in the mockup, in dp. */
private const val HERO_HEIGHT = 330f

/**
 * Illustration of the welcome screen (mockup 01): the brand gauge on a coral halo, with a calm and a crowded badge.
 * The arc sweeps to [score] when the page changes (no motion when the person reduces animations).
 *
 * @param score score shown by the gauge on this page.
 * @param modifier modifier of the illustration.
 */
@Composable
fun WelcomeHero(score: Int, modifier: Modifier = Modifier) {
    val colors = JaugeTheme.colors
    BoxWithConstraints(modifier = modifier.fillMaxWidth().height(HERO_HEIGHT.dp)) {
        val scale = maxWidth.value / HERO_WIDTH
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawRect(
                brush = Brush.radialGradient(
                    colors = listOf(JaugePalette.Coral.copy(alpha = 0.25f), JaugePalette.Coral.copy(alpha = 0f)),
                    center = Offset(size.width * 0.5f, size.height * 0.62f),
                    radius = size.width * 0.48f,
                ),
            )
        }
        JaugeGauge(
            score = score,
            size = GaugeSize.HERO,
            contentColor = colors.textPrimary,
            surfaceColor = colors.background,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(start = (36 * scale).dp, end = (36 * scale).dp, bottom = (14 * scale).dp)
                .fillMaxWidth()
                .height((236 * scale).dp),
        )
        HeroBadge(
            text = stringResource(Res.string.welcome_calm_badge, 12),
            color = JaugePalette.Mint,
            modifier = Modifier.offset(x = (12 * scale).dp, y = (40 * scale).dp),
            width = (108 * scale).dp,
        )
        HeroBadge(
            text = stringResource(Res.string.welcome_crowded_badge, 210),
            color = JaugePalette.Coral,
            modifier = Modifier.offset(x = (273 * scale).dp, y = (28 * scale).dp),
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
