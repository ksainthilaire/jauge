package io.jauge.core.network.payload

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Body of `POST /v1/analyses` for a link or a manual entry (images are sent as multipart).
 *
 * @property type `link` or `manual`.
 * @property url link of the offer, for `link`.
 * @property jobTitle job title, for `manual`.
 * @property city city, for `manual`.
 */
@Serializable
data class CreateAnalysisRequestPayload(
    val type: String,
    val url: String? = null,
    val jobTitle: String? = null,
    val city: String? = null,
) {
    /** Values of [type]. */
    companion object {
        /** Analysis of a link. */
        const val TYPE_LINK = "link"

        /** Analysis of a manual entry. */
        const val TYPE_MANUAL = "manual"
    }
}

/**
 * Salary range in thousands of euros, annual gross.
 *
 * @property minimum lower bound.
 * @property maximum upper bound.
 */
@Serializable
data class SalaryRangePayload(val minimum: Int, val maximum: Int)

/**
 * Applicants estimate.
 *
 * @property estimate most likely number.
 * @property low lower bound.
 * @property high upper bound.
 */
@Serializable
data class ApplicantsPayload(val estimate: Int, val low: Int, val high: Int)

/**
 * Offer as read by the API.
 *
 * @property jobTitle title of the position.
 * @property company company name.
 * @property city city and district.
 * @property contractType `permanent`, `fixed_term`, `apprenticeship`, `internship`, `freelance`, `temporary`, `other`.
 * @property workMode remote policy as written.
 * @property publishedOn publication date, ISO 8601 (`2026-10-21`).
 * @property displayedSalary salary written in the offer.
 * @property skills skills found.
 * @property sourceUrl link of the offer.
 */
@Serializable
data class OfferPayload(
    val jobTitle: String,
    val company: String? = null,
    val city: String,
    val contractType: String = "other",
    val workMode: String? = null,
    val publishedOn: String? = null,
    val displayedSalary: SalaryRangePayload? = null,
    val skills: List<String> = emptyList(),
    val sourceUrl: String? = null,
)

/**
 * Timing advice.
 *
 * @property applyBefore day of week in English upper case (`THURSDAY`).
 * @property sharePercent share of applications received within [withinDays].
 * @property withinDays number of days.
 */
@Serializable
data class TimingAdvicePayload(val applyBefore: String, val sharePercent: Int, val withinDays: Int)

/**
 * One reading step.
 *
 * @property kind `position_and_place`, `skills`, `comparison`, `applicants`.
 * @property status `pending`, `running`, `done`.
 * @property detail what was found.
 */
@Serializable
data class ReadingStepPayload(val kind: String, val status: String, val detail: String? = null)

/**
 * Failure of an analysis.
 *
 * @property code machine readable code, for example `unreadable_offer`.
 * @property recognizedCompany company that could still be read.
 */
@Serializable
data class AnalysisFailurePayload(val code: String, val recognizedCompany: String? = null)

/**
 * Result part of a completed analysis.
 *
 * @property score competition score, 0 to 100.
 * @property percentileInRegion share of offers of the region with less competition.
 * @property regionName region of the comparison.
 * @property applicants applicants estimate.
 * @property reliability `high`, `medium` or `low`.
 * @property estimatedSalary estimated salary.
 * @property twinOfferCount number of twin offers.
 * @property twinRadiusKilometers radius of the twin search.
 * @property trendThreeMonthsPercent change over 3 months.
 * @property comparableOfferCount comparable offers used.
 * @property timingAdvice advice about when to apply.
 */
@Serializable
data class AnalysisResultPayload(
    val score: Int,
    val percentileInRegion: Int,
    val regionName: String,
    val applicants: ApplicantsPayload,
    val reliability: String,
    val estimatedSalary: SalaryRangePayload? = null,
    val twinOfferCount: Int = 0,
    val twinRadiusKilometers: Int = 30,
    val trendThreeMonthsPercent: Int = 0,
    val comparableOfferCount: Int = 0,
    val timingAdvice: TimingAdvicePayload? = null,
)

/**
 * An analysis as returned by `POST /v1/analyses` and `GET /v1/analyses/{id}`.
 *
 * @property id identifier.
 * @property status `processing`, `completed` or `failed`.
 * @property createdAt creation time, ISO 8601.
 * @property progressPercent progress while processing.
 * @property steps reading steps while processing.
 * @property publishedDaysAgo publication age found so far.
 * @property hasDisplayedSalary whether the offer shows a salary, once known.
 * @property offer the offer, once read.
 * @property result the result, when completed.
 * @property failure the failure, when failed.
 * @property isFavorite true when the user marked the analysis as a favorite.
 */
@Serializable
data class AnalysisPayload(
    val id: String,
    val status: String,
    val createdAt: String,
    val progressPercent: Int = 0,
    val steps: List<ReadingStepPayload> = emptyList(),
    val publishedDaysAgo: Int? = null,
    val hasDisplayedSalary: Boolean? = null,
    val offer: OfferPayload? = null,
    val result: AnalysisResultPayload? = null,
    val failure: AnalysisFailurePayload? = null,
    val isFavorite: Boolean = false,
) {
    /** Values of [status]. */
    companion object {
        /** Still running: poll again. */
        const val STATUS_PROCESSING = "processing"

        /** Done. */
        const val STATUS_COMPLETED = "completed"

        /** Failed, see [failure]. */
        const val STATUS_FAILED = "failed"
    }
}

/**
 * Short analysis for lists.
 *
 * @property id identifier.
 * @property createdAt creation time, ISO 8601.
 * @property jobTitle title of the position.
 * @property company company name.
 * @property place city or district.
 * @property score competition score.
 * @property isFavorite true when the user marked the analysis as a favorite.
 */
@Serializable
data class AnalysisSummaryPayload(
    val id: String,
    val createdAt: String,
    val jobTitle: String,
    val company: String? = null,
    val place: String,
    val score: Int,
    val isFavorite: Boolean = false,
)

/**
 * Page of `GET /v1/analyses`.
 *
 * @property items analyses of the page, newest first.
 * @property nextCursor cursor of the next page, null on the last page.
 */
@Serializable
data class AnalysisPagePayload(
    val items: List<AnalysisSummaryPayload>,
    val nextCursor: String? = null,
)

/**
 * Body of `POST /v1/analyses/{id}/feedback`.
 *
 * @property actualApplicants real number of applicants known by the user.
 */
@Serializable
data class ApplicantFeedbackRequestPayload(val actualApplicants: Int)
