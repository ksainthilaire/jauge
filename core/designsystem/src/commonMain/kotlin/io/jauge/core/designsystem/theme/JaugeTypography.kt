package io.jauge.core.designsystem.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import io.jauge.core.designsystem.resources.Res
import io.jauge.core.designsystem.resources.bricolage_grotesque_bold
import io.jauge.core.designsystem.resources.bricolage_grotesque_extrabold
import io.jauge.core.designsystem.resources.instrument_sans_bold
import io.jauge.core.designsystem.resources.instrument_sans_medium
import io.jauge.core.designsystem.resources.instrument_sans_regular
import io.jauge.core.designsystem.resources.instrument_sans_semibold
import io.jauge.core.designsystem.resources.jetbrains_mono_bold
import io.jauge.core.designsystem.resources.jetbrains_mono_medium
import org.jetbrains.compose.resources.Font

/**
 * Text styles of the app (`BRANDING.md`): Bricolage Grotesque for titles, Instrument Sans for the interface and
 * JetBrains Mono for numbers (scores, salaries, applicants, percentages).
 *
 * @property display hero title (mockup 01, 40).
 * @property headline screen title (h2, 30).
 * @property title offer title (25).
 * @property sectionTitle title of a section ("Mes offres jaugées", 20).
 * @property bodyLarge introduction paragraphs (17).
 * @property body standard text (15).
 * @property bodyStrong emphasized list text (15.5, bold).
 * @property bodySmall secondary text (13.5).
 * @property label labels and small buttons (13, semi bold).
 * @property overline small uppercase heading ("BONJOUR INÈS").
 * @property caption captions (12).
 * @property button text of large buttons (17, bold).
 * @property pill text of pills (12.5, bold).
 * @property tab labels of the tab bar (11, semi bold).
 * @property numberLarge main score (44).
 * @property numberMedium figures of tiles (22).
 * @property numberSmall inline figures (13).
 * @property numberCaption small figures and axis labels (11.5).
 */
@Immutable
data class JaugeTypography(
    val display: TextStyle,
    val headline: TextStyle,
    val title: TextStyle,
    val sectionTitle: TextStyle,
    val bodyLarge: TextStyle,
    val body: TextStyle,
    val bodyStrong: TextStyle,
    val bodySmall: TextStyle,
    val label: TextStyle,
    val overline: TextStyle,
    val caption: TextStyle,
    val button: TextStyle,
    val pill: TextStyle,
    val tab: TextStyle,
    val numberLarge: TextStyle,
    val numberMedium: TextStyle,
    val numberSmall: TextStyle,
    val numberCaption: TextStyle,
)

/** Title font family: Bricolage Grotesque, embedded in the resources. */
@Composable
fun titleFontFamily(): FontFamily = FontFamily(
    Font(Res.font.bricolage_grotesque_bold, FontWeight.Bold),
    Font(Res.font.bricolage_grotesque_extrabold, FontWeight.ExtraBold),
)

/** Interface font family: Instrument Sans, embedded in the resources. */
@Composable
fun textFontFamily(): FontFamily = FontFamily(
    Font(Res.font.instrument_sans_regular, FontWeight.Normal),
    Font(Res.font.instrument_sans_medium, FontWeight.Medium),
    Font(Res.font.instrument_sans_semibold, FontWeight.SemiBold),
    Font(Res.font.instrument_sans_bold, FontWeight.Bold),
)

/** Number font family: JetBrains Mono, embedded in the resources. */
@Composable
fun numberFontFamily(): FontFamily = FontFamily(
    Font(Res.font.jetbrains_mono_medium, FontWeight.Medium),
    Font(Res.font.jetbrains_mono_bold, FontWeight.Bold),
)

/** Builds the typography with the embedded fonts. */
@Composable
fun jaugeTypography(): JaugeTypography {
    val titles = titleFontFamily()
    val text = textFontFamily()
    val numbers = numberFontFamily()
    return JaugeTypography(
        display = TextStyle(fontFamily = titles, fontWeight = FontWeight.ExtraBold, fontSize = 40.sp, lineHeight = 42.sp, letterSpacing = (-0.035).em),
        headline = TextStyle(fontFamily = titles, fontWeight = FontWeight.ExtraBold, fontSize = 30.sp, lineHeight = 32.sp, letterSpacing = (-0.027).em),
        title = TextStyle(fontFamily = titles, fontWeight = FontWeight.ExtraBold, fontSize = 25.sp, lineHeight = 28.sp, letterSpacing = (-0.024).em),
        sectionTitle = TextStyle(fontFamily = titles, fontWeight = FontWeight.Bold, fontSize = 20.sp, lineHeight = 24.sp),
        bodyLarge = TextStyle(fontFamily = text, fontWeight = FontWeight.Normal, fontSize = 17.sp, lineHeight = 25.sp),
        body = TextStyle(fontFamily = text, fontWeight = FontWeight.Medium, fontSize = 15.sp, lineHeight = 21.sp),
        bodyStrong = TextStyle(fontFamily = text, fontWeight = FontWeight.Bold, fontSize = 15.5.sp, lineHeight = 20.sp),
        bodySmall = TextStyle(fontFamily = text, fontWeight = FontWeight.Normal, fontSize = 13.5.sp, lineHeight = 19.sp),
        label = TextStyle(fontFamily = text, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, lineHeight = 17.sp),
        overline = TextStyle(fontFamily = text, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, lineHeight = 17.sp, letterSpacing = 0.09.em),
        caption = TextStyle(fontFamily = text, fontWeight = FontWeight.Normal, fontSize = 12.sp, lineHeight = 16.sp),
        button = TextStyle(fontFamily = text, fontWeight = FontWeight.Bold, fontSize = 17.sp, lineHeight = 22.sp),
        pill = TextStyle(fontFamily = text, fontWeight = FontWeight.Bold, fontSize = 12.5.sp, lineHeight = 16.sp),
        tab = TextStyle(fontFamily = text, fontWeight = FontWeight.SemiBold, fontSize = 11.sp, lineHeight = 14.sp),
        numberLarge = TextStyle(fontFamily = numbers, fontWeight = FontWeight.Bold, fontSize = 44.sp, lineHeight = 44.sp, letterSpacing = (-0.045).em),
        numberMedium = TextStyle(fontFamily = numbers, fontWeight = FontWeight.Bold, fontSize = 22.sp, lineHeight = 26.sp, letterSpacing = (-0.023).em),
        numberSmall = TextStyle(fontFamily = numbers, fontWeight = FontWeight.Bold, fontSize = 13.sp, lineHeight = 17.sp),
        numberCaption = TextStyle(fontFamily = numbers, fontWeight = FontWeight.Medium, fontSize = 11.5.sp, lineHeight = 15.sp, letterSpacing = 0.05.em),
    )
}

/** Typography provided by `JaugeTheme`. */
val LocalJaugeTypography = staticCompositionLocalOf<JaugeTypography> {
    error("JaugeTypography is provided by JaugeTheme: wrap the content in JaugeTheme { }")
}
