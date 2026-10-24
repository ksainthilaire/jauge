package io.jauge.core.designsystem.component

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.jauge.core.designsystem.resources.Res
import io.jauge.core.designsystem.resources.loading
import io.jauge.core.designsystem.theme.JaugeTheme
import org.jetbrains.compose.resources.stringResource

/**
 * Paints a loading placeholder with a slow moving highlight.
 *
 * @param shape shape of the placeholder.
 */
fun Modifier.skeleton(shape: Shape = RoundedCornerShape(8.dp)): Modifier = composed {
    val colors = JaugeTheme.colors
    val transition = rememberInfiniteTransition(label = "skeleton")
    val progress by transition.animateFloat(
        initialValue = -1f,
        targetValue = 2f,
        animationSpec = infiniteRepeatable(tween(durationMillis = 1_300, easing = LinearEasing), RepeatMode.Restart),
        label = "skeletonProgress",
    )
    background(
        brush = Brush.linearGradient(
            colors = listOf(colors.skeleton, colors.skeletonHighlight, colors.skeleton),
            start = androidx.compose.ui.geometry.Offset(progress * 600f - 300f, 0f),
            end = androidx.compose.ui.geometry.Offset(progress * 600f + 300f, 0f),
        ),
        shape = shape,
    )
}

/**
 * Loading placeholder of one list row (mini gauge, two lines of text and a pill).
 *
 * @param modifier modifier of the row.
 */
@Composable
fun SkeletonListRow(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(44.dp, 30.dp).skeleton())
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Box(Modifier.fillMaxWidth(0.7f).height(14.dp).skeleton())
            Box(Modifier.fillMaxWidth(0.5f).height(11.dp).skeleton())
        }
        Box(Modifier.width(56.dp).height(26.dp).skeleton(JaugeTheme.radii.pillShape))
    }
}

/**
 * Loading placeholder of a list in a card.
 *
 * @param rowCount number of placeholder rows.
 * @param modifier modifier of the card.
 */
@Composable
fun SkeletonList(rowCount: Int = 3, modifier: Modifier = Modifier) {
    val loadingDescription = stringResource(Res.string.loading)
    JaugeCard(
        modifier = modifier.semantics { contentDescription = loadingDescription },
        contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
    ) {
        repeat(rowCount) { SkeletonListRow() }
    }
}

/**
 * Loading placeholder of a content card (title and a few lines).
 *
 * @param modifier modifier of the card.
 * @param height height of the main block.
 */
@Composable
fun SkeletonCard(modifier: Modifier = Modifier, height: androidx.compose.ui.unit.Dp = 120.dp) {
    val loadingDescription = stringResource(Res.string.loading)
    JaugeCard(modifier = modifier.semantics { contentDescription = loadingDescription }) {
        Box(Modifier.fillMaxWidth(0.5f).height(16.dp).skeleton())
        Box(Modifier.fillMaxWidth().height(height).padding(top = 12.dp).skeleton(RoundedCornerShape(14.dp)))
    }
}
