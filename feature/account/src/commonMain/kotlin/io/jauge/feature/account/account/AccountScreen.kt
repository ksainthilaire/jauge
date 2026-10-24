package io.jauge.feature.account.account

import androidx.compose.runtime.Composable
import io.jauge.core.designsystem.component.PlaceholderScreen
import io.jauge.feature.account.resources.Res
import io.jauge.feature.account.resources.account_title
import org.jetbrains.compose.resources.stringResource

/**
 * Sign in and consents (mockup 11).
 *
 * Placeholder: the screen is built in a later phase, following `docs/ARCHITECTURE.md`.
 *
 * @param onBack called when the user leaves the screen.
 */
@Composable
fun AccountScreen(
    onBack: () -> Unit,
) {
    PlaceholderScreen(title = stringResource(Res.string.account_title), onBack = onBack)
}
