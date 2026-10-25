package io.jauge.feature.onboarding

import androidx.compose.runtime.Composable
import io.jauge.core.designsystem.theme.JaugeTheme
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import io.jauge.feature.onboarding.location.LocationPromptCard
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
    /** Location explanation shown before the system request, light and dark themes, above the home screen. */
    @Test
    fun locationPrompt() {
        listOf(false, true).forEach { dark ->
            capture(if (dark) "localisation-explication-sombre" else "localisation-explication") {
                JaugeTheme(darkTheme = dark) {
                    Box(
                        modifier = Modifier.fillMaxSize().background(JaugeTheme.colors.background).background(Color.Black.copy(alpha = 0.45f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        LocationPromptCard(onAllow = {}, onLater = {})
                    }
                }
            }
        }
    }

    private fun capture(name: String, content: @Composable () -> Unit) {
        captureRoboImage(filePath = "build/screenshots/$name.png") {
            PreviewContextConfigurationEffect()
            content()
        }
    }
}
