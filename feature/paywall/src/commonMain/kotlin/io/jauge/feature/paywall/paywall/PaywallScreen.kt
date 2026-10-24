package io.jauge.feature.paywall.paywall

import androidx.compose.runtime.Composable
import io.jauge.core.designsystem.component.PlaceholderScreen
import io.jauge.feature.paywall.resources.Res
import io.jauge.feature.paywall.resources.paywall_title
import org.jetbrains.compose.resources.stringResource

/**
 * Monthly subscription with a 3 day free trial (mockup 08, single offer).
 *
 * Placeholder: the screen is built in a later phase, following `docs/ARCHITECTURE.md`.
 *
 * @param onBack called when the user leaves the screen.
 */
@Composable
fun PaywallScreen(
    onBack: () -> Unit,
) {
    PlaceholderScreen(title = stringResource(Res.string.paywall_title), onBack = onBack)
}
