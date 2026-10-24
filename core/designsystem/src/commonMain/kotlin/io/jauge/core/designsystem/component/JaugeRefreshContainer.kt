package io.jauge.core.designsystem.component

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import io.jauge.core.designsystem.theme.JaugeTheme

/**
 * Pull to refresh container. Every screen that loads data wraps its scrollable content in it and binds
 * [isRefreshing] to its observed state.
 *
 * @param isRefreshing true while a refresh is running.
 * @param onRefresh called when the user pulls to refresh.
 * @param modifier modifier of the container.
 * @param content scrollable content (a `LazyColumn` or a `verticalScroll` column).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JaugeRefreshContainer(
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    val state = rememberPullToRefreshState()
    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = onRefresh,
        modifier = modifier,
        state = state,
        indicator = {
            PullToRefreshDefaults.Indicator(
                state = state,
                isRefreshing = isRefreshing,
                modifier = Modifier.align(Alignment.TopCenter),
                containerColor = JaugeTheme.colors.surface,
                color = JaugeTheme.colors.action,
            )
        },
        content = content,
    )
}
