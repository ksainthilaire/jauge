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
import io.jauge.core.domain.usecase.AnalysisFilter
import io.jauge.feature.analysis.history.HistoryContent
import io.jauge.feature.analysis.history.HistoryUiState
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
    private val favoriteAnalyses = analyses.mapIndexed { index, analysis -> analysis.copy(isFavorite = index == 0) }

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

    /** Mockup 02 with a favorite (star filled), light and dark themes. */
    @Test
    fun homeWithFavorite() {
        listOf(false, true).forEach { dark ->
            capture(if (dark) "02-favori-sombre" else "02-favori", darkTheme = dark) {
                HomeContent(
                    state = HomeUiState(
                        firstName = "Inès",
                        initials = "IB",
                        history = ContentState(content = favoriteAnalyses, isLoading = false),
                    ),
                    onLinkChange = {}, onAnalyzeLink = {}, onPickScreenshot = {}, onTakePhoto = {}, onOpenAnalysis = {},
                    onOpenHistory = {}, onRefresh = {}, onErrorAction = {}, animateGauges = false,
                )
            }
        }
    }

    /** "Tout voir" with the filter "Favoris", light and dark themes. */
    @Test
    fun historyFavorites() {
        listOf(false, true).forEach { dark ->
            capture(if (dark) "02-tout-voir-favoris-sombre" else "02-tout-voir-favoris", darkTheme = dark) {
                HistoryContent(
                    state = HistoryUiState(ContentState(content = favoriteAnalyses, isLoading = false), AnalysisFilter.FAVORITES),
                    onBack = {}, onOpenAnalysis = {}, onRefresh = {}, onErrorAction = {}, onSelectFilter = {},
                    onToggleFavorite = { _, _ -> }, onQueryChange = {}, onClearQuery = {}, animateGauges = false,
                )
            }
        }
    }

    /** "Tout voir" with every analysis, light and dark themes. */
    @Test
    fun historyAll() {
        listOf(false, true).forEach { dark ->
            capture(if (dark) "02-tout-voir-sombre" else "02-tout-voir", darkTheme = dark) {
                HistoryContent(
                    state = HistoryUiState(ContentState(content = favoriteAnalyses, isLoading = false), AnalysisFilter.ALL),
                    onBack = {}, onOpenAnalysis = {}, onRefresh = {}, onErrorAction = {}, onSelectFilter = {},
                    onToggleFavorite = { _, _ -> }, onQueryChange = {}, onClearQuery = {}, animateGauges = false,
                )
            }
        }
    }

    /** "Tout voir" with the filter "Favoris" and no star yet. */
    @Test
    fun historyNoFavorite() = capture("02-tout-voir-aucun-favori") {
        HistoryContent(
            state = HistoryUiState(ContentState(content = analyses, isLoading = false), AnalysisFilter.FAVORITES),
            onBack = {}, onOpenAnalysis = {}, onRefresh = {}, onErrorAction = {}, onSelectFilter = {},
            onToggleFavorite = { _, _ -> }, onQueryChange = {}, onClearQuery = {},
        )
    }

    private val manyAnalyses = analyses + listOf(
        AnalysisSummary("4", now - 8.days, "Community manager", "Brasserie Lumière", "Lyon 2e", 63),
        AnalysisSummary("5", now - 10.days, "Spécialiste SEO", "Éditions Garance", "Villeurbanne", 37),
        AnalysisSummary("6", now - 13.days, "Rédactrice web", "Atelier Sorel", "Lyon 3e", 71),
        AnalysisSummary("7", now - 16.days, "Product marketing manager", "Cœur de Lyon", "Lyon 1er", 55),
        AnalysisSummary("8", now - 20.days, "Chargé·e d'emailing", "Vélo Ardent", "Lyon 9e (Vaise)", 24),
        AnalysisSummary("9", now - 23.days, "Social ads manager", "Maison Odier", "Caluire", 48),
        AnalysisSummary("10", now - 27.days, "Data analyst marketing", "Groupe Pellat", "Villeurbanne", 33),
        AnalysisSummary("11", now - 31.days, "Chef de marque junior", "Fromagerie Évrard", "Écully", 82),
        AnalysisSummary("12", now - 36.days, "Growth hacker", "Studio Mirabelle", "Part-Dieu", 58),
    )

    /** "Tout voir" with more than 6 analyses: the search field appears, light and dark themes. */
    @Test
    fun historySearch() {
        listOf(false, true).forEach { dark ->
            capture(if (dark) "02-tout-voir-recherche-sombre" else "02-tout-voir-recherche", darkTheme = dark) {
                HistoryContent(
                    state = HistoryUiState(ContentState(content = manyAnalyses, isLoading = false), AnalysisFilter.ALL),
                    onBack = {}, onOpenAnalysis = {}, onRefresh = {}, onErrorAction = {}, onSelectFilter = {},
                    onToggleFavorite = { _, _ -> }, onQueryChange = {}, onClearQuery = {}, animateGauges = false,
                )
            }
        }
    }

    /** Search typed in "Tout voir": accents ignored ("velo" finds "Vélo Ardent"). */
    @Test
    fun historySearchTyped() = capture("02-tout-voir-recherche-velo") {
        HistoryContent(
            state = HistoryUiState(ContentState(content = manyAnalyses, isLoading = false), AnalysisFilter.ALL, query = "velo", appliedQuery = "velo"),
            onBack = {}, onOpenAnalysis = {}, onRefresh = {}, onErrorAction = {}, onSelectFilter = {},
            onToggleFavorite = { _, _ -> }, onQueryChange = {}, onClearQuery = {}, animateGauges = false,
        )
    }

    /** Search without result in "Tout voir", light and dark themes. */
    @Test
    fun historySearchNoResult() {
        listOf(false, true).forEach { dark ->
            capture(if (dark) "02-tout-voir-aucun-resultat-sombre" else "02-tout-voir-aucun-resultat", darkTheme = dark) {
                HistoryContent(
                    state = HistoryUiState(
                        ContentState(content = manyAnalyses, isLoading = false),
                        AnalysisFilter.ALL,
                        query = "boulangerie",
                        appliedQuery = "boulangerie",
                    ),
                    onBack = {}, onOpenAnalysis = {}, onRefresh = {}, onErrorAction = {}, onSelectFilter = {},
                    onToggleFavorite = { _, _ -> }, onQueryChange = {}, onClearQuery = {}, animateGauges = false,
                )
            }
        }
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
    private fun capture(name: String, darkTheme: Boolean = false, content: @Composable () -> Unit) {
        captureRoboImage(filePath = "build/screenshots/$name.png") {
            PreviewContextConfigurationEffect()
            JaugeTheme(darkTheme = darkTheme) { content() }
        }
    }
}
