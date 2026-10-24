package io.jauge.feature.account.legalnotice

import androidx.compose.runtime.Composable
import io.jauge.feature.account.legal.LegalDocument
import io.jauge.feature.account.legal.LegalSection
import io.jauge.feature.account.resources.Res
import io.jauge.feature.account.resources.legal_notice_title
import io.jauge.feature.account.resources.legal_host_address
import io.jauge.feature.account.resources.legal_publication_director
import io.jauge.feature.account.resources.legal_publisher_address
import io.jauge.feature.account.resources.legal_publisher_legal_form
import io.jauge.feature.account.resources.legal_publisher_name
import io.jauge.feature.account.resources.legal_publisher_registration
import io.jauge.feature.account.resources.legal_publisher_share_capital
import io.jauge.feature.account.resources.legal_publisher_vat_number
import io.jauge.feature.account.resources.notice_1_body
import io.jauge.feature.account.resources.notice_1_title
import io.jauge.feature.account.resources.notice_2_body
import io.jauge.feature.account.resources.notice_2_title
import io.jauge.feature.account.resources.notice_3_body
import io.jauge.feature.account.resources.notice_3_title
import io.jauge.feature.account.resources.notice_4_body
import io.jauge.feature.account.resources.notice_4_title
import io.jauge.feature.account.resources.notice_5_body
import io.jauge.feature.account.resources.notice_5_title
import org.jetbrains.compose.resources.stringResource

/**
 * Legal notice: publisher, publication director, host, intellectual property. Company details come from `legal_identity.xml`; values still to fill are shown as such, never invented.
 *
 * @param onBack called when the user leaves the screen.
 */
@Composable
fun LegalNoticeScreen(onBack: () -> Unit) {
    LegalDocument(
        title = stringResource(Res.string.legal_notice_title),
        introduction = null,
        sections = listOf(
            LegalSection(
                stringResource(Res.string.notice_1_title),
                stringResource(
                    Res.string.notice_1_body,
                    stringResource(Res.string.legal_publisher_name),
                    stringResource(Res.string.legal_publisher_legal_form),
                    stringResource(Res.string.legal_publisher_share_capital),
                    stringResource(Res.string.legal_publisher_registration),
                    stringResource(Res.string.legal_publisher_address),
                    stringResource(Res.string.legal_publisher_vat_number),
                ),
            ),
            LegalSection(stringResource(Res.string.notice_2_title), stringResource(Res.string.notice_2_body, stringResource(Res.string.legal_publication_director))),
            LegalSection(stringResource(Res.string.notice_3_title), stringResource(Res.string.notice_3_body, stringResource(Res.string.legal_host_address))),
            LegalSection(stringResource(Res.string.notice_4_title), stringResource(Res.string.notice_4_body)),
            LegalSection(stringResource(Res.string.notice_5_title), stringResource(Res.string.notice_5_body)),
        ),
        onBack = onBack,
    )
}
