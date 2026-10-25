package io.jauge.feature.twins

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import com.github.takahirom.roborazzi.captureRoboImage
import io.jauge.core.designsystem.theme.JaugeTheme
import io.jauge.core.domain.model.CalmAlert
import io.jauge.core.ui.state.ContentState
import io.jauge.feature.twins.twinoffers.TwinOffersLayout
import io.jauge.feature.twins.twinoffers.TwinOffersUiState
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Renders the twin offers screen with the data of the mockups, to compare it with `annonce-emploi/ecran-06`. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "fr-w393dp-h852dp-xhdpi")
class TwinsScreenshotTest {

    private fun capture(name: String, darkTheme: Boolean = false, content: @Composable () -> Unit) {
        val directory = System.getProperty("jauge.screenshots.directory") ?: "build/screenshots"
        captureRoboImage("$directory/$name.png") { JaugeTheme(darkTheme = darkTheme) { content() } }
    }

    @Test
    fun twinOffers() = capture("06-twin-offers") { TwinOffersSample() }

    /** The calm alert card follows the dark theme instead of staying a cream block. */
    @Test
    fun twinOffersDark() = capture("06-twin-offers-sombre", darkTheme = true) { TwinOffersSample() }

    @Composable
    private fun TwinOffersSample() {
        TwinOffersLayout(
            state = TwinOffersUiState(
                analysisId = "analysis-marketing",
                twins = ContentState(TwinsSampleData.twins, isLoading = false),
                alerts = listOf(CalmAlert("alert", "analysis-marketing", 30, true)),
            ),
            snackbarHostState = SnackbarHostState(),
            onBack = null, onRefresh = {}, onSelectSort = {}, onAlertChange = {}, onAnalyzeOffer = {},
        )
    }

    @Test
    fun twinOffersEmpty() = capture("06-twin-offers-empty") {
        TwinOffersLayout(
            state = TwinOffersUiState(twins = ContentState(isLoading = false), hasNoAnalysis = true),
            snackbarHostState = SnackbarHostState(),
            onBack = null, onRefresh = {}, onSelectSort = {}, onAlertChange = {}, onAnalyzeOffer = {},
        )
    }
}
