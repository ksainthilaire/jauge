package io.jauge.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshState
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import io.jauge.core.designsystem.theme.JaugeTheme

/**
 * Pull to refresh container. Every screen that loads data wraps its scrollable content in it and binds
 * [isRefreshing] to its observed state.
 *
 * The indicator always appears whole, just below the top edge of the container (so below the top bar of the
 * screen): it grows and fades in while the user pulls, instead of sliding out from behind the top bar where it
 * would be cut.
 *
 * @param isRefreshing true while a refresh is running.
 * @param onRefresh called when the user pulls to refresh.
 * @param modifier modifier of the container.
 * @param state pull state, exposed for the screenshot tests.
 * @param content scrollable content (a `LazyColumn` or a `verticalScroll` column).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JaugeRefreshContainer(
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
    state: PullToRefreshState = rememberPullToRefreshState(),
    content: @Composable BoxScope.() -> Unit,
) {
    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = onRefresh,
        modifier = modifier,
        state = state,
        indicator = {
            JaugeRefreshIndicator(
                state = state,
                isRefreshing = isRefreshing,
                modifier = Modifier.align(Alignment.TopCenter),
            )
        },
        content = content,
    )
}

/**
 * Indicator of [JaugeRefreshContainer]: a round surface with the action color progress, drawn entirely inside
 * the container. While pulling it fills a ring with the pull distance; while refreshing it spins.
 *
 * @param state pull state of the container.
 * @param isRefreshing true while a refresh is running.
 * @param modifier modifier of the indicator.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JaugeRefreshIndicator(
    state: PullToRefreshState,
    isRefreshing: Boolean,
    modifier: Modifier = Modifier,
) {
    val colors = JaugeTheme.colors
    Box(
        modifier = modifier
            .padding(top = INDICATOR_TOP_MARGIN)
            .size(INDICATOR_SIZE)
            .graphicsLayer {
                val fraction = if (isRefreshing) 1f else state.distanceFraction.coerceIn(0f, 1f)
                alpha = fraction
                scaleX = 0.4f + 0.6f * fraction
                scaleY = 0.4f + 0.6f * fraction
                translationY = (fraction - 1f) * INDICATOR_TRAVEL.toPx()
            }
            .shadow(elevation = 3.dp, shape = CircleShape)
            .background(colors.surface, CircleShape)
            .border(1.dp, colors.outline, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        if (isRefreshing) {
            CircularProgressIndicator(
                modifier = Modifier.size(SPINNER_SIZE),
                color = colors.action,
                strokeWidth = 2.5.dp,
                strokeCap = StrokeCap.Round,
            )
        } else {
            CircularProgressIndicator(
                progress = { state.distanceFraction.coerceIn(0f, 1f) },
                modifier = Modifier.size(SPINNER_SIZE),
                color = colors.action,
                trackColor = colors.outline,
                strokeWidth = 2.5.dp,
                strokeCap = StrokeCap.Round,
                gapSize = 0.dp,
            )
        }
    }
}

private val INDICATOR_SIZE = 40.dp
private val SPINNER_SIZE = 20.dp
private val INDICATOR_TOP_MARGIN = 12.dp
private val INDICATOR_TRAVEL = 16.dp
