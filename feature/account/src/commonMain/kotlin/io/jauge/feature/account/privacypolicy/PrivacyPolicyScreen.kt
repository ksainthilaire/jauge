package io.jauge.feature.account.privacypolicy

import androidx.compose.runtime.Composable
import io.jauge.feature.account.legal.LegalDocument
import io.jauge.feature.account.legal.LegalSection
import io.jauge.feature.account.resources.Res
import io.jauge.feature.account.resources.privacy_10_body
import io.jauge.feature.account.resources.privacy_10_title
import io.jauge.feature.account.resources.privacy_11_body
import io.jauge.feature.account.resources.privacy_11_title
import io.jauge.feature.account.resources.privacy_12_body
import io.jauge.feature.account.resources.privacy_12_title
import io.jauge.feature.account.resources.privacy_1_body
import io.jauge.feature.account.resources.privacy_1_title
import io.jauge.feature.account.resources.privacy_2_body
import io.jauge.feature.account.resources.privacy_2_title
import io.jauge.feature.account.resources.privacy_3_body
import io.jauge.feature.account.resources.privacy_3_title
import io.jauge.feature.account.resources.privacy_4_body
import io.jauge.feature.account.resources.privacy_4_title
import io.jauge.feature.account.resources.privacy_5_body
import io.jauge.feature.account.resources.privacy_5_title
import io.jauge.feature.account.resources.privacy_6_body
import io.jauge.feature.account.resources.privacy_6_title
import io.jauge.feature.account.resources.privacy_7_body
import io.jauge.feature.account.resources.privacy_7_title
import io.jauge.feature.account.resources.privacy_8_body
import io.jauge.feature.account.resources.privacy_8_title
import io.jauge.feature.account.resources.privacy_9_body
import io.jauge.feature.account.resources.privacy_9_title
import io.jauge.feature.account.resources.privacy_intro
import io.jauge.feature.account.resources.privacy_policy_title
import org.jetbrains.compose.resources.stringResource

/**
 * Privacy policy (linked from mockups 08, 11 and 13): data used, purposes, legal bases, recipients, retention and rights under the GDPR.
 *
 * @param onBack called when the user leaves the screen.
 */
@Composable
fun PrivacyPolicyScreen(onBack: () -> Unit) {
    LegalDocument(
        title = stringResource(Res.string.privacy_policy_title),
        introduction = stringResource(Res.string.privacy_intro),
        sections = listOf(
            LegalSection(stringResource(Res.string.privacy_1_title), stringResource(Res.string.privacy_1_body)),
            LegalSection(stringResource(Res.string.privacy_2_title), stringResource(Res.string.privacy_2_body)),
            LegalSection(stringResource(Res.string.privacy_3_title), stringResource(Res.string.privacy_3_body)),
            LegalSection(stringResource(Res.string.privacy_4_title), stringResource(Res.string.privacy_4_body)),
            LegalSection(stringResource(Res.string.privacy_5_title), stringResource(Res.string.privacy_5_body)),
            LegalSection(stringResource(Res.string.privacy_6_title), stringResource(Res.string.privacy_6_body)),
            LegalSection(stringResource(Res.string.privacy_7_title), stringResource(Res.string.privacy_7_body)),
            LegalSection(stringResource(Res.string.privacy_8_title), stringResource(Res.string.privacy_8_body)),
            LegalSection(stringResource(Res.string.privacy_9_title), stringResource(Res.string.privacy_9_body)),
            LegalSection(stringResource(Res.string.privacy_10_title), stringResource(Res.string.privacy_10_body)),
            LegalSection(stringResource(Res.string.privacy_11_title), stringResource(Res.string.privacy_11_body)),
            LegalSection(stringResource(Res.string.privacy_12_title), stringResource(Res.string.privacy_12_body)),
        ),
        onBack = onBack,
    )
}
