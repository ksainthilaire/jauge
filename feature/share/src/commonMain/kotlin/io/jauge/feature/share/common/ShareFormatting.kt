package io.jauge.feature.share.common

import androidx.compose.runtime.Composable
import androidx.compose.ui.text.intl.Locale
import io.jauge.core.domain.model.ContractType
import io.jauge.feature.share.resources.Res
import io.jauge.feature.share.resources.contract_apprenticeship
import io.jauge.feature.share.resources.contract_fixed_term
import io.jauge.feature.share.resources.contract_freelance
import io.jauge.feature.share.resources.contract_internship
import io.jauge.feature.share.resources.contract_permanent
import io.jauge.feature.share.resources.contract_temporary
import io.jauge.feature.share.resources.separator_dot
import org.jetbrains.compose.resources.stringResource
import kotlin.math.abs

/**
 * Formats a whole number with the thousands separator of the device language ("1 284" in French, "1,284" in
 * English).
 *
 * @param value the number.
 * @param languageCode language of the device.
 */
fun formatGrouped(value: Int, languageCode: String = Locale.current.language): String {
    val separator = if (languageCode == "fr") " " else ","
    val grouped = abs(value).toString().reversed().chunked(3).joinToString(separator).reversed()
    return if (value < 0) "-$grouped" else grouped
}

/**
 * Signed number of a change in percent ("+18", "-4", "0").
 *
 * @param value the change.
 */
fun formatSigned(value: Int): String = if (value > 0) "+$value" else value.toString()

/** Short label of a contract type ("CDI"), null for an unknown contract. */
@Composable
fun ContractType.labelOrNull(): String? = when (this) {
    ContractType.PERMANENT -> stringResource(Res.string.contract_permanent)
    ContractType.FIXED_TERM -> stringResource(Res.string.contract_fixed_term)
    ContractType.APPRENTICESHIP -> stringResource(Res.string.contract_apprenticeship)
    ContractType.INTERNSHIP -> stringResource(Res.string.contract_internship)
    ContractType.FREELANCE -> stringResource(Res.string.contract_freelance)
    ContractType.TEMPORARY -> stringResource(Res.string.contract_temporary)
    ContractType.OTHER -> null
}

/**
 * Joins the non blank parts with the middle dot separator ("Atelier Sorel · Lyon 3e · CDI").
 *
 * @param parts the parts, null or blank ones are skipped.
 */
@Composable
fun joinWithDots(vararg parts: String?): String =
    parts.filterNot { it.isNullOrBlank() }.joinToString(stringResource(Res.string.separator_dot))
