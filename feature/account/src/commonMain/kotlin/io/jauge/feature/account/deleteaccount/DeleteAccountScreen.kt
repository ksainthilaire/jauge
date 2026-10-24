package io.jauge.feature.account.deleteaccount

import androidx.compose.runtime.Composable
import io.jauge.core.designsystem.component.PlaceholderScreen
import io.jauge.feature.account.resources.Res
import io.jauge.feature.account.resources.delete_account_title
import org.jetbrains.compose.resources.stringResource

/**
 * Account deletion, required by the stores.
 *
 * Placeholder: the screen is built in a later phase, following `docs/ARCHITECTURE.md`.
 *
 * @param onBack called when the user leaves the screen.
 */
@Composable
fun DeleteAccountScreen(
    onBack: () -> Unit,
) {
    PlaceholderScreen(title = stringResource(Res.string.delete_account_title), onBack = onBack)
}
