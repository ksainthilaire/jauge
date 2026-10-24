package io.jauge.feature.twins.alertspermission

import androidx.compose.runtime.Composable
import io.jauge.core.designsystem.component.PlaceholderScreen
import io.jauge.feature.twins.resources.Res
import io.jauge.feature.twins.resources.alerts_permission_title
import org.jetbrains.compose.resources.stringResource

/**
 * Asks for the notification permission (mockup 12).
 *
 * Placeholder: the screen is built in a later phase, following `docs/ARCHITECTURE.md`.
 *
 * @param onBack called when the user leaves the screen.
 */
@Composable
fun AlertsPermissionScreen(
    onBack: () -> Unit,
) {
    PlaceholderScreen(title = stringResource(Res.string.alerts_permission_title), onBack = onBack)
}
