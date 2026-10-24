package io.jauge.feature.paywall.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import io.jauge.feature.paywall.paywall.PaywallActions
import io.jauge.feature.paywall.paywall.PaywallScreen
import kotlinx.serialization.Serializable

/** Route of [io.jauge.feature.paywall.paywall.PaywallScreen], opened from the profile or a locked feature. */
@Serializable
data object PaywallRoute

/** Route of the paywall opened because the free analysis of the week is used (shows the lock pill of mockup 08). */
@Serializable
data object QuotaPaywallRoute

/**
 * Adds the paywall (Jauge Plus, single monthly offer with a free trial, mockup 08) to the navigation graph.
 *
 * @param onBack pops the paywall (close button, and "Continuer" once Jauge Plus is unlocked unless [onUnlocked] says
 * otherwise).
 * @param onOpenTerms opens the terms of use.
 * @param onOpenPrivacy opens the privacy policy.
 * @param onSignIn opens the account screen.
 * @param onUnlocked called when Jauge Plus was unlocked and the user continues; pops the paywall by default.
 */
fun NavGraphBuilder.paywallGraph(
    onBack: () -> Unit,
    onOpenTerms: () -> Unit = {},
    onOpenPrivacy: () -> Unit = {},
    onSignIn: () -> Unit = {},
    onUnlocked: () -> Unit = onBack,
) {
    val actions = PaywallActions(
        onClose = onBack,
        onUnlocked = onUnlocked,
        onOpenTerms = onOpenTerms,
        onOpenPrivacy = onOpenPrivacy,
        onSignIn = onSignIn,
    )
    composable<PaywallRoute> {
        PaywallScreen(showsQuotaReached = false, actions = actions)
    }
    composable<QuotaPaywallRoute> {
        PaywallScreen(showsQuotaReached = true, actions = actions)
    }
}
