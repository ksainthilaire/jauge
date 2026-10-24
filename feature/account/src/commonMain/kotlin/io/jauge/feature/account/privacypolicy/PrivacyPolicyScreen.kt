package io.jauge.feature.account.privacypolicy

import androidx.compose.runtime.Composable
import io.jauge.core.designsystem.component.PlaceholderScreen
import io.jauge.feature.account.resources.Res
import io.jauge.feature.account.resources.privacy_policy_title
import org.jetbrains.compose.resources.stringResource

/**
 * Privacy policy.
 *
 * Placeholder: the screen is built in a later phase, following `docs/ARCHITECTURE.md`.
 *
 * @param onBack called when the user leaves the screen.
 */
@Composable
fun PrivacyPolicyScreen(
    onBack: () -> Unit,
) {
    PlaceholderScreen(title = stringResource(Res.string.privacy_policy_title), onBack = onBack)
}
