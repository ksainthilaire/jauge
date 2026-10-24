package io.jauge.feature.share.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
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
 * Adds the screens of the share feature (sharing an analysis as a link or a card (mockup 07)) to the navigation graph.
 *
 * Navigation to other features goes through callbacks wired in `composeApp`: add a parameter here when a screen
 * needs to open another feature.
 *
 * @param onBack pops the current screen.
 */
fun NavGraphBuilder.shareGraph(onBack: () -> Unit) {
    composable<ShareRoute> {
        ShareScreen(onBack = onBack)
    }
    composable<SharesRoute> {
        SharesScreen()
    }
}
