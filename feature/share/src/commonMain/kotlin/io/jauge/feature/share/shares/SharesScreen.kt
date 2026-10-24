package io.jauge.feature.share.shares

import androidx.compose.runtime.Composable
import io.jauge.core.designsystem.component.PlaceholderScreen
import io.jauge.feature.share.resources.Res
import io.jauge.feature.share.resources.shares_title
import org.jetbrains.compose.resources.stringResource

/**
 * Every shared analysis (tab "Partages").
 *
 * Placeholder: the screen is built in a later phase, following `docs/ARCHITECTURE.md`.
 *
 */
@Composable
fun SharesScreen(
) {
    PlaceholderScreen(title = stringResource(Res.string.shares_title), onBack = null)
}
