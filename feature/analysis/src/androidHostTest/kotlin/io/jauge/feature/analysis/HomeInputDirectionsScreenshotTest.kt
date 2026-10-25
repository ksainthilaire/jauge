package io.jauge.feature.analysis

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.github.takahirom.roborazzi.captureRoboImage
import io.jauge.core.designsystem.icon.JaugeIcons
import io.jauge.core.designsystem.theme.JaugeTheme
import org.jetbrains.compose.resources.PreviewContextConfigurationEffect
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Design exploration of the top of the home screen (docs/design/home-input.md): three directions, each drawn empty
 * (top) and with a recognized link (bottom), light and dark. Static sketches with literal texts, not product code.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "fr-rFR-w393dp-h852dp-xxhdpi")
class HomeInputDirectionsScreenshotTest {
    @Test
    fun directionA() = both("a-champ-heros") { filled -> DirectionHeroField(filled) }

    @Test
    fun directionB() = both("b-segment") { filled -> DirectionSegment(filled) }

    @Test
    fun directionC() = both("c-composer") { filled -> DirectionComposer(filled) }

    private fun both(name: String, content: @Composable (Boolean) -> Unit) {
        listOf(false, true).forEach { dark ->
            captureRoboImage(filePath = "build/screenshots/pistes/$name-${if (dark) "sombre" else "clair"}.png") {
                PreviewContextConfigurationEffect()
                JaugeTheme(darkTheme = dark) {
                    Column(
                        modifier = Modifier.fillMaxSize().background(JaugeTheme.colors.background).padding(horizontal = 22.dp, vertical = 12.dp),
                    ) {
                        content(false)
                        Box(Modifier.fillMaxWidth().padding(vertical = 22.dp).height(1.dp).background(JaugeTheme.colors.outline))
                        content(true)
                    }
                }
            }
        }
    }
}

@Composable
private fun Header() {
    val colors = JaugeTheme.colors
    Text("Bonsoir Inès", style = JaugeTheme.typography.body, color = colors.textSecondary)
    Text("Quelle offre\non jauge ?", style = JaugeTheme.typography.headline, color = colors.textPrimary, modifier = Modifier.padding(top = 4.dp))
}

@Composable
private fun RecognizedHost(modifier: Modifier = Modifier) {
    val colors = JaugeTheme.colors
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(Modifier.size(18.dp).background(colors.calm.container, CircleShape), contentAlignment = Alignment.Center) {
            Icon(JaugeIcons.Check, null, tint = colors.calm.content, modifier = Modifier.size(12.dp))
        }
        Text("Offre emplois-rhone.fr reconnue", style = JaugeTheme.typography.label, color = colors.calm.content)
    }
}

@Composable
private fun Field(filled: Boolean, trailing: @Composable () -> Unit, modifier: Modifier = Modifier, framed: Boolean = true) {
    val colors = JaugeTheme.colors
    val shape = RoundedCornerShape(18.dp)
    Row(
        modifier = modifier.fillMaxWidth()
            .then(if (framed) Modifier.background(colors.surface, shape).border(1.dp, if (filled) colors.textSecondary else colors.outline, shape) else Modifier)
            .padding(start = 14.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(JaugeIcons.Link, null, tint = colors.textSecondary, modifier = Modifier.size(18.dp))
        Text(
            text = if (filled) "emplois-rhone.fr/offre/charge-marketing…" else "Colle le lien de l'offre",
            style = JaugeTheme.typography.body.copy(fontSize = 15.5.sp),
            color = if (filled) colors.textPrimary else colors.textTertiary,
            maxLines = 1,
            modifier = Modifier.weight(1f).padding(horizontal = 10.dp, vertical = 12.dp),
        )
        trailing()
    }
}

@Composable
private fun Pill(text: String, filledStyle: Boolean, icon: ImageVector? = null) {
    val colors = JaugeTheme.colors
    Row(
        modifier = Modifier.height(44.dp)
            .background(if (filledStyle) colors.action else colors.backgroundAlternative, RoundedCornerShape(14.dp))
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(text, style = JaugeTheme.typography.label.copy(fontSize = 14.5.sp), color = if (filledStyle) colors.onAction else colors.textPrimary)
        icon?.let { Icon(it, null, tint = if (filledStyle) colors.onAction else colors.textPrimary, modifier = Modifier.size(18.dp)) }
    }
}

/** A: one hero field with "Coller" inside, a wide coral button only once a link is recognized, text links below. */
@Composable
private fun DirectionHeroField(filled: Boolean) {
    val colors = JaugeTheme.colors
    Header()
    Field(filled, trailing = { Pill(if (filled) "Effacer" else "Coller", false) }, modifier = Modifier.padding(top = 18.dp))
    if (filled) {
        RecognizedHost(Modifier.padding(top = 10.dp, start = 4.dp))
        Box(
            Modifier.padding(top = 12.dp).fillMaxWidth().height(52.dp).background(colors.action, RoundedCornerShape(18.dp)),
            contentAlignment = Alignment.Center,
        ) { Text("Jauger cette offre", style = JaugeTheme.typography.button, color = colors.onAction) }
    } else {
        Row(Modifier.padding(top = 14.dp, start = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Pas de lien ?  ", style = JaugeTheme.typography.bodySmall, color = colors.textSecondary)
            Text("Une capture", style = JaugeTheme.typography.label, color = colors.textPrimary)
            Text("  ·  ", style = JaugeTheme.typography.bodySmall, color = colors.textTertiary)
            Text("Une photo", style = JaugeTheme.typography.label, color = colors.textPrimary)
        }
    }
}

/** B: a segmented control "Lien · Capture · Photo" above one content area. */
@Composable
private fun DirectionSegment(filled: Boolean) {
    val colors = JaugeTheme.colors
    Header()
    Row(
        Modifier.padding(top = 18.dp).fillMaxWidth().background(colors.backgroundAlternative, RoundedCornerShape(16.dp)).padding(4.dp),
    ) {
        listOf(JaugeIcons.Link to "Lien", JaugeIcons.Image to "Capture", JaugeIcons.Camera to "Photo").forEachIndexed { index, (icon, text) ->
            val selected = index == 0
            Row(
                Modifier.weight(1f).height(44.dp).background(if (selected) colors.surface else Color.Transparent, RoundedCornerShape(12.dp)),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(icon, null, tint = if (selected) colors.textPrimary else colors.textSecondary, modifier = Modifier.size(17.dp))
                Spacer(Modifier.size(6.dp))
                Text(text, style = JaugeTheme.typography.label.copy(fontSize = 14.sp), color = if (selected) colors.textPrimary else colors.textSecondary)
            }
        }
    }
    Field(filled, trailing = { Pill("Coller", false) }, modifier = Modifier.padding(top = 12.dp))
    if (filled) RecognizedHost(Modifier.padding(top = 10.dp, start = 4.dp))
    Box(
        Modifier.padding(top = 12.dp).fillMaxWidth().height(52.dp)
            .background(if (filled) colors.action else colors.action.copy(alpha = 0.25f), RoundedCornerShape(18.dp)),
        contentAlignment = Alignment.Center,
    ) { Text("Jauger cette offre", style = JaugeTheme.typography.button, color = colors.onAction.copy(alpha = if (filled) 1f else 0.7f)) }
}

/** C: a composer like the AI inputs: the field on top, the three ways and one morphing action in a toolbar under it. */
@Composable
private fun DirectionComposer(filled: Boolean) {
    val colors = JaugeTheme.colors
    Header()
    Column(
        Modifier.padding(top = 18.dp).fillMaxWidth()
            .background(colors.surface, RoundedCornerShape(22.dp))
            .border(1.dp, colors.outline, RoundedCornerShape(22.dp))
            .padding(10.dp),
    ) {
        Field(filled, trailing = {}, framed = false)
        if (filled) RecognizedHost(Modifier.padding(start = 14.dp, bottom = 8.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(JaugeIcons.Image to "Capture", JaugeIcons.Camera to "Photo").forEach { (icon, text) ->
                Row(
                    Modifier.height(44.dp).background(colors.backgroundAlternative, RoundedCornerShape(14.dp)).padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Icon(icon, null, tint = colors.textSecondary, modifier = Modifier.size(17.dp))
                    Text(text, style = JaugeTheme.typography.label.copy(fontSize = 14.sp, fontWeight = FontWeight.SemiBold), color = colors.textPrimary)
                }
            }
            Spacer(Modifier.weight(1f))
            if (filled) Pill("Jauger", true, JaugeIcons.ArrowRight) else Pill("Coller", false)
        }
    }
    if (!filled) {
        Text(
            "Lien, capture d'écran ou photo d'une annonce papier.",
            style = JaugeTheme.typography.bodySmall, color = colors.textSecondary, modifier = Modifier.padding(top = 10.dp, start = 4.dp),
        )
    }
}
