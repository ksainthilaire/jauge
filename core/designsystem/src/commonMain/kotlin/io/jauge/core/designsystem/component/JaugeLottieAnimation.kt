package io.jauge.core.designsystem.component

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.jauge.core.designsystem.resources.Res

/**
 * Lottie animations of the design system, drawn in the brand colors (`tools/lottie/generate_animations.py` writes
 * them to `composeResources/files/lottie`).
 *
 * @property resourcePath path of the JSON file in the Compose resources.
 * @property loops true to repeat the animation while it is shown.
 */
enum class JaugeAnimation(val resourcePath: String, val loops: Boolean) {
    /** A gauge whose needle scans the three levels: reading an offer (mockup 03), loading the paywall offer. */
    LOADING("files/lottie/loading.json", loops = true),

    /** A mint disc with a check: purchase done, alert created. Played once. */
    SUCCESS("files/lottie/success.json", loops = false),
}

/**
 * Plays a [JaugeAnimation]. Uses lottie-compose on Android and Compottie on iOS. While the file is read, the space
 * stays empty (no layout jump).
 *
 * @param animation the animation to play.
 * @param modifier modifier of the animation.
 * @param size width and height of the animation.
 * @param contentDescription accessibility description, null when the animation is decorative.
 * @param onFinished called once when an animation that does not loop reaches its end.
 */
@Composable
fun JaugeLottieAnimation(
    animation: JaugeAnimation,
    modifier: Modifier = Modifier,
    size: Dp = 120.dp,
    contentDescription: String? = null,
    onFinished: () -> Unit = {},
) {
    val json by produceState<String?>(initialValue = null, animation) {
        value = runCatching { Res.readBytes(animation.resourcePath).decodeToString() }.getOrNull()
    }
    val describedModifier = if (contentDescription != null) {
        modifier.semantics { this.contentDescription = contentDescription }
    } else {
        modifier
    }
    val loadedJson = json
    if (loadedJson == null) {
        Box(describedModifier.size(size))
    } else {
        PlatformLottieAnimation(
            json = loadedJson,
            loops = animation.loops,
            modifier = describedModifier.size(size),
            onFinished = onFinished,
        )
    }
}

/**
 * Platform player of a Lottie JSON document.
 *
 * @param json the Lottie document.
 * @param loops true to repeat forever, false to play once.
 * @param modifier modifier of the animation.
 * @param onFinished called once when a non looping animation ends.
 */
@Composable
internal expect fun PlatformLottieAnimation(
    json: String,
    loops: Boolean,
    modifier: Modifier,
    onFinished: () -> Unit,
)
