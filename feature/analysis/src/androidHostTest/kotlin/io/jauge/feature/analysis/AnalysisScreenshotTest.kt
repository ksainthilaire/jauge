package io.jauge.feature.analysis

import androidx.compose.runtime.Composable
import com.github.takahirom.roborazzi.captureRoboImage
import io.jauge.core.designsystem.theme.JaugeTheme
import io.jauge.core.domain.model.AnalysisProgress
import io.jauge.core.domain.model.AnalysisSummary
import io.jauge.core.domain.model.OfferSource
import io.jauge.core.domain.model.ReadingStep
import io.jauge.core.domain.model.ReadingStepKind
import io.jauge.core.domain.model.ReadingStepStatus
import io.jauge.core.ui.state.ContentState
import io.jauge.feature.analysis.home.HomeContent
import io.jauge.feature.analysis.home.HomeUiState
import io.jauge.feature.analysis.reading.ReadingContent
import io.jauge.feature.analysis.reading.ReadingUiState
import io.jauge.feature.analysis.unreadableoffer.UnreadableOfferContent
import io.jauge.feature.analysis.unreadableoffer.UnreadableOfferUiState
import org.jetbrains.compose.resources.ExperimentalResourceApi
import org.jetbrains.compose.resources.PreviewContextConfigurationEffect
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days

/** Screenshots of the analysis screens on an iPhone sized screen (393 x 852), compared with the mockups. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "fr-rFR-w393dp-h852dp-xxhdpi")
class AnalysisScreenshotTest {
    private val now = Clock.System.now()
    private val analyses = listOf(
        AnalysisSummary("1", now - 1.days, "Chargée de marketing digital", "Atelier Sorel", "Lyon 3e", 78),
        AnalysisSummary("2", now - 3.days, "Traffic manager", "Groupe Pellat", "Villeurbanne", 44),
        AnalysisSummary("3", now - 5.days, "Responsable CRM", "Vélo Ardent", "Vaise", 19),
    )

    /** Mockup 02. */
    @Test
    fun home() = capture("02-coller-une-offre") {
        HomeContent(
            state = HomeUiState(
                firstName = "Inès",
                initials = "IB",
                link = "emplois-rhone.fr/offre/charge-marketing-digital-cdi-lyon-3-41872",
                history = ContentState(content = analyses, isLoading = false),
            ),
            onLinkChange = {}, onAnalyzeLink = {}, onPickScreenshot = {}, onTakePhoto = {}, onOpenAnalysis = {},
            onOpenHistory = {}, onRefresh = {}, onErrorAction = {}, animateGauges = false,
        )
    }

    /** Mockup 03. */
    @Test
    fun reading() = capture("03-lecture-capture") {
        ReadingContent(
            state = ReadingUiState(
                source = OfferSource.Link("https://emplois-rhone.fr/offre/charge-marketing-digital-cdi-lyon-3-41872"),
                progress = AnalysisProgress.Reading(
                    analysisId = "1",
                    percent = 62,
                    steps = listOf(
                        ReadingStep(ReadingStepKind.POSITION_AND_PLACE, ReadingStepStatus.DONE, "Chargé(e) de marketing digital · Lyon 3e · CDI"),
                        ReadingStep(ReadingStepKind.SKILLS, ReadingStepStatus.DONE, "Google Ads, Meta Ads, Brevo, GA4, Looker Studio, SEO"),
                        ReadingStep(ReadingStepKind.COMPARISON, ReadingStepStatus.RUNNING, null),
                        ReadingStep(ReadingStepKind.APPLICANTS, ReadingStepStatus.PENDING, null),
                    ),
                    publishedDaysAgo = 2,
                    hasDisplayedSalary = false,
                ),
            ),
            onClose = {}, onErrorAction = {}, animate = false,
        )
    }

    /** Mockup 10. */
    @Test
    fun unreadableOffer() = capture("10-offre-illisible") {
        UnreadableOfferContent(
            state = UnreadableOfferUiState(recognizedCompany = "Maison Odier"),
            onClose = {}, onRetakePhoto = {}, onPasteLink = {}, onEnterManually = {},
        )
    }

    @OptIn(ExperimentalResourceApi::class)
    private fun capture(name: String, content: @Composable () -> Unit) {
        captureRoboImage(filePath = "build/screenshots/$name.png") {
            PreviewContextConfigurationEffect()
            JaugeTheme(darkTheme = false) { content() }
        }
    }
}
