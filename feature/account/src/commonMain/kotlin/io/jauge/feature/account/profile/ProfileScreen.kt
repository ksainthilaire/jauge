package io.jauge.feature.account.profile

import androidx.compose.runtime.Composable
import io.jauge.core.designsystem.component.PlaceholderScreen
import io.jauge.feature.account.resources.Res
import io.jauge.feature.account.resources.profile_title
import org.jetbrains.compose.resources.stringResource

/**
 * Profile and subscription (mockup 13, tab "Profil").
 *
 * Placeholder: the screen is built in a later phase, following `docs/ARCHITECTURE.md`.
 *
 */
@Composable
fun ProfileScreen(
) {
    PlaceholderScreen(title = stringResource(Res.string.profile_title), onBack = null)
}
