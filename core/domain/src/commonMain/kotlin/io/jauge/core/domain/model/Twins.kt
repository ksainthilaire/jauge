package io.jauge.core.domain.model

import kotlinx.datetime.LocalDate

/** Sort orders of the twin offers (mockup 06). */
enum class TwinSort {
    /** Fewest applicants first. */
    LEAST_DISPUTED,

    /** Highest salary first. */
    BEST_PAID,

    /** Newest first. */
    MOST_RECENT,
}

/**
 * Same job, same sector, elsewhere (mockup 06).
 *
 * @property id identifier of the twin offer.
 * @property jobTitle title of the position.
 * @property company company name.
 * @property place city or district.
 * @property salary estimated or displayed salary, if known.
 * @property estimatedApplicants estimated applicants.
 * @property score competition score.
 * @property distanceKilometers distance from the analyzed offer.
 * @property publishedOn publication date, if known.
 * @property url link of the offer, if any.
 */
data class TwinOffer(
    val id: String,
    val jobTitle: String,
    val company: String,
    val place: String,
    val salary: SalaryRange?,
    val estimatedApplicants: Int,
    val score: Int,
    val distanceKilometers: Int,
    val publishedOn: LocalDate?,
    val url: String?,
) {
    /** Competition level of [score]. */
    val level: CompetitionLevel
        get() = CompetitionLevel.fromScore(score)
}

/**
 * Twin offers of an analysis.
 *
 * @property areaName area searched ("Lyon + 30 km").
 * @property radiusKilometers search radius.
 * @property offers the offers in the requested order.
 */
data class TwinOffers(
    val areaName: String,
    val radiusKilometers: Int,
    val offers: List<TwinOffer>,
)

/**
 * "Calm" alert: notify the user when a twin offer goes under [threshold] (mockup 06).
 *
 * @property id identifier of the alert.
 * @property analysisId analysis whose twins are watched.
 * @property threshold competition score under which the user is notified.
 * @property isActive true while the alert is on.
 */
data class CalmAlert(
    val id: String,
    val analysisId: String,
    val threshold: Int,
    val isActive: Boolean,
) {
    /** Default values. */
    companion object {
        /** Threshold shown in the mockups ("passe sous 30"). */
        const val DEFAULT_THRESHOLD = 30
    }
}
