package io.jauge.feature.analysis.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import io.jauge.feature.analysis.history.HistoryScreen
import io.jauge.feature.analysis.home.HomeScreen
import io.jauge.feature.analysis.manualentry.ManualEntryScreen
import io.jauge.feature.analysis.reading.ReadingScreen
import io.jauge.feature.analysis.unreadableoffer.UnreadableOfferScreen
import kotlinx.serialization.Serializable

/** Route of [io.jauge.feature.analysis.home.HomeScreen]. */
@Serializable
data object HomeRoute

/**
 * Route of [io.jauge.feature.analysis.reading.ReadingScreen].
 *
 * @property requestId identifier passed to the screen.
 */
@Serializable
data class ReadingRoute(val requestId: String)

/** Route of [io.jauge.feature.analysis.unreadableoffer.UnreadableOfferScreen]. */
@Serializable
data object UnreadableOfferRoute

/** Route of [io.jauge.feature.analysis.manualentry.ManualEntryScreen]. */
@Serializable
data object ManualEntryRoute

/** Route of [io.jauge.feature.analysis.history.HistoryScreen]. */
@Serializable
data object HistoryRoute

/**
 * Adds the screens of the analysis feature (pasting an offer and reading it (mockups 02, 03 and 10)) to the navigation graph.
 *
 * Navigation to other features goes through callbacks wired in `composeApp`: add a parameter here when a screen
 * needs to open another feature.
 *
 * @param onBack pops the current screen.
 */
fun NavGraphBuilder.analysisGraph(onBack: () -> Unit) {
    composable<HomeRoute> {
        HomeScreen()
    }
    composable<ReadingRoute> {
        ReadingScreen(onBack = onBack)
    }
    composable<UnreadableOfferRoute> {
        UnreadableOfferScreen(onBack = onBack)
    }
    composable<ManualEntryRoute> {
        ManualEntryScreen(onBack = onBack)
    }
    composable<HistoryRoute> {
        HistoryScreen(onBack = onBack)
    }
}
