package io.jauge.core.designsystem.component

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import io.jauge.core.designsystem.resources.Res
import io.jauge.core.designsystem.resources.level_calm
import io.jauge.core.designsystem.resources.level_crowded
import io.jauge.core.designsystem.resources.level_disputed
import io.jauge.core.designsystem.theme.JaugeTheme
import io.jauge.core.designsystem.theme.LevelColors
import org.jetbrains.compose.resources.StringResource

/**
 * The three competition levels shown by the gauge. Their colors are always shown together with their word
 * (`BRANDING.md`): calm (mint), disputed (amber), crowded (coral).
 *
 * @property label the word of the level.
 */
enum class GaugeLevel(val label: StringResource) {
    /** Low competition, score 0 to 33. */
    CALM(Res.string.level_calm),

    /** Medium competition, score 34 to 66. */
    DISPUTED(Res.string.level_disputed),

    /** High competition, score 67 to 100. */
    CROWDED(Res.string.level_crowded),
    ;

    /** Thresholds shared with the domain layer. */
    companion object {
        /** Lowest score of the disputed level. */
        const val DISPUTED_FROM = 34

        /** Lowest score of the crowded level. */
        const val CROWDED_FROM = 67

        /**
         * Level of a score between 0 and 100 (values outside are clamped).
         *
         * @param score the competition score.
         */
        fun fromScore(score: Int): GaugeLevel = when (score.coerceIn(0, 100)) {
            in 0 until DISPUTED_FROM -> CALM
            in DISPUTED_FROM until CROWDED_FROM -> DISPUTED
            else -> CROWDED
        }
    }
}

/** Colors of this level in the current theme. */
val GaugeLevel.colors: LevelColors
    @Composable @ReadOnlyComposable
    get() = when (this) {
        GaugeLevel.CALM -> JaugeTheme.colors.calm
        GaugeLevel.DISPUTED -> JaugeTheme.colors.disputed
        GaugeLevel.CROWDED -> JaugeTheme.colors.crowded
    }
