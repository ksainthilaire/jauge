package io.jauge.feature.twins.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import io.jauge.feature.twins.twinoffers.TwinOffersScreen
import kotlinx.serialization.Serializable

/**
 * Route of [io.jauge.feature.twins.twinoffers.TwinOffersScreen].
 *
 * @property analysisId analysis whose twins are shown; null for the tab, which shows the most recent analysis.
 */
@Serializable
data class TwinOffersRoute(val analysisId: String? = null)

/**
 * Adds the screens of the twins feature (twin offers with less competition and the calm alert, mockup 06; the alerts permission, mockup 12, is in feature:onboarding) to the navigation graph.
 *
 * Navigation to other features goes through callbacks wired in `composeApp`: add a parameter here when a screen
 * needs to open another feature.
 *
 * @param onBack pops the current screen.
 * @param onAnalyzeOffer opens the paste screen (empty state of the twin offers).
 */
fun NavGraphBuilder.twinsGraph(onBack: () -> Unit, onAnalyzeOffer: () -> Unit) {
    composable<TwinOffersRoute> { entry ->
        val route = entry.toRoute<TwinOffersRoute>()
        TwinOffersScreen(
            analysisId = route.analysisId,
            onBack = if (route.analysisId != null) onBack else null,
            onAnalyzeOffer = onAnalyzeOffer,
        )
    }
}
