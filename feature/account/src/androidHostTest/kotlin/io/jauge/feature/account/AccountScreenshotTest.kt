package io.jauge.feature.account

import androidx.compose.runtime.Composable
import com.github.takahirom.roborazzi.captureRoboImage
import io.jauge.feature.account.account.AccountContent
import io.jauge.feature.account.account.AccountUiState
import io.jauge.feature.account.email.EmailSignInContent
import io.jauge.feature.account.email.EmailSignInStep
import io.jauge.feature.account.email.EmailSignInUiState
import org.jetbrains.compose.resources.ExperimentalResourceApi
import org.jetbrains.compose.resources.PreviewContextConfigurationEffect
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Screenshots of the account screens on an iPhone sized screen (393 x 852), compared with the mockups. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "fr-rFR-w393dp-h852dp-xxhdpi")
class AccountScreenshotTest {

    /** Mockup 11. */
    @Test
    fun account() = capture("11-compte-et-accords") {
        AccountContent(AccountUiState(), {}, {}, {}, {}, {}, {}, {})
    }

    /** Email link step of mockup 11. */
    @Test
    fun emailLink() = capture("11b-lien-par-email") {
        EmailSignInContent(EmailSignInUiState(step = EmailSignInStep.LINK_SENT, email = "ines@exemple.fr"), {}, {}, {}, {}, {})
    }

    @OptIn(ExperimentalResourceApi::class)
    internal fun capture(name: String, content: @Composable () -> Unit) {
        captureRoboImage(filePath = "build/screenshots/$name.png") {
            PreviewContextConfigurationEffect()
            content()
        }
    }
}
