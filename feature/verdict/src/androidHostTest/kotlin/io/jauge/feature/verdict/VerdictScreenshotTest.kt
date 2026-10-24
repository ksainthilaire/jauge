package io.jauge.feature.verdict

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import com.github.takahirom.roborazzi.captureRoboImage
import io.jauge.core.designsystem.theme.JaugeTheme
import io.jauge.core.ui.state.ContentState
import io.jauge.feature.verdict.details.DetailsLayout
import io.jauge.feature.verdict.details.DetailsSection
import io.jauge.feature.verdict.details.DetailsUiState
import io.jauge.feature.verdict.methodology.MethodologyLayout
import io.jauge.feature.verdict.methodology.MethodologyUiState
import io.jauge.feature.verdict.verdict.VerdictLayout
import io.jauge.core.domain.error.JaugeError
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Renders the screens of the verdict feature with the data of the mockups into `docs/screenshots`, to compare them
 * with `annonce-emploi/ecran-04`, `05` and `09`.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "fr-w393dp-h852dp-xhdpi")
class VerdictScreenshotTest {

    private fun capture(name: String, darkTheme: Boolean = false, content: @Composable () -> Unit) {
        val directory = System.getProperty("jauge.screenshots.directory") ?: "build/screenshots"
        captureRoboImage("$directory/$name.png") { JaugeTheme(darkTheme = darkTheme) { content() } }
    }

    /** Verdict of a favorite analysis (filled star), light and dark themes. */
    @Test
    fun verdictFavorite() {
        listOf(false, true).forEach { dark ->
            capture(if (dark) "04-verdict-favori-sombre" else "04-verdict-favori", darkTheme = dark) {
                VerdictLayout(
                    state = ContentState(content = SampleData.analysis, isLoading = false),
                    onBack = {}, onRefresh = {}, onShare = {}, onOpenDetails = {}, onOpenMethodology = {}, onOpenTwins = {}, onOpenTrend = {},
                    animateGauge = false,
                    isFavorite = true,
                )
            }
        }
    }

    /** Score 19 (the case seen on a phone) with a large system text size, dark theme: the digits stay below the pivot. */
    @Test
    fun scoreNineteenLargeTextDark() = scoreCase(score = 19, fontScale = 1.3f, darkTheme = true, name = "04-verdict-score-19-texte-grand-sombre")

    /** Score 19 with a large system text size, light theme. */
    @Test
    fun scoreNineteenLargeTextLight() = scoreCase(score = 19, fontScale = 1.3f, darkTheme = false, name = "04-verdict-score-19-texte-grand-clair")

    /** Score 100 (three digits) with the largest system text size, dark theme. */
    @Test
    fun scoreHundredLargestTextDark() = scoreCase(score = 100, fontScale = 2f, darkTheme = true, name = "04-verdict-score-100-texte-200-sombre")

    /** Score 0 (needle flat on the left) at the default text size, light theme. */
    @Test
    fun scoreZeroLight() = scoreCase(score = 0, fontScale = 1f, darkTheme = false, name = "04-verdict-score-0-clair")

    private fun scoreCase(score: Int, fontScale: Float, darkTheme: Boolean, name: String) = capture(name, darkTheme) {
        val density = LocalDensity.current
        CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
            VerdictLayout(
                state = ContentState(content = SampleData.analysis.copy(score = score, percentileInRegion = score), isLoading = false),
                onBack = {}, onRefresh = {}, onShare = {}, onOpenDetails = {}, onOpenMethodology = {}, onOpenTwins = {}, onOpenTrend = {},
                animateGauge = false,
            )
        }
    }

    /** Pull to refresh running: the indicator is whole, below the top bar (light theme). */
    @Test
    fun verdictRefreshingLight() = verdictRefreshing(darkTheme = false)

    /** Pull to refresh running: the indicator is whole, below the top bar (dark theme). */
    @Test
    fun verdictRefreshingDark() = verdictRefreshing(darkTheme = true)

    private fun verdictRefreshing(darkTheme: Boolean) =
        capture(if (darkTheme) "04-verdict-rafraichir-sombre" else "04-verdict-rafraichir-clair", darkTheme) {
            VerdictLayout(
                state = ContentState(content = SampleData.analysis, isLoading = false, isRefreshing = true),
                onBack = {}, onRefresh = {}, onShare = {}, onOpenDetails = {}, onOpenMethodology = {}, onOpenTwins = {}, onOpenTrend = {},
                animateGauge = false,
            )
        }

    @Test
    fun verdict() = capture("04-verdict") {
        VerdictLayout(
            state = ContentState(content = SampleData.analysis, isLoading = false),
            onBack = {}, onRefresh = {}, onShare = {}, onOpenDetails = {}, onOpenMethodology = {}, onOpenTwins = {}, onOpenTrend = {},
            animateGauge = false,
        )
    }

    @Test
    fun verdictLoading() = capture("04-verdict-loading") {
        VerdictLayout(
            state = ContentState(),
            onBack = {}, onRefresh = {}, onShare = {}, onOpenDetails = {}, onOpenMethodology = {}, onOpenTwins = {}, onOpenTrend = {},
        )
    }

    @Test
    fun verdictError() = capture("04-verdict-error") {
        VerdictLayout(
            state = ContentState(isLoading = false, error = JaugeError.NoConnection),
            onBack = {}, onRefresh = {}, onShare = {}, onOpenDetails = {}, onOpenMethodology = {}, onOpenTwins = {}, onOpenTrend = {},
        )
    }

    @Test
    fun details() = capture("05-details") {
        DetailsLayout(
            state = DetailsUiState(
                salary = ContentState(SampleData.salary, isLoading = false),
                skills = ContentState(SampleData.skills, isLoading = false),
                trend = ContentState(SampleData.trend, isLoading = false),
                reliability = SampleData.analysis.reliability,
            ),
            initialSection = DetailsSection.SALARIES,
            onBack = {}, onRefresh = {}, onOpenMethodology = {},
        )
    }

    @Test
    fun methodology() = capture("09-methodology") {
        MethodologyLayout(
            state = MethodologyUiState(methodology = ContentState(SampleData.methodology, isLoading = false)),
            snackbarHostState = SnackbarHostState(),
            onBack = {}, onRefresh = {}, onOpenFeedback = {}, onFeedbackTextChange = {}, onSendFeedback = {}, onDismissFeedback = {},
        )
    }
}
