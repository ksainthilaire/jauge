package io.jauge.feature.share.share

import androidx.compose.runtime.Composable
import io.jauge.core.designsystem.component.PlaceholderScreen
import io.jauge.feature.share.resources.Res
import io.jauge.feature.share.resources.share_title
import org.jetbrains.compose.resources.stringResource

/**
 * Share card and link https://jauge.io/a/{analysisId} (mockup 07).
 *
 * Placeholder: the screen is built in a later phase, following `docs/ARCHITECTURE.md`.
 *
 * @param onBack called when the user leaves the screen.
 */
@Composable
fun ShareScreen(
    onBack: () -> Unit,
) {
    PlaceholderScreen(title = stringResource(Res.string.share_title), onBack = onBack)
}
