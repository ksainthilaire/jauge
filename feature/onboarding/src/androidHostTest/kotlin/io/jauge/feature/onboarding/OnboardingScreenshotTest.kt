package io.jauge.feature.onboarding

import androidx.compose.runtime.Composable
import com.github.takahirom.roborazzi.captureRoboImage
import io.jauge.core.common.platform.Platform
import io.jauge.feature.onboarding.alerts.AlertsPermissionContent
import io.jauge.feature.onboarding.welcome.WelcomeContent
import org.jetbrains.compose.resources.ExperimentalResourceApi
import org.jetbrains.compose.resources.PreviewContextConfigurationEffect
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Screenshots of the onboarding screens on an iPhone sized screen (393 x 852), compared with the mockups. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "fr-rFR-w393dp-h852dp-xxhdpi")
class OnboardingScreenshotTest {

    /** Mockup 01. */
    @Test
    fun welcome() = capture("01-bienvenue") {
        WelcomeContent(onStartAnalysis = {}, onSignIn = {}, onSkip = {})
    }

    /** Mockup 12. */
    @Test
    fun alertsPermission() = capture("12-autoriser-alertes") {
        AlertsPermissionContent(isWaitingForSystem = false, onContinue = {}, onLater = {}, platform = Platform.IOS)
    }

    @OptIn(ExperimentalResourceApi::class)
    private fun capture(name: String, content: @Composable () -> Unit) {
        captureRoboImage(filePath = "build/screenshots/$name.png") {
            PreviewContextConfigurationEffect()
            content()
        }
    }
}
