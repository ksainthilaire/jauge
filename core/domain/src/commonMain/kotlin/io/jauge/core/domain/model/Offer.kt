package io.jauge.core.domain.model

import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlin.time.Instant

/** Contract types read from an offer. */
enum class ContractType {
    /** Permanent contract (CDI). */
    PERMANENT,

    /** Fixed term contract (CDD). */
    FIXED_TERM,

    /** Work and study contract (alternance). */
    APPRENTICESHIP,

    /** Internship (stage). */
    INTERNSHIP,

    /** Freelance mission. */
    FREELANCE,

    /** Temporary work (intérim). */
    TEMPORARY,

    /** Unknown or other. */
    OTHER,
}

/**
 * Job offer as read by Jauge (mockup 03).
 *
 * @property jobTitle title of the position.
 * @property company company name, null when hidden or unreadable.
 * @property city city and district ("Lyon 3e").
 * @property contractType type of contract.
 * @property workMode remote policy as written in the offer ("Hybride 2 j"), if any.
 * @property publishedOn publication date, if known.
 * @property displayedSalary salary written in the offer, null when absent ("selon profil").
 * @property skills skills found in the offer.
 * @property sourceUrl link of the offer, if it came from a link.
 */
data class JobOffer(
    val jobTitle: String,
    val company: String?,
    val city: String,
    val contractType: ContractType,
    val workMode: String?,
    val publishedOn: LocalDate?,
    val displayedSalary: SalaryRange?,
    val skills: List<String>,
    val sourceUrl: String?,
)

/**
 * Advice about when to apply ("Postule avant jeudi : 70 % des candidatures arrivent en 5 jours").
 *
 * @property applyBefore day before which the user should apply.
 * @property shareOfApplicationsPercent share of applications received within [withinDays].
 * @property withinDays number of days after publication.
 */
data class TimingAdvice(
    val applyBefore: DayOfWeek,
    val shareOfApplicationsPercent: Int,
    val withinDays: Int,
)

/**
 * Advice of the verdict written by the AI of the server, in the language of the analysis (`result.advice`). Every
 * number in it comes from the result, never from the AI.
 *
 * @property text two or three sentences: when and how to apply ("Postule avant jeudi...").
 * @property negotiationArgument one sentence for the salary negotiation, null when none.
 * @property missingSkills skills often asked by comparable offers and missing from this one (4 at most).
 */
data class AnalysisAdvice(
    val text: String,
    val negotiationArgument: String?,
    val missingSkills: List<String>,
)

/**
 * Complete analysis of an offer: the verdict (mockup 04).
 *
 * @property id identifier, also used in the share link.
 * @property createdAt creation time.
 * @property offer the offer that was read.
 * @property score competition score between 0 and 100.
 * @property percentileInRegion share of offers of the region with less competition ("plus que 84 % des offres").
 * @property regionName name of the region used for comparisons ("Rhône").
 * @property applicants estimated applicants.
 * @property reliability reliability of the estimate.
 * @property estimatedSalary estimated salary, null when it cannot be estimated.
 * @property twinOfferCount number of twin offers nearby.
 * @property twinRadiusKilometers radius used to find twin offers.
 * @property trendThreeMonthsPercent change of applicants per offer over 3 months, in percent.
 * @property comparableOfferCount number of comparable offers used ("1 284 offres du Rhône").
 * @property timingAdvice advice about when to apply, if any.
 * @property isFavorite true when the user marked the analysis as a favorite (star).
 * @property reputation what public posts say about the company, null when nothing was found.
 * @property advice advice written by the AI of the server from the computed facts, null when unavailable (the
 *   screens then keep [timingAdvice] and their own sentences).
 */
data class Analysis(
    val id: String,
    val createdAt: Instant,
    val offer: JobOffer,
    val score: Int,
    val percentileInRegion: Int,
    val regionName: String,
    val applicants: ApplicantEstimate,
    val reliability: Reliability,
    val estimatedSalary: SalaryRange?,
    val twinOfferCount: Int,
    val twinRadiusKilometers: Int,
    val trendThreeMonthsPercent: Int,
    val comparableOfferCount: Int,
    val timingAdvice: TimingAdvice?,
    val isFavorite: Boolean = false,
    val reputation: Reputation? = null,
    val advice: AnalysisAdvice? = null,
) {
    /** Competition level of [score]. */
    val level: CompetitionLevel
        get() = CompetitionLevel.fromScore(score)
}

/**
 * Short version of an analysis for lists (mockup 02, "Mes offres jaugées").
 *
 * @property id identifier of the analysis.
 * @property createdAt creation time.
 * @property jobTitle title of the position.
 * @property company company name, if known.
 * @property place city or district.
 * @property score competition score.
 * @property isFavorite true when the user marked the analysis as a favorite (star).
 */
data class AnalysisSummary(
    val id: String,
    val createdAt: Instant,
    val jobTitle: String,
    val company: String?,
    val place: String,
    val score: Int,
    val isFavorite: Boolean = false,
) {
    /** Competition level of [score]. */
    val level: CompetitionLevel
        get() = CompetitionLevel.fromScore(score)
}
