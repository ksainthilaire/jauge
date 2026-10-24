package io.jauge.feature.account.components

import androidx.compose.runtime.Composable
import io.jauge.feature.account.resources.Res
import io.jauge.feature.account.resources.date_day_month
import io.jauge.feature.account.resources.month_1
import io.jauge.feature.account.resources.month_10
import io.jauge.feature.account.resources.month_11
import io.jauge.feature.account.resources.month_12
import io.jauge.feature.account.resources.month_2
import io.jauge.feature.account.resources.month_3
import io.jauge.feature.account.resources.month_4
import io.jauge.feature.account.resources.month_5
import io.jauge.feature.account.resources.month_6
import io.jauge.feature.account.resources.month_7
import io.jauge.feature.account.resources.month_8
import io.jauge.feature.account.resources.month_9
import kotlinx.datetime.LocalDate
import org.jetbrains.compose.resources.stringResource

private val monthNames = listOf(
    Res.string.month_1, Res.string.month_2, Res.string.month_3, Res.string.month_4, Res.string.month_5, Res.string.month_6,
    Res.string.month_7, Res.string.month_8, Res.string.month_9, Res.string.month_10, Res.string.month_11, Res.string.month_12,
)

/**
 * Short date in the language of the user: "26 oct." in French, "Oct 26" in English.
 *
 * @param date the date.
 */
@Composable
fun shortDate(date: LocalDate): String =
    stringResource(Res.string.date_day_month, date.day, stringResource(monthNames[date.month.ordinal]))
