package io.jauge.core.designsystem.component

import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import io.github.alexzhirkevich.compottie.Compottie
import io.github.alexzhirkevich.compottie.LottieCompositionSpec
import io.github.alexzhirkevich.compottie.animateLottieCompositionAsState
import io.github.alexzhirkevich.compottie.rememberLottieComposition
import io.github.alexzhirkevich.compottie.rememberLottiePainter

/** iOS player: Compottie (Lottie for Compose Multiplatform). */
@Composable
internal actual fun PlatformLottieAnimation(
    json: String,
    loops: Boolean,
    modifier: Modifier,
    onFinished: () -> Unit,
) {
    val composition by rememberLottieComposition { LottieCompositionSpec.JsonString(json) }
    val progress by animateLottieCompositionAsState(
        composition = composition,
        iterations = if (loops) Compottie.IterateForever else 1,
    )
    if (!loops) {
        LaunchedEffect(composition, progress) {
            if (composition != null && progress >= 1f) onFinished()
        }
    }
    Image(
        painter = rememberLottiePainter(composition = composition, progress = { progress }),
        contentDescription = null,
        modifier = modifier,
    )
}
