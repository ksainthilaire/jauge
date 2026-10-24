package io.jauge.feature.onboarding.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import io.jauge.feature.onboarding.alerts.AlertsPermissionScreen
import io.jauge.feature.onboarding.welcome.WelcomeScreen
import kotlinx.serialization.Serializable

/** Route of [io.jauge.feature.onboarding.welcome.WelcomeScreen] (mockup 01). */
@Serializable
data object WelcomeRoute

/** Route of [io.jauge.feature.onboarding.alerts.AlertsPermissionScreen] (mockup 12). */
@Serializable
data object AlertsPermissionRoute

/**
 * Adds the screens of the first launch (welcome (mockup 01) and alerts permission (mockup 12)) to the navigation
 * graph. The account step (mockup 11) belongs to the account feature and is wired in `composeApp`.
 *
 * @param onStartAnalysis "Analyser ma première offre": opens the account step.
 * @param onSignIn "Me connecter": opens the account step.
 * @param onSkip "Passer": opens the home screen.
 * @param onAlertsDone the alerts screen was answered: opens the home screen.
 */
fun NavGraphBuilder.onboardingGraph(
    onStartAnalysis: () -> Unit,
    onSignIn: () -> Unit,
    onSkip: () -> Unit,
    onAlertsDone: () -> Unit,
) {
    composable<WelcomeRoute> {
        WelcomeScreen(onStartAnalysis = onStartAnalysis, onSignIn = onSignIn, onSkip = onSkip)
    }
    composable<AlertsPermissionRoute> {
        AlertsPermissionScreen(onDone = onAlertsDone)
    }
}
