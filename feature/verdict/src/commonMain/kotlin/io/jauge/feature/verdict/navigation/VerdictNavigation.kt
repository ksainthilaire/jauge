package io.jauge.feature.verdict.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import io.jauge.feature.verdict.details.DetailsScreen
import io.jauge.feature.verdict.methodology.MethodologyScreen
import io.jauge.feature.verdict.verdict.VerdictScreen
import kotlinx.serialization.Serializable

/**
 * Route of [io.jauge.feature.verdict.verdict.VerdictScreen].
 *
 * @property analysisId identifier passed to the screen.
 */
@Serializable
data class VerdictRoute(val analysisId: String)

/**
 * Route of [io.jauge.feature.verdict.details.DetailsScreen].
 *
 * @property analysisId identifier passed to the screen.
 */
@Serializable
data class DetailsRoute(val analysisId: String)

/**
 * Route of [io.jauge.feature.verdict.methodology.MethodologyScreen].
 *
 * @property analysisId identifier passed to the screen.
 */
@Serializable
data class MethodologyRoute(val analysisId: String)

/**
 * Adds the screens of the verdict feature (the result of an analysis (mockups 04, 05 and 09)) to the navigation graph.
 *
 * Navigation to other features goes through callbacks wired in `composeApp`: add a parameter here when a screen
 * needs to open another feature.
 *
 * @param onBack pops the current screen.
 */
fun NavGraphBuilder.verdictGraph(onBack: () -> Unit) {
    composable<VerdictRoute> {
        VerdictScreen(onBack = onBack)
    }
    composable<DetailsRoute> {
        DetailsScreen(onBack = onBack)
    }
    composable<MethodologyRoute> {
        MethodologyScreen(onBack = onBack)
    }
}
