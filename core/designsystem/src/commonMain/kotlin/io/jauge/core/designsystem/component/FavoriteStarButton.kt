package io.jauge.core.designsystem.component

import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import io.jauge.core.designsystem.icon.JaugeIcons
import io.jauge.core.designsystem.theme.JaugeTheme

/**
 * Star that adds an analysis to the favorites or removes it: filled and amber when it is a favorite, line star
 * otherwise. The touch target is 44 dp.
 *
 * @param isFavorite true when the analysis is a favorite.
 * @param onToggle called with the new value when the star is tapped.
 * @param contentDescription text read by screen readers ("Ajouter aux favoris" or "Retirer des favoris").
 * @param modifier modifier of the button.
 * @param stateDescription optional state read by screen readers ("En favori").
 */
@Composable
fun FavoriteStarButton(
    isFavorite: Boolean,
    onToggle: (Boolean) -> Unit,
    contentDescription: String,
    modifier: Modifier = Modifier,
    stateDescription: String? = null,
) {
    val colors = JaugeTheme.colors
    IconButton(
        onClick = { onToggle(!isFavorite) },
        modifier = modifier
            .size(44.dp)
            .semantics {
                role = Role.Button
                if (stateDescription != null) this.stateDescription = stateDescription
            },
    ) {
        Icon(
            imageVector = if (isFavorite) JaugeIcons.StarFilled else JaugeIcons.Star,
            contentDescription = contentDescription,
            tint = if (isFavorite) colors.disputed.accent else colors.textTertiary,
            modifier = Modifier.size(22.dp),
        )
    }
}
