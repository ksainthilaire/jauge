package io.jauge.feature.twins.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import io.jauge.feature.twins.alertspermission.AlertsPermissionScreen
import io.jauge.feature.twins.twinoffers.TwinOffersScreen
import kotlinx.serialization.Serializable

/**
 * Route of [io.jauge.feature.twins.twinoffers.TwinOffersScreen].
 *
 * @property analysisId identifier passed to the screen.
 */
@Serializable
data class TwinOffersRoute(val analysisId: String? = null)

/** Route of [io.jauge.feature.twins.alertspermission.AlertsPermissionScreen]. */
@Serializable
data object AlertsPermissionRoute

/**
 * Adds the screens of the twins feature (twin offers with less competition and calm alerts (mockups 06 and 12)) to the navigation graph.
 *
 * Navigation to other features goes through callbacks wired in `composeApp`: add a parameter here when a screen
 * needs to open another feature.
 *
 * @param onBack pops the current screen.
 */
fun NavGraphBuilder.twinsGraph(onBack: () -> Unit) {
    composable<TwinOffersRoute> {
        TwinOffersScreen()
    }
    composable<AlertsPermissionRoute> {
        AlertsPermissionScreen(onBack = onBack)
    }
}
