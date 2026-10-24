package io.jauge.app

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureRoboImage
import io.jauge.app.shell.JaugeShellFrame
import io.jauge.core.common.platform.Platform
import io.jauge.core.designsystem.theme.JaugePalette
import io.jauge.core.designsystem.theme.JaugeTheme
import io.jauge.feature.onboarding.alerts.AlertsPermissionContent
import io.jauge.feature.onboarding.welcome.WelcomeContent
import org.jetbrains.compose.resources.ExperimentalResourceApi
import org.jetbrains.compose.resources.PreviewContextConfigurationEffect
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Checks that the status bar area takes the background of the screen (dark screens have no light band on top). */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "fr-rFR-w393dp-h852dp-xxhdpi")
class ShellFrameScreenshotTest {

    /** Dark welcome screen under a 54 dp status bar. */
    @Test
    fun darkScreen() = capture("cadre-ecran-sombre") {
        JaugeShellFrame(statusBarColor = JaugePalette.Ink, statusBarInsets = WindowInsets(top = 54.dp)) {
            WelcomeContent(onStartAnalysis = {}, onSignIn = {}, onSkip = {})
        }
    }

    /** Alerts screen on the alternative paper. */
    @Test
    fun alternativeScreen() = capture("cadre-ecran-papier") {
        JaugeShellFrame(statusBarColor = JaugePalette.Paper2, statusBarInsets = WindowInsets(top = 54.dp)) {
            AlertsPermissionContent(isWaitingForSystem = false, onContinue = {}, onLater = {}, platform = Platform.IOS)
        }
    }

    @OptIn(ExperimentalResourceApi::class)
    private fun capture(name: String, content: @Composable () -> Unit) {
        captureRoboImage(filePath = "build/screenshots/$name.png") {
            PreviewContextConfigurationEffect()
            JaugeTheme(darkTheme = false) { content() }
        }
    }
}
