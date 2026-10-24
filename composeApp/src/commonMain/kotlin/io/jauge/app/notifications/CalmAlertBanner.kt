package io.jauge.app.notifications

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import io.jauge.app.resources.Res
import io.jauge.app.resources.calm_alert_close
import io.jauge.core.designsystem.icon.JaugeIcons
import io.jauge.core.designsystem.theme.JaugeTheme
import io.jauge.core.domain.model.JaugeNotification
import io.jauge.core.domain.usecase.MarkNotificationAsReadUseCase
import io.jauge.core.domain.usecase.ObserveIncomingCalmAlertsUseCase
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

/**
 * Shows the calm alerts received by Supabase Realtime while the app is open, one at a time, for a few seconds.
 *
 * @param observeIncomingCalmAlerts new calm alerts.
 * @param markAsRead marks an alert as read when the user opens or closes it.
 */
class CalmAlertBannerViewModel(
    observeIncomingCalmAlerts: ObserveIncomingCalmAlertsUseCase,
    private val markAsRead: MarkNotificationAsReadUseCase,
) : ViewModel() {

    private val current = MutableStateFlow<JaugeNotification?>(null)
    private var hideJob: Job? = null

    /** The alert on screen, null when none. */
    val alert: StateFlow<JaugeNotification?> = current.asStateFlow()

    init {
        viewModelScope.launch {
            observeIncomingCalmAlerts().collect { notification ->
                current.value = notification
                hideJob?.cancel()
                hideJob = launch {
                    delay(VISIBLE_MILLISECONDS)
                    current.value = null
                }
            }
        }
    }

    /** The user opened or closed the banner. */
    fun dismiss() {
        val shown = current.value ?: return
        current.value = null
        hideJob?.cancel()
        viewModelScope.launch { markAsRead(shown.id) }
    }

    private companion object {
        const val VISIBLE_MILLISECONDS = 6_000L
    }
}

/**
 * Banner of a calm alert at the top of the app (mint, like the calm level).
 *
 * @param onOpen opens the twin offers of the analysis of the alert.
 * @param modifier modifier of the banner.
 */
@Composable
fun CalmAlertBannerHost(onOpen: (analysisId: String?) -> Unit, modifier: Modifier = Modifier) {
    val viewModel: CalmAlertBannerViewModel = koinViewModel()
    val alert by viewModel.alert.collectAsStateWithLifecycle()
    AnimatedVisibility(
        visible = alert != null,
        enter = slideInVertically { -it } + fadeIn(),
        exit = slideOutVertically { -it } + fadeOut(),
        modifier = modifier,
    ) {
        val shown = alert ?: return@AnimatedVisibility
        CalmAlertBanner(
            notification = shown,
            onOpen = {
                viewModel.dismiss()
                onOpen(shown.analysisId)
            },
            onClose = viewModel::dismiss,
        )
    }
}

@Composable
private fun CalmAlertBanner(notification: JaugeNotification, onOpen: () -> Unit, onClose: () -> Unit) {
    val colors = JaugeTheme.colors
    val shape = JaugeTheme.radii.cardShape
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = JaugeTheme.spacing.screenGutter, vertical = JaugeTheme.spacing.small)
            .background(colors.surface, shape)
            .border(1.5.dp, colors.calm.accent, shape)
            .clickable(onClick = onOpen)
            .padding(start = JaugeTheme.spacing.extraLarge, top = JaugeTheme.spacing.large, bottom = JaugeTheme.spacing.large)
            .semantics { liveRegion = LiveRegionMode.Polite },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(JaugeTheme.spacing.medium),
    ) {
        Box(Modifier.size(36.dp).background(colors.calm.container, CircleShape), contentAlignment = Alignment.Center) {
            Icon(JaugeIcons.Bell, contentDescription = null, tint = colors.calm.content, modifier = Modifier.size(18.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(notification.title, style = JaugeTheme.typography.bodyStrong, color = colors.textPrimary, maxLines = 2)
            if (notification.body.isNotBlank()) {
                Text(notification.body, style = JaugeTheme.typography.bodySmall, color = colors.textSecondary, maxLines = 3)
            }
        }
        IconButton(onClick = onClose) {
            Icon(JaugeIcons.Close, contentDescription = stringResource(Res.string.calm_alert_close), tint = colors.textSecondary, modifier = Modifier.size(18.dp))
        }
    }
}
