package io.jauge.core.domain.model

/**
 * Competition level of an offer, from its score between 0 and 100.
 */
enum class CompetitionLevel {
    /** Score 0 to 33: few applicants. */
    CALM,

    /** Score 34 to 66: medium competition. */
    DISPUTED,

    /** Score 67 to 100: many applicants. */
    CROWDED,
    ;

    /** Thresholds of the levels. */
    companion object {
        /** Lowest score of [DISPUTED]. */
        const val DISPUTED_FROM = 34

        /** Lowest score of [CROWDED]. */
        const val CROWDED_FROM = 67

        /**
         * Level of a score (values outside 0 to 100 are clamped).
         *
         * @param score the competition score.
         */
        fun fromScore(score: Int): CompetitionLevel = when (score.coerceIn(0, 100)) {
            in 0 until DISPUTED_FROM -> CALM
            in DISPUTED_FROM until CROWDED_FROM -> DISPUTED
            else -> CROWDED
        }
    }
}

/**
 * How reliable an estimate is, with the usual gap between the estimate and the real number (mockup 09).
 *
 * @property typicalMarginPercent usual error margin, in percent.
 */
enum class Reliability(val typicalMarginPercent: Int) {
    /** Usual gap of 15 %. */
    HIGH(15),

    /** Usual gap of 25 %. */
    MEDIUM(25),

    /** Usual gap of 40 %. */
    LOW(40),
}

/**
 * Estimated number of applicants with its range ("≈ 210, fourchette 160 à 260").
 *
 * @property estimate most likely number.
 * @property low lower bound of the range.
 * @property high upper bound of the range.
 */
data class ApplicantEstimate(
    val estimate: Int,
    val low: Int,
    val high: Int,
)

/**
 * Annual gross salary range in thousands of euros ("36 à 41 k€").
 *
 * @property minimumThousands lower bound.
 * @property maximumThousands upper bound.
 */
data class SalaryRange(
    val minimumThousands: Int,
    val maximumThousands: Int,
) {
    /** Middle of the range. */
    val middleThousands: Double
        get() = (minimumThousands + maximumThousands) / 2.0

    /** True when the bounds are positive and ordered. */
    val isValid: Boolean
        get() = minimumThousands in 1..maximumThousands
}
