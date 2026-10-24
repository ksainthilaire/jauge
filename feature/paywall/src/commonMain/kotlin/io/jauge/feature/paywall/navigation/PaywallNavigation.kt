package io.jauge.feature.paywall.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import io.jauge.feature.paywall.paywall.PaywallScreen
import kotlinx.serialization.Serializable

/** Route of [io.jauge.feature.paywall.paywall.PaywallScreen]. */
@Serializable
data object PaywallRoute

/**
 * Adds the screens of the paywall feature (the Jauge Plus subscription offer (mockup 08)) to the navigation graph.
 *
 * Navigation to other features goes through callbacks wired in `composeApp`: add a parameter here when a screen
 * needs to open another feature.
 *
 * @param onBack pops the current screen.
 */
fun NavGraphBuilder.paywallGraph(onBack: () -> Unit) {
    composable<PaywallRoute> {
        PaywallScreen(onBack = onBack)
    }
}
