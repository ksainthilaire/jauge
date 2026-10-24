package io.jauge.feature.share.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import io.jauge.feature.share.share.ShareScreen
import io.jauge.feature.share.shares.SharesScreen
import kotlinx.serialization.Serializable

/**
 * Route of [io.jauge.feature.share.share.ShareScreen].
 *
 * @property analysisId identifier passed to the screen.
 */
@Serializable
data class ShareRoute(val analysisId: String)

/** Route of [io.jauge.feature.share.shares.SharesScreen]. */
@Serializable
data object SharesRoute

/**
 * Adds the screens of the share feature (sharing an analysis as a link or a card (mockup 07), and the tab
 * "Partages") to the navigation graph.
 *
 * @param onBack pops the current screen.
 * @param onOpenAnalysis opens the verdict of a shared analysis.
 */
fun NavGraphBuilder.shareGraph(onBack: () -> Unit, onOpenAnalysis: (analysisId: String) -> Unit) {
    composable<ShareRoute> { entry ->
        val route = entry.toRoute<ShareRoute>()
        ShareScreen(analysisId = route.analysisId, onBack = onBack)
    }
    composable<SharesRoute> {
        SharesScreen(onOpenAnalysis = onOpenAnalysis)
    }
}
