package io.jauge.feature.verdict

import androidx.compose.runtime.Composable
import com.github.takahirom.roborazzi.captureRoboImage
import io.jauge.core.designsystem.theme.JaugeTheme
import io.jauge.core.ui.state.ContentState
import io.jauge.feature.verdict.details.DetailsLayout
import io.jauge.feature.verdict.details.DetailsSection
import io.jauge.feature.verdict.details.DetailsUiState
import io.jauge.feature.verdict.verdict.VerdictLayout
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Verdict and details with the advice and the reputation written by the server (`result.advice`,
 * `result.reputation`), on a tall screen so that the cards under the key figures show, in light and dark themes.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "fr-w393dp-h1200dp-xhdpi")
class AdviceScreenshotTest {

    private fun capture(name: String, darkTheme: Boolean, content: @Composable () -> Unit) {
        val directory = System.getProperty("jauge.screenshots.directory") ?: "build/screenshots"
        captureRoboImage("$directory/$name.png") { JaugeTheme(darkTheme = darkTheme) { content() } }
    }

    /** Verdict with the advice and the reputation, light theme. */
    @Test
    fun verdictWithReputationLight() = verdict(darkTheme = false, name = "04-verdict-conseil-avis-clair")

    /** Verdict with the advice and the reputation, dark theme. */
    @Test
    fun verdictWithReputationDark() = verdict(darkTheme = true, name = "04-verdict-conseil-avis-sombre")

    /** Details with the negotiation sentence and the missing skills, light theme. */
    @Test
    fun detailsWithAdviceLight() = details(darkTheme = false, name = "05-details-conseil-clair")

    /** Details with the negotiation sentence and the missing skills, dark theme. */
    @Test
    fun detailsWithAdviceDark() = details(darkTheme = true, name = "05-details-conseil-sombre")

    private fun verdict(darkTheme: Boolean, name: String) = capture(name, darkTheme) {
        VerdictLayout(
            state = ContentState(content = SampleData.analysisWithAdvice, isLoading = false),
            onBack = {}, onRefresh = {}, onShare = {}, onOpenDetails = {}, onOpenMethodology = {}, onOpenTwins = {}, onOpenTrend = {},
            animateGauge = false,
        )
    }

    private fun details(darkTheme: Boolean, name: String) = capture(name, darkTheme) {
        DetailsLayout(
            state = DetailsUiState(
                salary = ContentState(SampleData.salary, isLoading = false),
                skills = ContentState(SampleData.skills, isLoading = false),
                trend = ContentState(SampleData.trend, isLoading = false),
                reliability = SampleData.analysis.reliability,
                advice = SampleData.analysisWithAdvice.advice,
            ),
            initialSection = DetailsSection.SALARIES,
            onBack = {}, onRefresh = {}, onOpenMethodology = {},
        )
    }
}
