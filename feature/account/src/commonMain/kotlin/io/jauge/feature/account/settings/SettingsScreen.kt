package io.jauge.feature.account.settings

import androidx.compose.runtime.Composable
import io.jauge.core.designsystem.component.PlaceholderScreen
import io.jauge.feature.account.resources.Res
import io.jauge.feature.account.resources.settings_title
import org.jetbrains.compose.resources.stringResource

/**
 * App settings (notifications, data, appearance).
 *
 * Placeholder: the screen is built in a later phase, following `docs/ARCHITECTURE.md`.
 *
 * @param onBack called when the user leaves the screen.
 */
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
) {
    PlaceholderScreen(title = stringResource(Res.string.settings_title), onBack = onBack)
}
