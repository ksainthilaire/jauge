package io.jauge.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.airbnb.lottie.LottieCompositionFactory
import com.airbnb.lottie.compose.LottieAnimation
import com.github.takahirom.roborazzi.captureRoboImage
import io.jauge.core.designsystem.theme.JaugeTheme
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import kotlin.test.assertNotNull

/** Renders the brand Lottie animations at a few moments, on light and dark backgrounds. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w393dp-h400dp-xhdpi")
class LottieAnimationScreenshotTest {

    private fun composition(name: String) = assertNotNull(
        LottieCompositionFactory.fromJsonStringSync(File("src/commonMain/composeResources/files/lottie/$name").readText(), name).value,
        "$name must be a valid Lottie document",
    )

    @Test
    fun animationsRender() {
        val loading = composition("loading.json")
        val success = composition("success.json")
        captureRoboImage(System.getProperty("jauge.screenshots.directory") + "/lottie-animations.png") {
            Column {
                for (dark in listOf(false, true)) {
                    JaugeTheme(darkTheme = dark) {
                        Row(
                            modifier = Modifier.background(JaugeTheme.colors.background).padding(12.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            for (progress in listOf(0f, 0.5f)) {
                                LottieAnimation(composition = loading, progress = { progress }, modifier = Modifier.size(84.dp))
                            }
                            for (progress in listOf(0.6f, 0.98f)) {
                                LottieAnimation(composition = success, progress = { progress }, modifier = Modifier.size(84.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}
