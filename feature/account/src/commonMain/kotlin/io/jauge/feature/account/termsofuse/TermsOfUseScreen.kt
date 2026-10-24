package io.jauge.feature.account.termsofuse

import androidx.compose.runtime.Composable
import io.jauge.core.designsystem.component.PlaceholderScreen
import io.jauge.feature.account.resources.Res
import io.jauge.feature.account.resources.terms_of_use_title
import org.jetbrains.compose.resources.stringResource

/**
 * Terms of use.
 *
 * Placeholder: the screen is built in a later phase, following `docs/ARCHITECTURE.md`.
 *
 * @param onBack called when the user leaves the screen.
 */
@Composable
fun TermsOfUseScreen(
    onBack: () -> Unit,
) {
    PlaceholderScreen(title = stringResource(Res.string.terms_of_use_title), onBack = onBack)
}
