package io.jauge.feature.account.termsofuse

import androidx.compose.runtime.Composable
import io.jauge.feature.account.legal.LegalDocument
import io.jauge.feature.account.legal.LegalSection
import io.jauge.feature.account.resources.Res
import io.jauge.feature.account.resources.legal_consumer_mediator
import io.jauge.feature.account.resources.terms_10_body
import io.jauge.feature.account.resources.terms_10_title
import io.jauge.feature.account.resources.terms_11_body
import io.jauge.feature.account.resources.terms_11_title
import io.jauge.feature.account.resources.terms_12_body
import io.jauge.feature.account.resources.terms_12_title
import io.jauge.feature.account.resources.terms_13_body
import io.jauge.feature.account.resources.terms_13_title
import io.jauge.feature.account.resources.terms_1_body
import io.jauge.feature.account.resources.terms_1_title
import io.jauge.feature.account.resources.terms_2_body
import io.jauge.feature.account.resources.terms_2_title
import io.jauge.feature.account.resources.terms_3_body
import io.jauge.feature.account.resources.terms_3_title
import io.jauge.feature.account.resources.terms_4_body
import io.jauge.feature.account.resources.terms_4_title
import io.jauge.feature.account.resources.terms_5_body
import io.jauge.feature.account.resources.terms_5_title
import io.jauge.feature.account.resources.terms_6_body
import io.jauge.feature.account.resources.terms_6_title
import io.jauge.feature.account.resources.terms_7_body
import io.jauge.feature.account.resources.terms_7_title
import io.jauge.feature.account.resources.terms_8_body
import io.jauge.feature.account.resources.terms_8_title
import io.jauge.feature.account.resources.terms_9_body
import io.jauge.feature.account.resources.terms_9_title
import io.jauge.feature.account.resources.terms_intro
import io.jauge.feature.account.resources.terms_of_use_title
import org.jetbrains.compose.resources.stringResource

/**
 * Terms of use (linked from mockups 08 and 11): the service, the free plan, the Jauge Plus monthly subscription and its renewal, liability and disputes.
 *
 * @param onBack called when the user leaves the screen.
 */
@Composable
fun TermsOfUseScreen(onBack: () -> Unit) {
    LegalDocument(
        title = stringResource(Res.string.terms_of_use_title),
        introduction = stringResource(Res.string.terms_intro),
        sections = listOf(
            LegalSection(stringResource(Res.string.terms_1_title), stringResource(Res.string.terms_1_body)),
            LegalSection(stringResource(Res.string.terms_2_title), stringResource(Res.string.terms_2_body)),
            LegalSection(stringResource(Res.string.terms_3_title), stringResource(Res.string.terms_3_body)),
            LegalSection(stringResource(Res.string.terms_4_title), stringResource(Res.string.terms_4_body)),
            LegalSection(stringResource(Res.string.terms_5_title), stringResource(Res.string.terms_5_body)),
            LegalSection(stringResource(Res.string.terms_6_title), stringResource(Res.string.terms_6_body)),
            LegalSection(stringResource(Res.string.terms_7_title), stringResource(Res.string.terms_7_body)),
            LegalSection(stringResource(Res.string.terms_8_title), stringResource(Res.string.terms_8_body)),
            LegalSection(stringResource(Res.string.terms_9_title), stringResource(Res.string.terms_9_body)),
            LegalSection(stringResource(Res.string.terms_10_title), stringResource(Res.string.terms_10_body)),
            LegalSection(stringResource(Res.string.terms_11_title), stringResource(Res.string.terms_11_body)),
            LegalSection(stringResource(Res.string.terms_12_title), stringResource(Res.string.terms_12_body)),
            LegalSection(stringResource(Res.string.terms_13_title), stringResource(Res.string.terms_13_body, stringResource(Res.string.legal_consumer_mediator))),
        ),
        onBack = onBack,
    )
}
