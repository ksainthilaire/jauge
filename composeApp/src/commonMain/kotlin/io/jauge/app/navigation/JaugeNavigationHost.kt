package io.jauge.app.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import io.jauge.feature.account.navigation.accountGraph
import io.jauge.feature.analysis.navigation.HomeRoute
import io.jauge.feature.analysis.navigation.analysisGraph
import io.jauge.feature.onboarding.navigation.WelcomeRoute
import io.jauge.feature.onboarding.navigation.onboardingGraph
import io.jauge.feature.paywall.navigation.paywallGraph
import io.jauge.feature.share.navigation.shareGraph
import io.jauge.feature.twins.navigation.twinsGraph
import io.jauge.feature.verdict.navigation.verdictGraph

/**
 * Navigation graph of the whole app: every feature adds its screens here, and navigation between features is wired
 * through the callbacks of each `xxxGraph` function.
 *
 * @param navigationController controller shared with the tab bar.
 * @param modifier modifier of the host.
 */
@Composable
fun JaugeNavigationHost(
    navigationController: NavHostController,
    modifier: Modifier = Modifier,
) {
    val onBack: () -> Unit = { navigationController.popBackStack() }
    NavHost(
        navController = navigationController,
        startDestination = WelcomeRoute,
        modifier = modifier,
    ) {
        onboardingGraph(
            onStartAnalysis = {
                navigationController.navigate(HomeRoute) {
                    popUpTo(WelcomeRoute) { inclusive = true }
                }
            },
        )
        analysisGraph(onBack = onBack)
        verdictGraph(onBack = onBack)
        twinsGraph(onBack = onBack)
        shareGraph(onBack = onBack)
        paywallGraph(onBack = onBack)
        accountGraph(onBack = onBack)
    }
}

/**
 * Opens a tab, keeping one copy of each tab in the back stack and restoring its state.
 *
 * @param destination the tab to open.
 */
fun NavHostController.navigateToTopLevelDestination(destination: TopLevelDestination) {
    navigate(destination.route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
