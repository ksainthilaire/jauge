package io.jauge.feature.verdict.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import io.jauge.feature.verdict.details.DetailsScreen
import io.jauge.feature.verdict.details.DetailsSection
import io.jauge.feature.verdict.methodology.MethodologyScreen
import io.jauge.feature.verdict.verdict.VerdictScreen
import kotlinx.serialization.Serializable

/**
 * Route of [io.jauge.feature.verdict.verdict.VerdictScreen]. Also opened by the public link
 * `https://jauge.io/a/{analysisId}`.
 *
 * @property analysisId identifier passed to the screen.
 */
@Serializable
data class VerdictRoute(val analysisId: String)

/**
 * Route of [io.jauge.feature.verdict.details.DetailsScreen].
 *
 * @property analysisId identifier passed to the screen.
 * @property section name of the [DetailsSection] to show first, salaries when null.
 */
@Serializable
data class DetailsRoute(val analysisId: String, val section: String? = null)

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
 * Screens of this feature open each other through [navigationController]; other features are reached through the
 * callbacks wired in `composeApp`.
 *
 * @param navigationController controller of the app, used for the screens of this feature.
 * @param onBack pops the current screen.
 * @param onShare opens the share card of an analysis.
 * @param onOpenTwins opens the twin offers of an analysis.
 */
fun NavGraphBuilder.verdictGraph(
    navigationController: NavController,
    onBack: () -> Unit,
    onShare: (analysisId: String) -> Unit,
    onOpenTwins: (analysisId: String) -> Unit,
) {
    val openMethodology: (String) -> Unit = { analysisId -> navigationController.navigate(MethodologyRoute(analysisId)) }
    composable<VerdictRoute> { entry ->
        val route = entry.toRoute<VerdictRoute>()
        VerdictScreen(
            analysisId = route.analysisId,
            onBack = onBack,
            onShare = onShare,
            onOpenDetails = { analysisId -> navigationController.navigate(DetailsRoute(analysisId)) },
            onOpenMethodology = openMethodology,
            onOpenTwins = onOpenTwins,
            onOpenTrend = { analysisId -> navigationController.navigate(DetailsRoute(analysisId, DetailsSection.TREND.name)) },
        )
    }
    composable<DetailsRoute> { entry ->
        val route = entry.toRoute<DetailsRoute>()
        DetailsScreen(
            analysisId = route.analysisId,
            initialSection = DetailsSection.entries.firstOrNull { it.name == route.section } ?: DetailsSection.SALARIES,
            onBack = onBack,
            onOpenMethodology = openMethodology,
        )
    }
    composable<MethodologyRoute> { entry ->
        val route = entry.toRoute<MethodologyRoute>()
        MethodologyScreen(analysisId = route.analysisId, onBack = onBack)
    }
}
