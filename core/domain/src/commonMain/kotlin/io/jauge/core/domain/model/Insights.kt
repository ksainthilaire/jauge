package io.jauge.core.domain.model

import kotlinx.datetime.LocalDate

/** Where the salary of an offer sits compared with the local median. */
enum class SalaryPosition {
    /** Below the median. */
    BELOW_MEDIAN,

    /** Around the median. */
    AROUND_MEDIAN,

    /** Above the median. */
    ABOVE_MEDIAN,
}

/**
 * Salary of the position in the area (mockup 05, "Salaires").
 *
 * @property areaName city or area of the comparison ("Lyon").
 * @property offerCount number of comparable offers showing a salary.
 * @property scaleMinimumThousands left end of the scale.
 * @property scaleMaximumThousands right end of the scale.
 * @property middleHalf range where half of the offers pay.
 * @property medianThousands local median.
 * @property offerRange estimated range of this offer, null when unknown.
 * @property negotiationTargetThousands suggested amount to ask for, null when not relevant.
 */
data class SalaryInsight(
    val areaName: String,
    val offerCount: Int,
    val scaleMinimumThousands: Int,
    val scaleMaximumThousands: Int,
    val middleHalf: SalaryRange,
    val medianThousands: Int,
    val offerRange: SalaryRange?,
    val negotiationTargetThousands: Int?,
) {
    /** Position of the offer compared with the median, null when the offer range is unknown. */
    val position: SalaryPosition?
        get() {
            val range = offerRange ?: return null
            return when {
                range.middleThousands > medianThousands + 1 -> SalaryPosition.ABOVE_MEDIAN
                range.middleThousands < medianThousands - 1 -> SalaryPosition.BELOW_MEDIAN
                else -> SalaryPosition.AROUND_MEDIAN
            }
        }
}

/**
 * How often a skill is asked for by twin offers, and whether the user has it.
 *
 * @property name name of the skill.
 * @property sharePercent share of twin offers asking for it.
 * @property isOwned true when the user has it, false when it is "à ajouter", null when unknown.
 */
data class SkillDemand(
    val name: String,
    val sharePercent: Int,
    val isOwned: Boolean?,
)

/**
 * Skills asked for by comparable offers (mockup 05, "Compétences").
 *
 * @property skills skills sorted by decreasing share.
 */
data class SkillsInsight(val skills: List<SkillDemand>)

/**
 * One month of the trend chart.
 *
 * @property month first day of the month.
 * @property applicantsPerOffer average applicants per offer that month.
 */
data class TrendPoint(
    val month: LocalDate,
    val applicantsPerOffer: Int,
)

/**
 * Applicants per offer over 12 months (mockup 05, "Tendance").
 *
 * @property points monthly points, oldest first.
 * @property changeThreeMonthsPercent change over the last 3 months, in percent.
 */
data class TrendInsight(
    val points: List<TrendPoint>,
    val changeThreeMonthsPercent: Int,
)

/**
 * How an estimate was made (mockup 09).
 *
 * @property applicants the estimate explained.
 * @property reliability its reliability.
 * @property reliabilityReasons why this reliability, written by the API in the user language.
 * @property comparableOfferCount comparable offers used.
 * @property regionName region of the comparison.
 * @property salarySourceCount comparable offers showing a salary.
 */
data class Methodology(
    val applicants: ApplicantEstimate,
    val reliability: Reliability,
    val reliabilityReasons: List<String>,
    val comparableOfferCount: Int,
    val regionName: String,
    val salarySourceCount: Int,
)
