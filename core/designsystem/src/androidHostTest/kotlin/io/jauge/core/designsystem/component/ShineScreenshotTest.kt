package io.jauge.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureRoboImage
import io.jauge.core.designsystem.icon.JaugeIcons
import io.jauge.core.designsystem.theme.JaugeTheme
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Renders the reflection of the trial button at a few moments, on light and dark backgrounds. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w393dp-h700dp-xhdpi")
class ShineScreenshotTest {

    @Test
    fun trialButtonShine() {
        captureRoboImage(System.getProperty("jauge.screenshots.directory") + "/bouton-essai-reflet.png") {
            Column {
                for (dark in listOf(false, true)) {
                    JaugeTheme(darkTheme = dark) {
                        Column(
                            modifier = Modifier.background(JaugeTheme.colors.background).padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            for (progress in listOf(0.2f, 0.45f, 0.7f)) {
                                JaugeButton(
                                    text = "Essayer 3 jours gratuits",
                                    onClick = {},
                                    trailingIcon = JaugeIcons.ArrowRight,
                                    shine = true,
                                    shineProgress = progress,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
