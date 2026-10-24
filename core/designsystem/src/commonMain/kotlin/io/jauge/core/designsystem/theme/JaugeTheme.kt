package io.jauge.core.designsystem.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * Theme of the app. Wrap every screen in it.
 *
 * @param darkTheme true to use the dark palette, follows the system by default.
 * @param content the themed content.
 */
@Composable
fun JaugeTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) {
        darkColorScheme(primary = Color(0xFFFF5B3A), background = Color(0xFF13211D), surface = Color(0xFF1E302A))
    } else {
        lightColorScheme(primary = Color(0xFFFF5B3A), background = Color(0xFFF7F2E8), surface = Color(0xFFFFFDF8))
    }
    MaterialTheme(colorScheme = colorScheme, content = content)
}
