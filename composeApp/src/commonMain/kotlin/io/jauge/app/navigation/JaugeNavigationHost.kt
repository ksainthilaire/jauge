package io.jauge.app.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import io.jauge.core.domain.model.OnboardingStep
import io.jauge.feature.account.navigation.AccountRoute
import io.jauge.feature.account.navigation.PrivacyPolicyRoute
import io.jauge.feature.account.navigation.TermsOfUseRoute
import io.jauge.feature.account.navigation.accountGraph
import io.jauge.feature.analysis.navigation.AnalysisNavigator
import io.jauge.feature.analysis.navigation.HomeRoute
import io.jauge.feature.analysis.navigation.analysisGraph
import io.jauge.feature.onboarding.navigation.AlertsPermissionRoute
import io.jauge.feature.onboarding.navigation.WelcomeRoute
import io.jauge.feature.onboarding.navigation.onboardingGraph
import io.jauge.feature.paywall.navigation.QuotaPaywallRoute
import io.jauge.feature.paywall.navigation.paywallGraph
import io.jauge.feature.share.navigation.ShareRoute
import io.jauge.feature.share.navigation.shareGraph
import io.jauge.feature.twins.navigation.TwinOffersRoute
import io.jauge.feature.twins.navigation.twinsGraph
import io.jauge.feature.verdict.navigation.MethodologyRoute
import io.jauge.feature.verdict.navigation.VerdictRoute
import io.jauge.feature.verdict.navigation.verdictGraph

/**
 * Navigation graph of the whole app: every feature adds its screens here, and navigation between features is wired
 * through the callbacks of each `xxxGraph` function.
 *
 * @param navigationController controller shared with the tab bar.
 * @param startStep step of the first launch the app opens on.
 * @param modifier modifier of the host.
 */
@Composable
fun JaugeNavigationHost(
    navigationController: NavHostController,
    startStep: OnboardingStep,
    modifier: Modifier = Modifier,
) {
    val onBack: () -> Unit = { navigationController.popBackStack() }
    val openHome: () -> Unit = {
        navigationController.navigate(HomeRoute) {
            popUpTo(navigationController.graph.id) { inclusive = true }
        }
    }
    val startDestination: Any = remember {
        when (startStep) {
            OnboardingStep.WELCOME -> WelcomeRoute
            OnboardingStep.ACCOUNT -> AccountRoute(fromOnboarding = true)
            OnboardingStep.ALERTS -> AlertsPermissionRoute
            OnboardingStep.DONE -> HomeRoute
        }
    }
    NavHost(
        navController = navigationController,
        startDestination = startDestination,
        modifier = modifier,
    ) {
        onboardingGraph(
            onStartAnalysis = { navigationController.navigate(AccountRoute(fromOnboarding = true)) },
            onSignIn = { navigationController.navigate(AccountRoute(fromOnboarding = true)) },
            onSkip = openHome,
            onAlertsDone = openHome,
        )
        analysisGraph(
            AnalysisNavigator(
                onBack = onBack,
                onNavigate = { route, replaceCurrent ->
                    navigationController.navigate(route) {
                        if (replaceCurrent) navigationController.currentDestination?.id?.let { popUpTo(it) { inclusive = true } }
                    }
                },
                onBackToHome = { navigationController.popBackStack<HomeRoute>(inclusive = false) },
                onOpenAnalysis = { analysisId, replaceCurrent ->
                    navigationController.navigate(VerdictRoute(analysisId)) {
                        if (replaceCurrent) navigationController.currentDestination?.id?.let { popUpTo(it) { inclusive = true } }
                    }
                },
                onOpenPaywall = { navigationController.navigate(QuotaPaywallRoute) },
            ),
        )
        verdictGraph(
            navigationController = navigationController,
            onBack = onBack,
            onShare = { analysisId -> navigationController.navigate(ShareRoute(analysisId)) },
            onOpenTwins = { analysisId -> navigationController.navigate(TwinOffersRoute(analysisId)) },
        )
        twinsGraph(
            onBack = onBack,
            onAnalyzeOffer = { navigationController.navigateToTopLevelDestination(TopLevelDestination.ANALYZE) },
        )
        shareGraph(
            onBack = onBack,
            onOpenAnalysis = { analysisId -> navigationController.navigate(VerdictRoute(analysisId)) },
        )
        paywallGraph(
            onBack = onBack,
            onOpenTerms = { navigationController.navigate(TermsOfUseRoute) },
            onOpenPrivacy = { navigationController.navigate(PrivacyPolicyRoute) },
            onSignIn = { navigationController.navigate(AccountRoute(fromOnboarding = false)) },
        )
        accountGraph(
            onBack = onBack,
            onOpenEmailSignIn = { route -> navigationController.navigate(route) },
            onAccountDone = { fromOnboarding ->
                if (fromOnboarding) {
                    navigationController.navigate(AlertsPermissionRoute) {
                        popUpTo(navigationController.graph.id) { inclusive = true }
                    }
                } else {
                    navigationController.popBackStack<AccountRoute>(inclusive = true)
                }
            },
            onOpenTerms = { navigationController.navigate(TermsOfUseRoute) },
            onOpenPrivacyPolicy = { navigationController.navigate(PrivacyPolicyRoute) },
            onNavigate = { route -> navigationController.navigate(route) },
            onOpenPaywall = { navigationController.navigate(PaywallRoute) },
            onOpenMethodology = { analysisId -> navigationController.navigate(MethodologyRoute(analysisId)) },
            onAccountDeleted = {
                navigationController.navigate(WelcomeRoute) {
                    popUpTo(navigationController.graph.id) { inclusive = true }
                }
            },
        )
    }
}

/**
 * Opens a tab, keeping one copy of each tab in the back stack and restoring its state.
 *
 * @param destination the tab to open.
 */
fun NavHostController.navigateToTopLevelDestination(destination: TopLevelDestination) {
    navigate(destination.route) {
        popUpTo(HomeRoute) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
