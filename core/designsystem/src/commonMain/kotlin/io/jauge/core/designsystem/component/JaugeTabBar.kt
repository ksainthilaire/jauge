package io.jauge.core.designsystem.component

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.jauge.core.designsystem.theme.JaugeTheme

/**
 * One item of [JaugeTabBar].
 *
 * @property label label under the icon.
 * @property icon line icon.
 * @property selected true for the current tab.
 * @property onClick called on tap.
 * @property onLongClick optional long press action (used by the hidden debug switch).
 */
data class JaugeTabItem(
    val label: String,
    val icon: ImageVector,
    val selected: Boolean,
    val onClick: () -> Unit,
    val onLongClick: (() -> Unit)? = null,
)

/**
 * Bottom tab bar (`.tabbar`): translucent off white, top separator, icon and label per tab.
 *
 * @param items the tabs, in order.
 * @param modifier modifier of the bar.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun JaugeTabBar(
    items: List<JaugeTabItem>,
    modifier: Modifier = Modifier,
) {
    val colors = JaugeTheme.colors
    Column(modifier = modifier.fillMaxWidth().background(colors.surface.copy(alpha = 0.94f))) {
        HorizontalDivider(color = colors.outline, thickness = 1.dp)
        Row(
            modifier = Modifier.fillMaxWidth().navigationBarsPadding().height(64.dp).padding(top = 10.dp),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.Top,
        ) {
            items.forEach { item ->
                val contentColor = if (item.selected) colors.textPrimary else colors.textSecondary
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .semantics { selected = item.selected }
                        .combinedClickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            role = Role.Tab,
                            onClick = item.onClick,
                            onLongClick = item.onLongClick,
                        ),
                    contentAlignment = Alignment.TopCenter,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Icon(item.icon, contentDescription = null, tint = contentColor, modifier = Modifier.size(25.dp))
                        Text(text = item.label, style = JaugeTheme.typography.tab, color = contentColor)
                    }
                }
            }
        }
    }
}
