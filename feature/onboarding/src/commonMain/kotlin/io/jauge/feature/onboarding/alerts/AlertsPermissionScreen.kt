package io.jauge.feature.onboarding.alerts

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.jauge.core.common.platform.Platform
import io.jauge.core.common.platform.currentPlatform
import io.jauge.core.designsystem.component.JaugeButton
import io.jauge.core.designsystem.component.JaugeButtonStyle
import io.jauge.core.designsystem.component.JaugeLogo
import io.jauge.core.designsystem.icon.JaugeIcons
import io.jauge.core.designsystem.theme.JaugePalette
import io.jauge.core.designsystem.theme.JaugeTheme
import io.jauge.feature.onboarding.resources.Res
import io.jauge.feature.onboarding.resources.alerts_continue
import io.jauge.feature.onboarding.resources.alerts_example_calm_text
import io.jauge.feature.onboarding.resources.alerts_example_calm_title
import io.jauge.feature.onboarding.resources.alerts_example_moved_text
import io.jauge.feature.onboarding.resources.alerts_example_moved_title
import io.jauge.feature.onboarding.resources.alerts_example_time_now
import io.jauge.feature.onboarding.resources.alerts_example_time_recent
import io.jauge.feature.onboarding.resources.alerts_later
import io.jauge.feature.onboarding.resources.alerts_promise_frequency
import io.jauge.feature.onboarding.resources.alerts_promise_no_ads
import io.jauge.feature.onboarding.resources.alerts_promise_settings
import io.jauge.feature.onboarding.resources.alerts_system_prompt_android
import io.jauge.feature.onboarding.resources.alerts_system_prompt_ios
import io.jauge.feature.onboarding.resources.alerts_text
import io.jauge.feature.onboarding.resources.alerts_title
import io.jauge.feature.onboarding.resources.app_name
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

/**
 * Screen shown before the system notification request (mockup 12): explains what Jauge sends, then "Continuer" opens
 * the system request and "Plus tard" goes on without it.
 *
 * @param onDone called once the user answered, whatever the answer.
 */
@Composable
fun AlertsPermissionScreen(onDone: () -> Unit) {
    val viewModel: AlertsPermissionViewModel = koinViewModel()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val requestPermission = rememberNotificationPermissionRequest { granted -> viewModel.onSystemAnswer(granted, onDone) }
    AlertsPermissionContent(
        isWaitingForSystem = state.isWaitingForSystem,
        onContinue = {
            viewModel.onSystemRequestShown()
            requestPermission()
        },
        onLater = { viewModel.later(onDone) },
    )
}

/**
 * Stateless content of the alerts screen, also used by the screenshot tests.
 *
 * @param isWaitingForSystem true while the system request is shown.
 * @param onContinue "Continuer".
 * @param onLater "Plus tard".
 * @param modifier modifier of the screen.
 * @param platform platform whose system request is announced.
 */
@Composable
fun AlertsPermissionContent(
    isWaitingForSystem: Boolean,
    onContinue: () -> Unit,
    onLater: () -> Unit,
    modifier: Modifier = Modifier,
    platform: Platform = currentPlatform,
) {
    JaugeTheme(darkTheme = false) {
        val colors = JaugeTheme.colors
        val typography = JaugeTheme.typography
        val gutter = JaugeTheme.spacing.screenGutter
        Column(modifier = modifier.fillMaxSize().background(colors.backgroundAlternative).navigationBarsPadding()) {
            Row(
                modifier = Modifier.fillMaxWidth().height(48.dp).padding(horizontal = gutter),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(Res.string.alerts_later),
                    style = typography.body.copy(fontWeight = FontWeight.SemiBold),
                    color = colors.textSecondary,
                    modifier = Modifier.clip(JaugeTheme.radii.pillShape).clickable(role = Role.Button, onClick = onLater).padding(8.dp),
                )
            }
            Column(modifier = Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                NotificationExamples(modifier = Modifier.padding(top = 16.dp))
                Column(modifier = Modifier.padding(horizontal = gutter)) {
                    Text(
                        text = stringResource(Res.string.alerts_title),
                        style = typography.headline.copy(fontSize = 31.sp),
                        color = colors.textPrimary,
                    )
                    Text(
                        text = stringResource(Res.string.alerts_text),
                        style = typography.body.copy(fontSize = 15.5.sp, fontWeight = FontWeight.Normal, lineHeight = 22.sp),
                        color = colors.inverseSurfaceSecondary,
                        modifier = Modifier.padding(top = 12.dp),
                    )
                    Column(modifier = Modifier.padding(top = 18.dp), verticalArrangement = Arrangement.spacedBy(11.dp)) {
                        Promise(stringResource(Res.string.alerts_promise_frequency))
                        Promise(stringResource(Res.string.alerts_promise_no_ads))
                        Promise(stringResource(Res.string.alerts_promise_settings))
                    }
                }
            }
            Column(modifier = Modifier.padding(horizontal = gutter).padding(top = 12.dp, bottom = 24.dp)) {
                JaugeButton(
                    text = stringResource(Res.string.alerts_continue),
                    onClick = onContinue,
                    style = JaugeButtonStyle.NEUTRAL,
                    isLoading = isWaitingForSystem,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    text = stringResource(
                        if (platform == Platform.IOS) Res.string.alerts_system_prompt_ios else Res.string.alerts_system_prompt_android,
                    ),
                    style = typography.caption.copy(fontSize = 12.5.sp, lineHeight = 17.5.sp),
                    color = colors.textSecondary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                )
            }
        }
    }
}

@Composable
private fun Promise(text: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(JaugeIcons.Check, contentDescription = null, tint = JaugeTheme.colors.calm.content, modifier = Modifier.size(18.dp))
        Text(
            text = text,
            style = JaugeTheme.typography.body.copy(fontSize = 14.5.sp),
            color = JaugeTheme.colors.inverseSurfaceSecondary,
        )
    }
}

@Composable
private fun NotificationExamples(modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxWidth().height(320.dp)) {
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .offset(y = 10.dp)
                .size(250.dp)
                .background(
                    Brush.radialGradient(
                        0f to JaugePalette.Mint.copy(alpha = 0.30f),
                        0.68f to JaugePalette.Mint.copy(alpha = 0f),
                    ),
                    CircleShape,
                ),
        )
        NotificationCard(
            time = stringResource(Res.string.alerts_example_time_recent),
            title = stringResource(Res.string.alerts_example_moved_title),
            text = stringResource(Res.string.alerts_example_moved_text),
            modifier = Modifier.offset(y = 44.dp).scale(0.94f).alpha(0.75f),
        )
        NotificationCard(
            time = stringResource(Res.string.alerts_example_time_now),
            title = stringResource(Res.string.alerts_example_calm_title),
            text = stringResource(Res.string.alerts_example_calm_text),
            modifier = Modifier.offset(y = 136.dp),
        )
    }
}

@Composable
private fun NotificationCard(time: String, title: String, text: String, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(22.dp)
    val typography = JaugeTheme.typography
    val colors = JaugeTheme.colors
    Row(
        modifier = modifier
            .padding(horizontal = JaugeTheme.spacing.screenGutter)
            .fillMaxWidth()
            .shadow(elevation = 14.dp, shape = shape, ambientColor = JaugePalette.Ink.copy(alpha = 0.16f), spotColor = JaugePalette.Ink.copy(alpha = 0.16f))
            .background(JaugePalette.OffWhite.copy(alpha = 0.92f), shape)
            .border(1.dp, JaugePalette.Line.copy(alpha = 0.8f), shape)
            .padding(horizontal = 14.dp, vertical = 13.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        JaugeLogo(modifier = Modifier.size(38.dp).clip(RoundedCornerShape(9.dp)))
        Column(modifier = Modifier.weight(1f)) {
            Row(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = stringResource(Res.string.app_name).uppercase(),
                    style = typography.caption.copy(fontWeight = FontWeight.SemiBold, letterSpacing = 0.05.em),
                    color = colors.textSecondary,
                    modifier = Modifier.weight(1f),
                )
                Text(text = time, style = typography.caption, color = colors.textSecondary)
            }
            Text(
                text = title,
                style = typography.bodyStrong.copy(fontSize = 14.5.sp),
                color = colors.textPrimary,
                modifier = Modifier.padding(top = 2.dp),
            )
            Text(
                text = text,
                style = typography.bodySmall.copy(lineHeight = 18.sp),
                color = colors.inverseSurfaceSecondary,
                modifier = Modifier.padding(top = 1.dp),
            )
        }
    }
}
