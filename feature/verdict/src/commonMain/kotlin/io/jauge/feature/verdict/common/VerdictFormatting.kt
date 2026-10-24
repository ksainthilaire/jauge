package io.jauge.feature.verdict.common

import androidx.compose.runtime.Composable
import androidx.compose.ui.text.intl.Locale
import io.jauge.core.domain.model.ContractType
import io.jauge.core.domain.model.Reliability
import io.jauge.core.domain.model.SalaryRange
import io.jauge.feature.verdict.resources.Res
import io.jauge.feature.verdict.resources.contract_apprenticeship
import io.jauge.feature.verdict.resources.contract_fixed_term
import io.jauge.feature.verdict.resources.contract_freelance
import io.jauge.feature.verdict.resources.contract_internship
import io.jauge.feature.verdict.resources.contract_permanent
import io.jauge.feature.verdict.resources.contract_temporary
import io.jauge.feature.verdict.resources.percent_signed
import io.jauge.feature.verdict.resources.reliability_high
import io.jauge.feature.verdict.resources.reliability_low
import io.jauge.feature.verdict.resources.reliability_medium
import io.jauge.feature.verdict.resources.salary_range_thousands
import io.jauge.feature.verdict.resources.separator_dot
import org.jetbrains.compose.resources.stringResource
import kotlin.math.abs

/** Narrow no-break space, the French thousands separator ("1 284"). */
private const val NARROW_NO_BREAK_SPACE = ' '

/**
 * Formats a whole number with the thousands separator of the device language ("1 284" in French, "1,284" in
 * English).
 *
 * @param value the number.
 * @param languageCode language of the device, `fr` by default for previews.
 */
fun formatGrouped(value: Int, languageCode: String = Locale.current.language): String {
    val separator = if (languageCode == "fr") NARROW_NO_BREAK_SPACE else ','
    val digits = abs(value).toString()
    val grouped = digits.reversed().chunked(3).joinToString(separator.toString()).reversed()
    return if (value < 0) "-$grouped" else grouped
}

/**
 * Signed number of a change in percent ("+18", "-4", "0").
 *
 * @param value the change.
 */
fun formatSigned(value: Int): String = if (value > 0) "+$value" else value.toString()

/** Change in percent with its sign ("+18 %"). */
@Composable
fun signedPercent(value: Int): String = stringResource(Res.string.percent_signed, formatSigned(value))

/** Salary range in thousands of euros ("36-41 k€"). */
@Composable
fun SalaryRange.label(): String = stringResource(Res.string.salary_range_thousands, minimumThousands, maximumThousands)

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

/** Label of a reliability ("Fiabilité moyenne"). */
@Composable
fun Reliability.label(): String = when (this) {
    Reliability.HIGH -> stringResource(Res.string.reliability_high)
    Reliability.MEDIUM -> stringResource(Res.string.reliability_medium)
    Reliability.LOW -> stringResource(Res.string.reliability_low)
}

/**
 * Joins the non blank parts with the middle dot separator ("Atelier Sorel · Lyon 3e · CDI").
 *
 * @param parts the parts, null or blank ones are skipped.
 */
@Composable
fun joinWithDots(vararg parts: String?): String =
    parts.filterNot { it.isNullOrBlank() }.joinToString(stringResource(Res.string.separator_dot))
