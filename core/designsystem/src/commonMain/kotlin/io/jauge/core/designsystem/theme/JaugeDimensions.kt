package io.jauge.core.designsystem.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Spacing scale taken from the mockups (`ecrans.html`).
 *
 * @property extraSmall 4 dp, between a value and its caption.
 * @property small 8 dp, between pills and small items.
 * @property medium 10 dp, between cards in a grid.
 * @property large 14 dp, inner padding of compact cards.
 * @property extraLarge 16 dp, inner padding of cards.
 * @property screenGutter 22 dp, horizontal padding of every screen.
 * @property section 26 dp, between sections of a screen.
 * @property huge 40 dp, around empty and error states.
 */
@Immutable
data class JaugeSpacing(
    val extraSmall: Dp = 4.dp,
    val small: Dp = 8.dp,
    val medium: Dp = 10.dp,
    val large: Dp = 14.dp,
    val extraLarge: Dp = 16.dp,
    val screenGutter: Dp = 22.dp,
    val section: Dp = 26.dp,
    val huge: Dp = 40.dp,
)

/**
 * Corner radii taken from the mockups.
 *
 * @property small 8 dp, small badges.
 * @property iconTile 11 dp, square icon backgrounds.
 * @property medium 14 dp, inner blocks.
 * @property button 18 dp, buttons and banners.
 * @property card 22 dp, cards.
 * @property large 26 dp, share card and sheets.
 */
@Immutable
data class JaugeRadii(
    val small: Dp = 8.dp,
    val iconTile: Dp = 11.dp,
    val medium: Dp = 14.dp,
    val button: Dp = 18.dp,
    val card: Dp = 22.dp,
    val large: Dp = 26.dp,
) {
    /** Shape of cards. */
    val cardShape: RoundedCornerShape get() = RoundedCornerShape(card)

    /** Shape of buttons. */
    val buttonShape: RoundedCornerShape get() = RoundedCornerShape(button)

    /** Fully rounded shape of pills, chips and round buttons. */
    val pillShape: RoundedCornerShape get() = RoundedCornerShape(percent = 50)
}

/** Spacing provided by `JaugeTheme`. */
val LocalJaugeSpacing = staticCompositionLocalOf { JaugeSpacing() }

/** Radii provided by `JaugeTheme`. */
val LocalJaugeRadii = staticCompositionLocalOf { JaugeRadii() }
