package io.jauge.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.jauge.core.designsystem.theme.JaugeTheme

/**
 * Round 40 dp icon button of the top bar (`.rond`).
 *
 * @param icon the icon.
 * @param contentDescription spoken description, from `strings.xml`.
 * @param onClick called on tap.
 * @param modifier modifier of the button.
 */
@Composable
fun RoundIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = JaugeTheme.colors
    IconButton(
        onClick = onClick,
        modifier = modifier
            .size(40.dp)
            .background(colors.surface, CircleShape)
            .border(1.dp, colors.outline, CircleShape),
    ) {
        Icon(icon, contentDescription = contentDescription, tint = colors.textPrimary, modifier = Modifier.size(20.dp))
    }
}

/**
 * Top bar of the app (`.nav`): optional round button on each side and a centered title.
 *
 * @param title centered title, or null.
 * @param modifier modifier of the bar.
 * @param navigationIcon optional left button, usually a [RoundIconButton] with back or close.
 * @param action optional right button.
 */
@Composable
fun JaugeTopBar(
    title: String?,
    modifier: Modifier = Modifier,
    navigationIcon: (@Composable () -> Unit)? = null,
    action: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = modifier.fillMaxWidth().height(48.dp).padding(horizontal = JaugeTheme.spacing.screenGutter),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(40.dp)) { navigationIcon?.invoke() }
        Text(
            text = title.orEmpty(),
            style = JaugeTheme.typography.bodyStrong,
            color = JaugeTheme.colors.textPrimary,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Box(Modifier.size(40.dp)) { action?.invoke() }
    }
}

/**
 * Standard screen layout: background, top bar, content and an optional bottom area for main actions.
 * The status bar inset is handled by the app shell; this scaffold handles the navigation bar inset of [bottomBar].
 *
 * @param modifier modifier of the screen.
 * @param title centered title of the top bar, or null for no title.
 * @param navigationIcon optional left button of the top bar.
 * @param action optional right button of the top bar.
 * @param showTopBar false to hide the top bar (tab roots with their own header).
 * @param snackbarHostState optional host for short confirmations.
 * @param bottomBar optional bottom area (main buttons), drawn above the navigation bar.
 * @param content the screen content; make it scrollable and wrap it in [JaugeRefreshContainer] when it loads data.
 */
@Composable
fun JaugeScreenScaffold(
    modifier: Modifier = Modifier,
    title: String? = null,
    navigationIcon: (@Composable () -> Unit)? = null,
    action: (@Composable () -> Unit)? = null,
    showTopBar: Boolean = true,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
    bottomBar: (@Composable ColumnScope.() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Box(modifier = modifier.fillMaxSize().background(JaugeTheme.colors.background)) {
        // imePadding keeps the bottom bar (main button) above the keyboard; the navigation bar padding below then adds nothing.
        Column(modifier = Modifier.fillMaxSize().imePadding()) {
            if (showTopBar) {
                JaugeTopBar(title = title, navigationIcon = navigationIcon, action = action)
            }
            Column(modifier = Modifier.weight(1f).fillMaxWidth(), content = content)
            if (bottomBar != null) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = JaugeTheme.spacing.screenGutter)
                        .padding(top = JaugeTheme.spacing.medium, bottom = JaugeTheme.spacing.extraLarge),
                    content = bottomBar,
                )
            }
        }
        SnackbarHost(hostState = snackbarHostState, modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 96.dp))
    }
}
