package io.jauge.feature.analysis.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import io.jauge.feature.analysis.history.HistoryScreen
import io.jauge.feature.analysis.home.HomeScreen
import io.jauge.feature.analysis.manualentry.ManualEntryScreen
import io.jauge.feature.analysis.reading.ReadingScreen
import io.jauge.feature.analysis.unreadableoffer.UnreadableOfferScreen
import kotlinx.serialization.Serializable

/** Route of [io.jauge.feature.analysis.home.HomeScreen] (mockup 02, tab root). */
@Serializable
data object HomeRoute

/**
 * Route of [io.jauge.feature.analysis.reading.ReadingScreen] (mockup 03).
 *
 * @property requestId request kept in the pending analyses.
 */
@Serializable
data class ReadingRoute(val requestId: String)

/**
 * Route of [io.jauge.feature.analysis.unreadableoffer.UnreadableOfferScreen] (mockup 10).
 *
 * @property requestId request that could not be read.
 * @property recognizedCompany company that could still be read, if any.
 */
@Serializable
data class UnreadableOfferRoute(val requestId: String, val recognizedCompany: String? = null)

/** Route of [io.jauge.feature.analysis.manualentry.ManualEntryScreen]. */
@Serializable
data object ManualEntryRoute

/** Route of [io.jauge.feature.analysis.history.HistoryScreen]. */
@Serializable
data object HistoryRoute

/**
 * Callbacks of the analysis feature towards the app.
 *
 * @property onBack pops the current screen.
 * @property onNavigate opens a screen of this feature, replacing the reading screens when [replaceCurrent] is true.
 * @property onBackToHome goes back to the home screen.
 * @property onOpenAnalysis opens the verdict of an analysis, replacing the reading screen.
 * @property onOpenPaywall opens Jauge Plus.
 */
class AnalysisNavigator(
    val onBack: () -> Unit,
    val onNavigate: (route: Any, replaceCurrent: Boolean) -> Unit,
    val onBackToHome: () -> Unit,
    val onOpenAnalysis: (analysisId: String, replaceCurrent: Boolean) -> Unit,
    val onOpenPaywall: () -> Unit,
)

/**
 * Adds the screens of the analysis feature (paste an offer, reading, unreadable offer, manual entry, history
 * (mockups 02, 03 and 10)) to the navigation graph.
 *
 * @param navigator callbacks wired in `composeApp`.
 */
fun NavGraphBuilder.analysisGraph(navigator: AnalysisNavigator) {
    composable<HomeRoute> {
        HomeScreen(
            onStartReading = { requestId -> navigator.onNavigate(ReadingRoute(requestId), false) },
            onOpenAnalysis = { analysisId -> navigator.onOpenAnalysis(analysisId, false) },
            onOpenHistory = { navigator.onNavigate(HistoryRoute, false) },
            onOpenPaywall = navigator.onOpenPaywall,
        )
    }
    composable<ReadingRoute> { entry ->
        val route = entry.toRoute<ReadingRoute>()
        ReadingScreen(
            requestId = route.requestId,
            onClose = navigator.onBackToHome,
            onCompleted = { analysisId -> navigator.onOpenAnalysis(analysisId, true) },
            onUnreadable = { company -> navigator.onNavigate(UnreadableOfferRoute(route.requestId, company), true) },
            onOpenPaywall = navigator.onOpenPaywall,
        )
    }
    composable<UnreadableOfferRoute> { entry ->
        val route = entry.toRoute<UnreadableOfferRoute>()
        UnreadableOfferScreen(
            requestId = route.requestId,
            recognizedCompany = route.recognizedCompany,
            onClose = navigator.onBackToHome,
            onStartReading = { requestId -> navigator.onNavigate(ReadingRoute(requestId), true) },
            onPasteLink = navigator.onBackToHome,
            onEnterManually = { navigator.onNavigate(ManualEntryRoute, true) },
        )
    }
    composable<ManualEntryRoute> {
        ManualEntryScreen(
            onBack = navigator.onBack,
            onStartReading = { requestId -> navigator.onNavigate(ReadingRoute(requestId), true) },
        )
    }
    composable<HistoryRoute> {
        HistoryScreen(
            onBack = navigator.onBack,
            onOpenAnalysis = { analysisId -> navigator.onOpenAnalysis(analysisId, false) },
            onOpenPaywall = navigator.onOpenPaywall,
        )
    }
}
