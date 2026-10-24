package io.jauge.core.domain.usecase

import io.jauge.core.domain.error.JaugeError
import io.jauge.core.domain.model.Methodology
import io.jauge.core.domain.model.Outcome
import io.jauge.core.domain.model.SalaryInsight
import io.jauge.core.domain.model.SkillsInsight
import io.jauge.core.domain.model.TrendInsight
import io.jauge.core.domain.model.map
import io.jauge.core.domain.repository.InsightRepository

/**
 * Salary of the position (mockup 05).
 *
 * @param repository insights repository.
 */
class GetSalaryInsightUseCase(private val repository: InsightRepository) {
    /**
     * Loads the salary insight. An offer range that is not valid is dropped (an empty field rather than a wrong value).
     *
     * @param analysisId identifier of the analysis.
     */
    suspend operator fun invoke(analysisId: String): Outcome<SalaryInsight> =
        repository.getSalary(analysisId).map { insight ->
            if (insight.offerRange?.isValid == false) insight.copy(offerRange = null) else insight
        }
}

/**
 * Skills asked for by comparable offers (mockup 05).
 *
 * @param repository insights repository.
 */
class GetSkillsInsightUseCase(private val repository: InsightRepository) {
    /**
     * Loads the skills, sorted by decreasing share and with shares kept between 0 and 100.
     *
     * @param analysisId identifier of the analysis.
     */
    suspend operator fun invoke(analysisId: String): Outcome<SkillsInsight> =
        repository.getSkills(analysisId).map { insight ->
            SkillsInsight(
                insight.skills
                    .map { it.copy(sharePercent = it.sharePercent.coerceIn(0, 100)) }
                    .sortedByDescending { it.sharePercent },
            )
        }
}

/**
 * Applicants per offer over 12 months (mockup 05).
 *
 * @param repository insights repository.
 */
class GetTrendInsightUseCase(private val repository: InsightRepository) {
    /**
     * Loads the trend, oldest month first.
     *
     * @param analysisId identifier of the analysis.
     */
    suspend operator fun invoke(analysisId: String): Outcome<TrendInsight> =
        repository.getTrend(analysisId).map { trend -> trend.copy(points = trend.points.sortedBy { it.month }) }
}

/**
 * How the estimate was made (mockup 09).
 *
 * @param repository insights repository.
 */
class GetMethodologyUseCase(private val repository: InsightRepository) {
    /**
     * Loads the methodology of an analysis.
     *
     * @param analysisId identifier of the analysis.
     */
    suspend operator fun invoke(analysisId: String): Outcome<Methodology> = repository.getMethodology(analysisId)
}

/**
 * Sends the real number of applicants known by the user (mockup 09, "Dis-le-nous").
 *
 * @param repository insights repository.
 */
class SubmitActualApplicantsUseCase(private val repository: InsightRepository) {
    /**
     * Sends the number when it is plausible (1 to 100 000).
     *
     * @param analysisId identifier of the analysis.
     * @param actualApplicants number given by the user.
     */
    suspend operator fun invoke(analysisId: String, actualApplicants: Int): Outcome<Unit> =
        if (actualApplicants !in 1..MAXIMUM_APPLICANTS) {
            Outcome.Failure(JaugeError.InvalidData)
        } else {
            repository.submitActualApplicants(analysisId, actualApplicants)
        }

    private companion object {
        const val MAXIMUM_APPLICANTS = 100_000
    }
}
