package io.jauge.feature.account.about

import androidx.compose.runtime.Composable
import io.jauge.core.designsystem.component.PlaceholderScreen
import io.jauge.feature.account.resources.Res
import io.jauge.feature.account.resources.about_title
import org.jetbrains.compose.resources.stringResource

/**
 * About the app, version and licenses.
 *
 * Placeholder: the screen is built in a later phase, following `docs/ARCHITECTURE.md`.
 *
 * @param onBack called when the user leaves the screen.
 */
@Composable
fun AboutScreen(
    onBack: () -> Unit,
) {
    PlaceholderScreen(title = stringResource(Res.string.about_title), onBack = onBack)
}
