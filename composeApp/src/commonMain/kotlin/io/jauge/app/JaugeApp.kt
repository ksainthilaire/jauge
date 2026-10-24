package io.jauge.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import io.jauge.app.link.IncomingLinks
import io.jauge.app.navigation.JaugeNavigationHost
import io.jauge.app.notifications.CalmAlertBannerHost
import io.jauge.app.navigation.TopLevelDestination
import io.jauge.app.navigation.navigateToTopLevelDestination
import io.jauge.app.resources.Res
import io.jauge.app.resources.offline_mode_indicator
import io.jauge.core.designsystem.component.JaugePill
import io.jauge.core.designsystem.component.JaugeTabBar
import io.jauge.core.designsystem.component.JaugeTabItem
import io.jauge.core.designsystem.theme.JaugeTheme
import io.jauge.feature.share.link.AnalysisLink
import io.jauge.feature.verdict.navigation.VerdictRoute
import io.jauge.feature.twins.navigation.TwinOffersRoute
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

/**
 * Root composable of the app, shared by Android and iOS.
 *
 * It shows the offline mode indicator, the navigation graph and the tab bar on tab roots. In debug builds a long
 * press on the "Profil" tab flips between the offline data and the real API.
 */
@Composable
fun JaugeApp() {
    JaugeTheme {
        val appViewModel: AppViewModel = koinViewModel()
        val uiState by appViewModel.uiState.collectAsStateWithLifecycle()
        val onboardingStep by appViewModel.onboardingStep.collectAsStateWithLifecycle()
        val navigationController = rememberNavController()
        val incomingLink by IncomingLinks.pending.collectAsStateWithLifecycle()
        LaunchedEffect(incomingLink) {
            val analysisId = AnalysisLink.analysisIdOrNull(incomingLink) ?: return@LaunchedEffect IncomingLinks.consume()
            navigationController.navigate(VerdictRoute(analysisId)) { launchSingleTop = true }
            IncomingLinks.consume()
        }
        val backStackEntry by navigationController.currentBackStackEntryAsState()
        val currentDestination = backStackEntry?.destination
        val selectedTab = TopLevelDestination.entries.firstOrNull { tab ->
            currentDestination?.hierarchy?.any { it.hasRoute(tab.routeClass) } == true
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(JaugeTheme.colors.background)
                .statusBarsPadding(),
        ) {
            if (uiState.isOfflineMode) {
                OfflineModeIndicator()
            }
            val startStep = onboardingStep
            Box(Modifier.weight(1f)) {
                if (startStep != null) {
                    JaugeNavigationHost(
                        navigationController = navigationController,
                        startStep = startStep,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
                CalmAlertBannerHost(
                    onOpen = { analysisId -> navigationController.navigate(TwinOffersRoute(analysisId)) },
                    modifier = Modifier.align(Alignment.TopCenter),
                )
            }
            if (selectedTab != null) {
                JaugeTabBar(
                    items = TopLevelDestination.entries.map { tab ->
                        JaugeTabItem(
                            label = stringResource(tab.label),
                            icon = tab.icon,
                            selected = tab == selectedTab,
                            onClick = { navigationController.navigateToTopLevelDestination(tab) },
                            onLongClick = if (tab == TopLevelDestination.PROFILE && uiState.canToggleOfflineMode) {
                                appViewModel::toggleOfflineMode
                            } else {
                                null
                            },
                        )
                    },
                )
            }
        }
    }
}

@Composable
private fun OfflineModeIndicator() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = JaugeTheme.spacing.extraSmall),
        contentAlignment = Alignment.Center,
    ) {
        JaugePill(
            text = stringResource(Res.string.offline_mode_indicator),
            containerColor = JaugeTheme.colors.disputed.container,
            contentColor = JaugeTheme.colors.disputed.content,
            dotColor = JaugeTheme.colors.disputed.accent,
        )
    }
}
