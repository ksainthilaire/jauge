package io.jauge.core.designsystem.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Raw brand palette from `BRANDING.md`. Screens never use it directly: they read the semantic [JaugeColors] through
 * `JaugeTheme.colors`, which switch between light and dark.
 */
object JaugePalette {
    /** Ink, dark background and main text. */
    val Ink = Color(0xFF13211D)

    /** Ink 2, secondary dark surfaces. */
    val Ink2 = Color(0xFF1E302A)

    /** Ink 3, dark outlines. */
    val Ink3 = Color(0xFF2C423B)

    /** Deepest ink, used behind brand blocks. */
    val InkDeep = Color(0xFF0F1B18)

    /** Paper, warm light background. */
    val Paper = Color(0xFFF7F2E8)

    /** Paper 2, light tracks and alternative background. */
    val Paper2 = Color(0xFFEFE8DA)

    /** Off white, cards. */
    val OffWhite = Color(0xFFFFFDF8)

    /** Line, separators and outlines. */
    val Line = Color(0xFFE2DACB)

    /** Sage grey, secondary text. */
    val Sage = Color(0xFF6C7A74)

    /** Light sage grey, tertiary text. */
    val Sage2 = Color(0xFFA9B3AE)

    /** Mint: calm (low competition), success. */
    val Mint = Color(0xFF3CCB9F)

    /** Mint text on light backgrounds. */
    val MintText = Color(0xFF0E7A5C)

    /** Mint container on light backgrounds. */
    val MintContainer = Color(0xFFDDF5EC)

    /** Amber: disputed (medium competition), advice. */
    val Amber = Color(0xFFFFB23F)

    /** Amber text on light backgrounds. */
    val AmberText = Color(0xFF9A5B00)

    /** Amber container on light backgrounds. */
    val AmberContainer = Color(0xFFFFF0D6)

    /** Coral: crowded (high competition) and actions. */
    val Coral = Color(0xFFFF5B3A)

    /** Coral text on light backgrounds. */
    val CoralText = Color(0xFFC2361A)

    /** Coral container on light backgrounds. */
    val CoralContainer = Color(0xFFFFE3DB)

    /** Pure white, text on coral buttons. */
    val White = Color(0xFFFFFFFF)
}

/**
 * Colors of one competition level (calm, disputed or crowded).
 *
 * @property accent the level color itself (gauge segment, dot).
 * @property content text and icons drawn on [container] or on the background.
 * @property container soft background of pills and banners.
 */
@Immutable
data class LevelColors(
    val accent: Color,
    val content: Color,
    val container: Color,
)

/**
 * Semantic colors of the app.
 *
 * @property background screen background.
 * @property backgroundAlternative tracks, segmented backgrounds and secondary areas.
 * @property surface cards and sheets.
 * @property outline card borders and separators.
 * @property textPrimary main text.
 * @property textSecondary secondary text.
 * @property textTertiary hints and disabled text.
 * @property inverseSurface strong contrasting surface (the dark verdict card on a light screen).
 * @property onInverseSurface text on [inverseSurface].
 * @property inverseSurfaceSecondary secondary contrasting surface.
 * @property inverseOutline outline on contrasting surfaces.
 * @property action coral call to action.
 * @property onAction text on [action].
 * @property neutralAction ink secondary button.
 * @property onNeutralAction text on [neutralAction].
 * @property calm colors of the calm level.
 * @property disputed colors of the disputed level.
 * @property crowded colors of the crowded level.
 * @property skeleton base color of loading placeholders.
 * @property skeletonHighlight moving highlight of loading placeholders.
 * @property isDark true for the dark palette.
 */
@Immutable
data class JaugeColors(
    val background: Color,
    val backgroundAlternative: Color,
    val surface: Color,
    val outline: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textTertiary: Color,
    val inverseSurface: Color,
    val onInverseSurface: Color,
    val inverseSurfaceSecondary: Color,
    val inverseOutline: Color,
    val action: Color,
    val onAction: Color,
    val neutralAction: Color,
    val onNeutralAction: Color,
    val calm: LevelColors,
    val disputed: LevelColors,
    val crowded: LevelColors,
    val skeleton: Color,
    val skeletonHighlight: Color,
    val isDark: Boolean,
)

/** Light palette: paper background, off white cards, ink text (most mockups). */
val LightJaugeColors = JaugeColors(
    background = JaugePalette.Paper,
    backgroundAlternative = JaugePalette.Paper2,
    surface = JaugePalette.OffWhite,
    outline = JaugePalette.Line,
    textPrimary = JaugePalette.Ink,
    textSecondary = JaugePalette.Sage,
    textTertiary = JaugePalette.Sage2,
    inverseSurface = JaugePalette.Ink,
    onInverseSurface = JaugePalette.Paper,
    inverseSurfaceSecondary = JaugePalette.Ink2,
    inverseOutline = JaugePalette.Ink3,
    action = JaugePalette.Coral,
    onAction = JaugePalette.White,
    neutralAction = JaugePalette.Ink,
    onNeutralAction = JaugePalette.Paper,
    calm = LevelColors(JaugePalette.Mint, JaugePalette.MintText, JaugePalette.MintContainer),
    disputed = LevelColors(JaugePalette.Amber, JaugePalette.AmberText, JaugePalette.AmberContainer),
    crowded = LevelColors(JaugePalette.Coral, JaugePalette.CoralText, JaugePalette.CoralContainer),
    skeleton = JaugePalette.Paper2,
    skeletonHighlight = JaugePalette.OffWhite,
    isDark = false,
)

/** Dark palette: ink background, ink 2 surfaces, paper text (mockups 01, 03, 08 and 12). */
val DarkJaugeColors = JaugeColors(
    background = JaugePalette.Ink,
    backgroundAlternative = JaugePalette.InkDeep,
    surface = JaugePalette.Ink2,
    outline = JaugePalette.Ink3,
    textPrimary = JaugePalette.Paper,
    textSecondary = JaugePalette.Sage2,
    textTertiary = JaugePalette.Sage,
    inverseSurface = JaugePalette.Paper,
    onInverseSurface = JaugePalette.Ink,
    inverseSurfaceSecondary = JaugePalette.OffWhite,
    inverseOutline = JaugePalette.Line,
    action = JaugePalette.Coral,
    onAction = JaugePalette.White,
    neutralAction = JaugePalette.Paper,
    onNeutralAction = JaugePalette.Ink,
    calm = LevelColors(JaugePalette.Mint, JaugePalette.Mint, Color(0xFF173B31)),
    disputed = LevelColors(JaugePalette.Amber, JaugePalette.Amber, Color(0xFF3A2F18)),
    crowded = LevelColors(JaugePalette.Coral, Color(0xFFFF8468), Color(0xFF2A2620)),
    skeleton = JaugePalette.Ink2,
    skeletonHighlight = JaugePalette.Ink3,
    isDark = true,
)

/** Colors provided by `JaugeTheme`. */
val LocalJaugeColors = staticCompositionLocalOf { LightJaugeColors }
