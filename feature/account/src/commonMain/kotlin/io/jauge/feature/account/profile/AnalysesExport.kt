package io.jauge.feature.account.profile

import io.jauge.core.domain.model.AnalysisSummary
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/**
 * Builds the export of the analyses ("Exporter mes analyses", right to portability): one line per analysis,
 * separated by semicolons, readable by any spreadsheet.
 *
 * @param header first line, from `strings.xml`.
 * @param analyses analyses to export.
 * @param timeZone time zone used for the dates.
 */
fun analysesToCsv(header: String, analyses: List<AnalysisSummary>, timeZone: TimeZone = TimeZone.currentSystemDefault()): String =
    buildString {
        appendLine(header)
        analyses.forEach { analysis ->
            val date = analysis.createdAt.toLocalDateTime(timeZone).date
            appendLine(listOf(date.toString(), analysis.jobTitle, analysis.company.orEmpty(), analysis.place, analysis.score.toString()).joinToString(";") { cell(it) })
        }
    }

private fun cell(value: String): String {
    val clean = value.replace('\n', ' ').trim()
    return if (clean.contains(';') || clean.contains('"')) "\"" + clean.replace("\"", "\"\"") + "\"" else clean
}
