package io.jauge.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureRoboImage
import io.jauge.core.designsystem.theme.JaugeTheme
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Renders the gauge at every size for the scores 0, 19, 50, 78 and 100, in light and dark themes, at the normal and
 * the largest text size, and squeezed into a short area (the case that cut the arc on a phone).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w393dp-h1000dp-xhdpi")
class GaugeScreenshotTest {

    private val scores = listOf(0, 19, 50, 78, 100)

    @Test
    fun gaugeLight() = sheet(darkTheme = false, fontScale = 1f, name = "gauge-clair")

    @Test
    fun gaugeDark() = sheet(darkTheme = true, fontScale = 1f, name = "gauge-sombre")

    @Test
    fun gaugeLargestTextDark() = sheet(darkTheme = true, fontScale = 2f, name = "gauge-texte-200-sombre")

    private fun sheet(darkTheme: Boolean, fontScale: Float, name: String) {
        captureRoboImage(System.getProperty("jauge.screenshots.directory") + "/$name.png") {
            JaugeTheme(darkTheme = darkTheme) {
                val density = LocalDensity.current
                CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
                    val colors = JaugeTheme.colors
                    Column(
                        modifier = Modifier.background(colors.background).padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        // Verdict card, then the same gauge forced into a short area: still whole, with its margin.
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Column(
                                Modifier.weight(1f).background(colors.emphasisSurface, RoundedCornerShape(20.dp)).padding(10.dp),
                            ) {
                                JaugeGauge(78, size = GaugeSize.LARGE, contentColor = colors.onEmphasisSurface, surfaceColor = colors.emphasisSurface, showOutOf = true, animate = false)
                            }
                            Column(
                                Modifier.weight(1f).background(colors.emphasisSurface, RoundedCornerShape(20.dp)).padding(10.dp),
                            ) {
                                JaugeGauge(
                                    19,
                                    size = GaugeSize.LARGE,
                                    contentColor = colors.onEmphasisSurface,
                                    surfaceColor = colors.emphasisSurface,
                                    showOutOf = true,
                                    animate = false,
                                    modifier = Modifier.fillMaxWidth().height(60.dp),
                                )
                            }
                        }
                        // Medium gauges at the width of the square share card.
                        scores.chunked(3).forEach { row ->
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                row.forEach { score ->
                                    Column(Modifier.background(colors.surface, RoundedCornerShape(14.dp)).padding(4.dp)) {
                                        JaugeGauge(score, size = GaugeSize.MEDIUM, showOutOf = true, animate = false, modifier = Modifier.width(112.dp))
                                    }
                                }
                            }
                        }
                        Row(
                            modifier = Modifier.background(colors.surface, RoundedCornerShape(14.dp)).padding(10.dp),
                            horizontalArrangement = Arrangement.spacedBy(14.dp),
                        ) {
                            scores.forEach { score -> JaugeGauge(score, size = GaugeSize.MINI, animate = false) }
                            JaugeGauge(72, size = GaugeSize.MINI, showScore = false, animate = false, modifier = Modifier.width(26.dp))
                        }
                        scores.forEach { score ->
                            if (score == 50 || score == 100) {
                                Column(Modifier.fillMaxWidth().background(colors.emphasisSurface, RoundedCornerShape(20.dp)).padding(16.dp)) {
                                    JaugeGauge(score, size = GaugeSize.LARGE, contentColor = colors.onEmphasisSurface, surfaceColor = colors.emphasisSurface, showOutOf = true, animate = false)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
