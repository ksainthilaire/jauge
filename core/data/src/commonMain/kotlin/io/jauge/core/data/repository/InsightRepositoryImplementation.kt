package io.jauge.core.data.repository

import io.jauge.core.data.error.outcomeOf
import io.jauge.core.data.mapper.toDomain
import io.jauge.core.domain.model.Methodology
import io.jauge.core.domain.model.Outcome
import io.jauge.core.domain.model.SalaryInsight
import io.jauge.core.domain.model.SkillsInsight
import io.jauge.core.domain.model.TrendInsight
import io.jauge.core.domain.repository.InsightRepository
import io.jauge.core.data.preferences.SearchAreaSource
import io.jauge.core.data.preferences.toPayload
import io.jauge.core.network.JaugeApi
import io.jauge.core.network.payload.ApplicantFeedbackRequestPayload

/**
 * [InsightRepository] backed by the Jauge API.
 *
 * @param api the API client.
 * @param searchArea search area of the user, sent with the salaries.
 */
class InsightRepositoryImplementation(
    private val api: JaugeApi,
    private val searchArea: SearchAreaSource = SearchAreaSource.NONE,
) : InsightRepository {

    override suspend fun getSalary(analysisId: String): Outcome<SalaryInsight> = outcomeOf { api.getSalary(analysisId, searchArea.current()?.toPayload()).toDomain() }

    override suspend fun getSkills(analysisId: String): Outcome<SkillsInsight> = outcomeOf { api.getSkills(analysisId).toDomain() }

    override suspend fun getTrend(analysisId: String): Outcome<TrendInsight> = outcomeOf { api.getTrend(analysisId).toDomain() }

    override suspend fun getMethodology(analysisId: String): Outcome<Methodology> =
        outcomeOf { api.getMethodology(analysisId).toDomain() }

    override suspend fun submitActualApplicants(analysisId: String, actualApplicants: Int): Outcome<Unit> =
        outcomeOf { api.sendApplicantFeedback(analysisId, ApplicantFeedbackRequestPayload(actualApplicants)) }
}
