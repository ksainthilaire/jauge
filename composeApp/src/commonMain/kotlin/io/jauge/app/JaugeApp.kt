package io.jauge.app

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import io.jauge.app.navigation.JaugeNavigationHost
import io.jauge.app.navigation.TopLevelDestination
import io.jauge.app.navigation.navigateToTopLevelDestination
import io.jauge.app.resources.Res
import io.jauge.app.resources.offline_mode_indicator
import io.jauge.core.designsystem.theme.JaugeTheme
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
        val navigationController = rememberNavController()
        val backStackEntry by navigationController.currentBackStackEntryAsState()
        val currentDestination = backStackEntry?.destination
        val selectedTab = TopLevelDestination.entries.firstOrNull { tab ->
            currentDestination?.hierarchy?.any { it.hasRoute(tab.routeClass) } == true
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .statusBarsPadding(),
        ) {
            if (uiState.isOfflineMode) {
                OfflineModeIndicator()
            }
            JaugeNavigationHost(
                navigationController = navigationController,
                modifier = Modifier.weight(1f),
            )
            if (selectedTab != null) {
                TemporaryTabBar(
                    selectedTab = selectedTab,
                    onSelect = navigationController::navigateToTopLevelDestination,
                    onProfileLongPress = {
                        if (uiState.canToggleOfflineMode) appViewModel.toggleOfflineMode()
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
            .background(MaterialTheme.colorScheme.tertiaryContainer)
            .padding(vertical = 4.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = stringResource(Res.string.offline_mode_indicator),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onTertiaryContainer,
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TemporaryTabBar(
    selectedTab: TopLevelDestination,
    onSelect: (TopLevelDestination) -> Unit,
    onProfileLongPress: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .navigationBarsPadding(),
        horizontalArrangement = Arrangement.SpaceAround,
    ) {
        TopLevelDestination.entries.forEach { tab ->
            Text(
                text = stringResource(tab.label),
                fontWeight = if (tab == selectedTab) FontWeight.Bold else FontWeight.Normal,
                modifier = Modifier
                    .weight(1f)
                    .combinedClickable(
                        onClick = { onSelect(tab) },
                        onLongClick = if (tab == TopLevelDestination.PROFILE) onProfileLongPress else null,
                    )
                    .padding(vertical = 16.dp),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
        }
    }
}
