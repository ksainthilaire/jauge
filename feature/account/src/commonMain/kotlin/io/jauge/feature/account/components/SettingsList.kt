package io.jauge.feature.account.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import io.jauge.core.designsystem.component.JaugeCard
import io.jauge.core.designsystem.icon.JaugeIcons
import io.jauge.core.designsystem.theme.JaugeTheme

/**
 * One row of a settings list (`.liste` in mockup 13).
 *
 * @property icon line icon on the left.
 * @property label text of the row.
 * @property value optional value on the right ("Activées").
 * @property isDestructive true for "Supprimer mon compte" (coral).
 * @property onClick called on tap.
 */
class SettingsRowItem(
    val icon: ImageVector,
    val label: String,
    val value: String? = null,
    val isDestructive: Boolean = false,
    val onClick: () -> Unit,
)

/**
 * Small uppercase title above a settings card (`.grp`).
 *
 * @param text the title.
 * @param modifier modifier of the text.
 */
@Composable
fun SettingsGroupTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text.uppercase(),
        style = JaugeTheme.typography.label.copy(fontSize = 12.5.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.08.em),
        color = JaugeTheme.colors.textSecondary,
        modifier = modifier.padding(start = 4.dp, top = 16.dp, bottom = 8.dp),
    )
}

/**
 * Card of settings rows separated by thin lines.
 *
 * @param rows rows to show.
 * @param modifier modifier of the card.
 */
@Composable
fun SettingsListCard(rows: List<SettingsRowItem>, modifier: Modifier = Modifier) {
    val colors = JaugeTheme.colors
    JaugeCard(modifier = modifier.fillMaxWidth(), contentPadding = PaddingValues(0.dp)) {
        rows.forEachIndexed { index, row ->
            val tint: Color = if (row.isDestructive) colors.crowded.content else colors.textPrimary
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 46.dp)
                    .clickable(role = Role.Button, onClick = row.onClick)
                    .padding(horizontal = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(row.icon, contentDescription = null, tint = tint, modifier = Modifier.size(19.dp))
                Text(text = row.label, style = JaugeTheme.typography.body, color = tint, modifier = Modifier.weight(1f).padding(vertical = 12.dp))
                row.value?.let { Text(text = it, style = JaugeTheme.typography.bodySmall, color = colors.textSecondary) }
                Icon(JaugeIcons.ChevronRight, contentDescription = null, tint = colors.textTertiary, modifier = Modifier.size(16.dp))
            }
            if (index < rows.lastIndex) HorizontalDivider(color = colors.outline)
        }
    }
}
