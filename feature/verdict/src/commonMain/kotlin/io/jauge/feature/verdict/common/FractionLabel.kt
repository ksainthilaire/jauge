package io.jauge.feature.verdict.common

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout

/** How a [FractionLabel] is anchored on its position. */
enum class LabelAnchor {
    /** The label starts at the position. */
    START,

    /** The label is centered on the position. */
    CENTER,

    /** The label ends at the position. */
    END,
}

/**
 * Places its content horizontally at a fraction of the available width, kept inside the bounds (labels of the
 * salary scale and of the estimate range).
 *
 * @param fraction position between 0 and 1.
 * @param anchor which point of the content sits on the position.
 * @param modifier modifier of the full width row.
 * @param content the label.
 */
@Composable
fun FractionLabel(
    fraction: Float,
    anchor: LabelAnchor,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Layout(content = content, modifier = modifier.fillMaxWidth()) { measurables, constraints ->
        val placeable = measurables.first().measure(constraints.copy(minWidth = 0))
        val width = constraints.maxWidth
        val position = (fraction.coerceIn(0f, 1f) * width).toInt()
        val wanted = when (anchor) {
            LabelAnchor.START -> position
            LabelAnchor.CENTER -> position - placeable.width / 2
            LabelAnchor.END -> position - placeable.width
        }
        val x = wanted.coerceIn(0, (width - placeable.width).coerceAtLeast(0))
        layout(width, placeable.height) { placeable.place(x, 0) }
    }
}
