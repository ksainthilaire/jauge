package io.jauge.feature.account.legalnotice

import androidx.compose.runtime.Composable
import io.jauge.core.designsystem.component.PlaceholderScreen
import io.jauge.feature.account.resources.Res
import io.jauge.feature.account.resources.legal_notice_title
import org.jetbrains.compose.resources.stringResource

/**
 * Legal notice of the publisher.
 *
 * Placeholder: the screen is built in a later phase, following `docs/ARCHITECTURE.md`.
 *
 * @param onBack called when the user leaves the screen.
 */
@Composable
fun LegalNoticeScreen(
    onBack: () -> Unit,
) {
    PlaceholderScreen(title = stringResource(Res.string.legal_notice_title), onBack = onBack)
}
