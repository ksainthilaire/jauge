package io.jauge.core.designsystem.motion

import androidx.compose.runtime.Composable

/**
 * True when the person asked the system to reduce motion: "Supprimer les animations" on Android (animator duration
 * scale set to 0), "Réduire les animations" on iOS. Components then skip their transitions and show the end state.
 */
@Composable
expect fun rememberReducedMotion(): Boolean
