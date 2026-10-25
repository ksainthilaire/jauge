package io.jauge.feature.analysis.home

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import io.jauge.core.designsystem.component.ComposerActionButton
import io.jauge.core.designsystem.component.ComposerStatus
import io.jauge.core.designsystem.component.ComposerStatusTone
import io.jauge.core.designsystem.component.ComposerToolButton
import io.jauge.core.designsystem.component.JaugeComposer
import io.jauge.core.designsystem.icon.JaugeIcons
import io.jauge.core.designsystem.theme.JaugeTheme
import io.jauge.core.ui.error.toFriendlyMessage
import io.jauge.feature.analysis.resources.Res
import io.jauge.feature.analysis.resources.home_analyze
import io.jauge.feature.analysis.resources.home_clear_link
import io.jauge.feature.analysis.resources.home_clipboard_empty
import io.jauge.feature.analysis.resources.home_image_unreadable
import io.jauge.feature.analysis.resources.home_input_help
import io.jauge.feature.analysis.resources.home_link_field
import io.jauge.feature.analysis.resources.home_link_incomplete
import io.jauge.feature.analysis.resources.home_link_job_board
import io.jauge.feature.analysis.resources.home_link_placeholder
import io.jauge.feature.analysis.resources.home_link_site
import io.jauge.feature.analysis.resources.home_paste
import io.jauge.feature.analysis.resources.home_paste_copied_link
import io.jauge.feature.analysis.resources.home_photo
import io.jauge.feature.analysis.resources.home_screenshot
import org.jetbrains.compose.resources.stringResource

/**
 * The three ways to give an offer, gathered in one composer (docs/design/home-input.md): the link field, "Capture"
 * and "Photo" in its toolbar, and one action that is "Coller" while the field is empty and "Jauger" once a link is
 * recognized.
 *
 * @param state state of the home screen.
 * @param onLinkChange link typed.
 * @param onClearLink cross of the field.
 * @param onPaste "Coller": reads the clipboard (only on this tap).
 * @param onAnalyze "Jauger".
 * @param onPickScreenshot "Capture".
 * @param onTakePhoto "Photo".
 * @param modifier modifier of the block.
 */
@Composable
internal fun OfferInput(
    state: HomeUiState,
    onLinkChange: (String) -> Unit,
    onClearLink: () -> Unit,
    onPaste: () -> Unit,
    onAnalyze: () -> Unit,
    onPickScreenshot: () -> Unit,
    onTakePhoto: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val linkStatus = state.linkStatus
    val status: (@Composable () -> Unit)? = when {
        state.linkError != null -> {
            { ComposerStatus(stringResource(state.linkError.toFriendlyMessage().message), ComposerStatusTone.ERROR) }
        }
        state.isClipboardEmpty -> {
            { ComposerStatus(stringResource(Res.string.home_clipboard_empty), ComposerStatusTone.HINT) }
        }
        linkStatus is OfferLinkStatus.Recognized -> {
            {
                val text = linkStatus.jobBoardName?.let { stringResource(Res.string.home_link_job_board, it) }
                    ?: stringResource(Res.string.home_link_site, linkStatus.host)
                ComposerStatus(text, ComposerStatusTone.SUCCESS)
            }
        }
        linkStatus is OfferLinkStatus.Incomplete -> {
            { ComposerStatus(stringResource(Res.string.home_link_incomplete), ComposerStatusTone.HINT) }
        }
        else -> null
    }
    Column(modifier = modifier) {
        JaugeComposer(
            value = state.link,
            onValueChange = onLinkChange,
            placeholder = stringResource(Res.string.home_link_placeholder),
            fieldLabel = stringResource(Res.string.home_link_field),
            leadingIcon = JaugeIcons.Link,
            clearLabel = stringResource(Res.string.home_clear_link),
            onClear = onClearLink,
            isError = state.linkError != null,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go),
            keyboardActions = KeyboardActions(onGo = { onAnalyze() }),
            status = status,
            tools = {
                ComposerToolButton(text = stringResource(Res.string.home_screenshot), icon = JaugeIcons.Image, onClick = onPickScreenshot)
                ComposerToolButton(text = stringResource(Res.string.home_photo), icon = JaugeIcons.Camera, onClick = onTakePhoto)
            },
            action = {
                if (linkStatus is OfferLinkStatus.Empty) {
                    ComposerActionButton(
                        text = stringResource(if (state.clipboardMayHoldLink) Res.string.home_paste_copied_link else Res.string.home_paste),
                        onClick = onPaste,
                        icon = JaugeIcons.Paste,
                    )
                } else {
                    ComposerActionButton(
                        text = stringResource(Res.string.home_analyze),
                        onClick = onAnalyze,
                        icon = JaugeIcons.ArrowRight,
                        emphasized = true,
                        enabled = state.canAnalyze,
                    )
                }
            },
        )
        val colors = JaugeTheme.colors
        when {
            state.isImageUnreadable -> Text(
                text = stringResource(Res.string.home_image_unreadable),
                style = JaugeTheme.typography.bodySmall,
                color = colors.crowded.content,
                modifier = Modifier.padding(top = 10.dp, start = 4.dp),
            )
            linkStatus is OfferLinkStatus.Empty -> Text(
                text = stringResource(Res.string.home_input_help),
                style = JaugeTheme.typography.bodySmall,
                color = colors.textSecondary,
                modifier = Modifier.padding(top = 10.dp, start = 4.dp),
            )
        }
    }
}
