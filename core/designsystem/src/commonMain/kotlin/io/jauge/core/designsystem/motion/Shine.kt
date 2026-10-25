package io.jauge.core.designsystem.motion

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState
import kotlin.math.sqrt

/**
 * Light reflection that sweeps diagonally across an element every [periodMilliseconds], to draw the eye to a main
 * offer ("Essayer 3 jours gratuits"). The band is drawn behind the content of the element, so a label above it is
 * never covered. Place it after the background of the element (`background(color, shape).shine(shape)`).
 *
 * Nothing moves, and nothing is computed, when the user asked the system for fewer animations (Android: animation
 * scale at 0, iOS: "Réduire les animations") or while the screen is not in the foreground (lifecycle below RESUMED):
 * the animation is then not even created. Only the drawing phase reads the progress, so a frame never recomposes.
 *
 * @param shape shape of the element; the band is clipped to it.
 * @param color color of the center of the band, white with a low alpha on the coral buttons.
 * @param enabled false to switch the reflection off (disabled or loading button).
 * @param periodMilliseconds time between two sweeps, sweep included.
 * @param fixedProgress position of the band between 0 and 1 for screenshots and previews, null to animate.
 */
@Composable
fun Modifier.shine(
    shape: Shape,
    color: Color = ShineDefaults.Color,
    enabled: Boolean = true,
    periodMilliseconds: Int = ShineDefaults.PERIOD_MILLISECONDS,
    fixedProgress: Float? = null,
): Modifier {
    if (!enabled) return this
    if (fixedProgress != null) return clip(shape).drawBehind { drawShine(fixedProgress, color) }
    val reducedMotion = rememberReducedMotion()
    val lifecycleState by LocalLifecycleOwner.current.lifecycle.currentStateAsState()
    if (reducedMotion || !lifecycleState.isAtLeast(Lifecycle.State.RESUMED)) return this
    val progress = rememberShineProgress(periodMilliseconds)
    return clip(shape).drawBehind { drawShine(progress.value, color) }
}

/** Default values of [shine]. */
object ShineDefaults {
    /** Center of the band: a soft white, light enough to keep a white label readable as it passes. */
    val Color = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.30f)

    /** Time between two sweeps (3.5 s), sweep included. */
    const val PERIOD_MILLISECONDS = 3_500

    /** Duration of one sweep across the element. */
    const val SWEEP_MILLISECONDS = 1_000
}

@Composable
private fun rememberShineProgress(periodMilliseconds: Int): State<Float> {
    val transition = rememberInfiniteTransition(label = "shine")
    val sweep = ShineDefaults.SWEEP_MILLISECONDS.coerceAtMost(periodMilliseconds)
    return transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            keyframes {
                durationMillis = periodMilliseconds
                0f at 0 using FastOutSlowInEasing
                1f at sweep
                1f at periodMilliseconds
            },
        ),
        label = "shine progress",
    )
}

/** Draws the band at [progress] (0: before the left edge, 1: past the right edge), tilted by about 25 degrees. */
private fun DrawScope.drawShine(progress: Float, color: Color) {
    val bandWidth = size.width * BAND_WIDTH_RATIO
    val travel = size.width + 2 * bandWidth
    val center = Offset(-bandWidth + travel * progress.coerceIn(0f, 1f), size.height / 2)
    val length = sqrt(1f + TILT * TILT)
    val direction = Offset(1f / length, TILT / length) * (bandWidth / 2)
    drawRect(
        brush = Brush.linearGradient(
            0f to Color.Transparent,
            0.5f to color,
            1f to Color.Transparent,
            start = center - direction,
            end = center + direction,
        ),
    )
}

/** Width of the band compared with the element. */
private const val BAND_WIDTH_RATIO = 0.32f

/** Vertical part of the direction across the band: tilts it so that it crosses the element diagonally. */
private const val TILT = 0.47f
