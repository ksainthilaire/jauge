package io.jauge.core.designsystem.component

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import com.airbnb.lottie.compose.LottieAnimation
import com.airbnb.lottie.compose.LottieCompositionSpec
import com.airbnb.lottie.compose.LottieConstants
import com.airbnb.lottie.compose.animateLottieCompositionAsState
import com.airbnb.lottie.compose.rememberLottieComposition

/** Android player: Airbnb lottie-compose. */
@Composable
internal actual fun PlatformLottieAnimation(
    json: String,
    loops: Boolean,
    modifier: Modifier,
    onFinished: () -> Unit,
) {
    val composition by rememberLottieComposition(LottieCompositionSpec.JsonString(json))
    val progress by animateLottieCompositionAsState(
        composition = composition,
        iterations = if (loops) LottieConstants.IterateForever else 1,
    )
    if (!loops) {
        LaunchedEffect(composition, progress) {
            if (composition != null && progress >= 1f) onFinished()
        }
    }
    LottieAnimation(composition = composition, progress = { progress }, modifier = modifier)
}
