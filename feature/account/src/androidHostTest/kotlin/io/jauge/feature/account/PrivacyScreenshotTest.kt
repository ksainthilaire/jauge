package io.jauge.feature.account

import androidx.compose.runtime.Composable
import com.github.takahirom.roborazzi.captureRoboImage
import io.jauge.core.designsystem.theme.JaugeTheme
import io.jauge.feature.account.deleteaccount.DeleteAccountContent
import io.jauge.feature.account.deleteaccount.DeleteAccountUiState
import io.jauge.feature.account.privacypolicy.PrivacyPolicyScreen
import org.jetbrains.compose.resources.ExperimentalResourceApi
import org.jetbrains.compose.resources.PreviewContextConfigurationEffect
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Screenshots of the privacy texts on a very tall screen (393 x 2600), so that every section of the privacy policy can
 * be read in one picture, in light and dark themes.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "fr-rFR-w393dp-h2600dp-xhdpi")
class PrivacyScreenshotTest {

    /** Whole privacy policy, light theme. */
    @Test
    fun privacyPolicyLight() = capture("legal-politique-de-confidentialite-complete-clair") {
        JaugeTheme(darkTheme = false) { PrivacyPolicyScreen(onBack = {}) }
    }

    /** Whole privacy policy, dark theme. */
    @Test
    fun privacyPolicyDark() = capture("legal-politique-de-confidentialite-complete-sombre") {
        JaugeTheme(darkTheme = true) { PrivacyPolicyScreen(onBack = {}) }
    }

    /** Account deletion, dark theme. */
    @Test
    fun deleteAccountDark() = capture("suppression-du-compte-sombre") {
        JaugeTheme(darkTheme = true) { DeleteAccountContent(DeleteAccountUiState(isConfirmed = true), {}, {}, {}, {}) }
    }

    @OptIn(ExperimentalResourceApi::class)
    private fun capture(name: String, content: @Composable () -> Unit) {
        captureRoboImage(filePath = "build/screenshots/$name.png") {
            PreviewContextConfigurationEffect()
            content()
        }
    }
}
