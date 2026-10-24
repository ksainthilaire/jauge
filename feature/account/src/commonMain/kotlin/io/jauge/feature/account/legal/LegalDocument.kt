package io.jauge.feature.account.legal

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.jauge.core.designsystem.component.JaugeScreenScaffold
import io.jauge.core.designsystem.component.RoundIconButton
import io.jauge.core.designsystem.icon.JaugeIcons
import io.jauge.core.designsystem.theme.JaugeTheme
import io.jauge.feature.account.resources.Res
import io.jauge.feature.account.resources.account_back
import io.jauge.feature.account.resources.legal_updated
import org.jetbrains.compose.resources.stringResource

/**
 * One section of a legal page: a title and its text, already resolved from `strings.xml`.
 *
 * @property title title of the section.
 * @property body text of the section.
 */
class LegalSection(val title: String, val body: String)

/**
 * Layout shared by the legal pages: title bar, update date, introduction and sections.
 *
 * @param title title of the page.
 * @param introduction optional paragraph before the sections.
 * @param sections sections of the page.
 * @param onBack back button.
 * @param modifier modifier of the screen.
 */
@Composable
fun LegalDocument(
    title: String,
    introduction: String?,
    sections: List<LegalSection>,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = JaugeTheme.colors
    val typography = JaugeTheme.typography
    JaugeScreenScaffold(
        modifier = modifier,
        title = title,
        navigationIcon = { RoundIconButton(JaugeIcons.Back, stringResource(Res.string.account_back), onBack) },
    ) {
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = JaugeTheme.spacing.screenGutter)
                .padding(top = 12.dp, bottom = 32.dp),
        ) {
            Text(text = title, style = typography.headline, color = colors.textPrimary)
            Text(
                text = stringResource(Res.string.legal_updated),
                style = typography.caption,
                color = colors.textSecondary,
                modifier = Modifier.padding(top = 6.dp),
            )
            introduction?.let {
                Text(
                    text = it,
                    style = typography.body.copy(fontWeight = FontWeight.Normal, lineHeight = 22.sp),
                    color = colors.inverseSurfaceSecondary,
                    modifier = Modifier.padding(top = 16.dp),
                )
            }
            sections.forEach { section ->
                Text(
                    text = section.title,
                    style = typography.sectionTitle.copy(fontSize = 18.sp),
                    color = colors.textPrimary,
                    modifier = Modifier.padding(top = 22.dp),
                )
                Text(
                    text = section.body,
                    style = typography.body.copy(fontWeight = FontWeight.Normal, lineHeight = 22.sp),
                    color = colors.inverseSurfaceSecondary,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }
    }
}
