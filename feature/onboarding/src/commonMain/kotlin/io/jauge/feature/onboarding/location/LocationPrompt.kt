package io.jauge.feature.onboarding.location

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.jauge.core.designsystem.component.JaugeButton
import io.jauge.core.designsystem.component.JaugeButtonStyle
import io.jauge.core.designsystem.component.JaugeCard
import io.jauge.core.designsystem.icon.JaugeIcons
import io.jauge.core.designsystem.theme.JaugeTheme
import io.jauge.feature.onboarding.resources.Res
import io.jauge.feature.onboarding.resources.location_prompt_allow
import io.jauge.feature.onboarding.resources.location_prompt_default
import io.jauge.feature.onboarding.resources.location_prompt_later
import io.jauge.feature.onboarding.resources.location_prompt_message
import io.jauge.feature.onboarding.resources.location_prompt_privacy
import io.jauge.feature.onboarding.resources.location_prompt_title
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

/**
 * Shows the location explanation once, above the app, after the first launch. Put it in the app shell once the first
 * launch is over; it decides by itself whether to show.
 */
@Composable
fun LocationPromptHost() {
    val viewModel: LocationPromptViewModel = koinViewModel()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    if (state.isVisible) {
        Dialog(
            onDismissRequest = viewModel::decline,
            properties = DialogProperties(dismissOnClickOutside = false, usePlatformDefaultWidth = false),
        ) {
            LocationPromptCard(onAllow = viewModel::allow, onLater = viewModel::decline)
        }
    }
}

/**
 * Explanation of the app before the system location request: why the city is useful, what stays on the phone and
 * what happens without it.
 *
 * @param onAllow "Autoriser la localisation".
 * @param onLater "Plus tard".
 * @param modifier modifier of the card.
 */
@Composable
fun LocationPromptCard(onAllow: () -> Unit, onLater: () -> Unit, modifier: Modifier = Modifier) {
    val colors = JaugeTheme.colors
    val typography = JaugeTheme.typography
    JaugeCard(modifier = modifier.fillMaxWidth().padding(horizontal = JaugeTheme.spacing.screenGutter)) {
        Box(
            modifier = Modifier.size(48.dp).background(colors.calm.container, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(JaugeIcons.Pin, contentDescription = null, tint = colors.calm.content, modifier = Modifier.size(24.dp))
        }
        Text(
            text = stringResource(Res.string.location_prompt_title),
            style = typography.title,
            color = colors.textPrimary,
            modifier = Modifier.padding(top = 14.dp),
        )
        Text(
            text = stringResource(Res.string.location_prompt_message),
            style = typography.body,
            color = colors.textSecondary,
            modifier = Modifier.padding(top = 8.dp),
        )
        Row(modifier = Modifier.padding(top = 12.dp), verticalAlignment = Alignment.Top) {
            Icon(
                JaugeIcons.Shield,
                contentDescription = null,
                tint = colors.calm.content,
                modifier = Modifier.padding(top = 2.dp, end = 8.dp).size(16.dp),
            )
            Text(text = stringResource(Res.string.location_prompt_privacy), style = typography.bodySmall, color = colors.textSecondary)
        }
        Column(modifier = Modifier.padding(top = 18.dp)) {
            JaugeButton(text = stringResource(Res.string.location_prompt_allow), onClick = onAllow, modifier = Modifier.fillMaxWidth())
            JaugeButton(
                text = stringResource(Res.string.location_prompt_later),
                onClick = onLater,
                style = JaugeButtonStyle.OUTLINED,
                modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
            )
        }
        Text(
            text = stringResource(Res.string.location_prompt_default),
            style = typography.bodySmall,
            color = colors.textTertiary,
            modifier = Modifier.padding(top = 12.dp),
        )
    }
}
