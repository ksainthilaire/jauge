package io.jauge.feature.account.about

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.jauge.core.designsystem.component.JaugeLogo
import io.jauge.core.designsystem.component.JaugeScreenScaffold
import io.jauge.core.designsystem.component.RoundIconButton
import io.jauge.core.designsystem.icon.JaugeIcons
import io.jauge.core.designsystem.theme.JaugeTheme
import io.jauge.feature.account.components.SettingsListCard
import io.jauge.feature.account.components.SettingsRowItem
import io.jauge.feature.account.platform.rememberAppVersion
import io.jauge.feature.account.resources.Res
import io.jauge.feature.account.resources.about_app_name
import io.jauge.feature.account.resources.about_contact
import io.jauge.feature.account.resources.about_contact_address
import io.jauge.feature.account.resources.about_tagline
import io.jauge.feature.account.resources.about_text
import io.jauge.feature.account.resources.about_title
import io.jauge.feature.account.resources.about_version
import io.jauge.feature.account.resources.about_website
import io.jauge.feature.account.resources.about_website_address
import io.jauge.feature.account.resources.account_back
import io.jauge.feature.account.resources.profile_legal_notice
import io.jauge.feature.account.resources.profile_privacy
import io.jauge.feature.account.resources.profile_terms
import org.jetbrains.compose.resources.stringResource

/**
 * About Jauge: version, what the app does, contact and legal pages.
 *
 * No technical information is shown here (libraries, build numbers, identifiers): the notices required by the
 * licenses of the libraries ship with the app in `third_party_notices.txt` (resources of `composeApp`), not on screen.
 *
 * @param onBack back button.
 * @param onOpenLegalNotice legal notice.
 * @param onOpenPrivacyPolicy privacy policy.
 * @param onOpenTerms terms of use.
 */
@Composable
fun AboutScreen(onBack: () -> Unit, onOpenLegalNotice: () -> Unit, onOpenPrivacyPolicy: () -> Unit, onOpenTerms: () -> Unit) {
    val colors = JaugeTheme.colors
    val typography = JaugeTheme.typography
    val uriHandler = LocalUriHandler.current
    val version = displayVersion(rememberAppVersion())
    val contact = stringResource(Res.string.about_contact_address)
    val website = stringResource(Res.string.about_website_address)
    JaugeScreenScaffold(
        title = stringResource(Res.string.about_title),
        navigationIcon = { RoundIconButton(JaugeIcons.Back, stringResource(Res.string.account_back), onBack) },
    ) {
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = JaugeTheme.spacing.screenGutter)
                .padding(top = 16.dp, bottom = 32.dp),
        ) {
            JaugeLogo(modifier = Modifier.size(64.dp))
            Text(text = stringResource(Res.string.about_app_name), style = typography.headline, color = colors.textPrimary, modifier = Modifier.padding(top = 16.dp))
            Text(text = stringResource(Res.string.about_tagline), style = typography.body, color = colors.textSecondary)
            if (version.isNotEmpty()) {
                Text(
                    text = stringResource(Res.string.about_version, version),
                    style = typography.numberSmall.copy(fontWeight = FontWeight.Medium),
                    color = colors.textSecondary,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
            Text(
                text = stringResource(Res.string.about_text),
                style = typography.body.copy(fontWeight = FontWeight.Normal),
                color = colors.inverseSurfaceSecondary,
                modifier = Modifier.padding(top = 16.dp),
            )
            SettingsListCard(
                rows = listOf(
                    SettingsRowItem(JaugeIcons.Mail, stringResource(Res.string.about_contact), value = contact) {
                        runCatching { uriHandler.openUri("mailto:$contact") }
                    },
                    SettingsRowItem(JaugeIcons.Link, stringResource(Res.string.about_website)) { runCatching { uriHandler.openUri(website) } },
                    SettingsRowItem(JaugeIcons.Info, stringResource(Res.string.profile_legal_notice), onClick = onOpenLegalNotice),
                    SettingsRowItem(JaugeIcons.Shield, stringResource(Res.string.profile_privacy), onClick = onOpenPrivacyPolicy),
                    SettingsRowItem(JaugeIcons.Document, stringResource(Res.string.profile_terms), onClick = onOpenTerms),
                ),
                modifier = Modifier.padding(top = 18.dp),
            )
        }
    }
}

/**
 * Version shown to the user: only the public part of the version name ("1.0" or "1.2.3"), without any build
 * suffix ("-debug", "+42", " (17)"). Empty when the raw version holds no public part.
 *
 * @param rawVersion version name read from the platform.
 */
fun displayVersion(rawVersion: String): String =
    Regex("""^\s*(\d+(?:\.\d+)*)""").find(rawVersion)?.groupValues?.get(1).orEmpty()
