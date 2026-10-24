package io.jauge.core.designsystem.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable

/**
 * Theme of the app. Wrap every screen in it.
 *
 * Screens that are always dark in the mockups (01 welcome, 03 reading, 08 paywall, 12 alerts) wrap their content in
 * `JaugeTheme(darkTheme = true)`.
 *
 * @param darkTheme true to use the dark palette, follows the system by default.
 * @param content the themed content.
 */
@Composable
fun JaugeTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colors = if (darkTheme) DarkJaugeColors else LightJaugeColors
    val typography = jaugeTypography()
    MaterialTheme(
        colorScheme = colors.toMaterialColorScheme(),
        typography = typography.toMaterialTypography(),
    ) {
        CompositionLocalProvider(
            LocalJaugeColors provides colors,
            LocalJaugeTypography provides typography,
            LocalJaugeSpacing provides JaugeSpacing(),
            LocalJaugeRadii provides JaugeRadii(),
            LocalContentColor provides colors.textPrimary,
            content = content,
        )
    }
}

/** Access to the design tokens of the current theme, for example `JaugeTheme.colors.action`. */
object JaugeTheme {
    /** Semantic colors. */
    val colors: JaugeColors
        @Composable @ReadOnlyComposable
        get() = LocalJaugeColors.current

    /** Text styles. */
    val typography: JaugeTypography
        @Composable @ReadOnlyComposable
        get() = LocalJaugeTypography.current

    /** Spacing scale. */
    val spacing: JaugeSpacing
        @Composable @ReadOnlyComposable
        get() = LocalJaugeSpacing.current

    /** Corner radii. */
    val radii: JaugeRadii
        @Composable @ReadOnlyComposable
        get() = LocalJaugeRadii.current
}

/** Maps the Jauge colors onto Material 3 so that Material components look on brand. */
private fun JaugeColors.toMaterialColorScheme(): ColorScheme {
    val base = if (isDark) darkColorScheme() else lightColorScheme()
    return base.copy(
        primary = action,
        onPrimary = onAction,
        primaryContainer = crowded.container,
        onPrimaryContainer = crowded.content,
        secondary = neutralAction,
        onSecondary = onNeutralAction,
        secondaryContainer = calm.container,
        onSecondaryContainer = calm.content,
        tertiary = disputed.accent,
        onTertiary = textPrimary,
        tertiaryContainer = disputed.container,
        onTertiaryContainer = disputed.content,
        background = background,
        onBackground = textPrimary,
        surface = surface,
        onSurface = textPrimary,
        surfaceVariant = backgroundAlternative,
        onSurfaceVariant = textSecondary,
        surfaceContainer = surface,
        surfaceContainerLow = surface,
        surfaceContainerHigh = surface,
        surfaceContainerHighest = backgroundAlternative,
        outline = outline,
        outlineVariant = outline,
        inverseSurface = inverseSurface,
        inverseOnSurface = onInverseSurface,
        error = crowded.content,
        errorContainer = crowded.container,
    )
}

/** Maps the Jauge text styles onto Material 3. */
private fun JaugeTypography.toMaterialTypography(): Typography = Typography(
    displayLarge = display,
    displayMedium = display,
    displaySmall = headline,
    headlineLarge = headline,
    headlineMedium = headline,
    headlineSmall = title,
    titleLarge = sectionTitle,
    titleMedium = bodyStrong,
    titleSmall = label,
    bodyLarge = bodyLarge,
    bodyMedium = body,
    bodySmall = bodySmall,
    labelLarge = button,
    labelMedium = label,
    labelSmall = caption,
)
