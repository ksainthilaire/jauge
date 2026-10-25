package io.jauge.feature.verdict

import io.jauge.core.domain.error.JaugeError
import io.jauge.core.domain.model.Analysis
import io.jauge.core.domain.model.AnalysisAdvice
import io.jauge.core.domain.model.AnalysisProgress
import io.jauge.core.domain.model.AnalysisSummary
import io.jauge.core.domain.model.ApplicantEstimate
import io.jauge.core.domain.model.ContractType
import io.jauge.core.domain.model.JobOffer
import io.jauge.core.domain.model.Methodology
import io.jauge.core.domain.model.OfferSource
import io.jauge.core.domain.model.Outcome
import io.jauge.core.domain.model.Reliability
import io.jauge.core.domain.model.SalaryInsight
import io.jauge.core.domain.model.SalaryRange
import io.jauge.core.domain.model.SkillDemand
import io.jauge.core.domain.model.SkillsInsight
import io.jauge.core.domain.model.TimingAdvice
import io.jauge.core.domain.model.TrendInsight
import io.jauge.core.domain.model.TrendPoint
import io.jauge.core.domain.repository.AnalysisRepository
import io.jauge.core.domain.repository.InsightRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus
import kotlin.time.Instant

/** Data of the mockups, shared by the view model tests and the screenshot tests. */
object SampleData {
    val analysis = Analysis(
        id = "analysis-marketing",
        createdAt = Instant.parse("2026-10-23T08:00:00Z"),
        offer = JobOffer(
            jobTitle = "Chargée de marketing digital",
            company = "Atelier Sorel",
            city = "Lyon 3e",
            contractType = ContractType.PERMANENT,
            workMode = "Hybride 2 j",
            publishedOn = LocalDate(2026, 10, 21),
            displayedSalary = null,
            skills = listOf("Google Ads", "GA4"),
            sourceUrl = null,
        ),
        score = 78,
        percentileInRegion = 84,
        regionName = "Rhône",
        applicants = ApplicantEstimate(210, 160, 260),
        reliability = Reliability.MEDIUM,
        estimatedSalary = SalaryRange(36, 41),
        twinOfferCount = 46,
        twinRadiusKilometers = 30,
        trendThreeMonthsPercent = 18,
        comparableOfferCount = 1_284,
        timingAdvice = TimingAdvice(DayOfWeek.THURSDAY, 70, 5),
    )

    /** Analysis with the advice written by the server. */
    val analysisWithAdvice = analysis.copy(
        advice = AnalysisAdvice(
            text = "Postule avant jeudi. 70 % des candidatures arrivent dans les 5 premiers jours : mets en avant tes " +
                "résultats chiffrés sur les campagnes payantes.",
            negotiationArgument = "L'offre n'affiche pas de salaire et la médiane de Lyon est de 37 k€ : demande 40 k€.",
            missingSkills = listOf("HubSpot", "Figma"),
        ),
    )

    val salary = SalaryInsight(
        areaName = "Lyon",
        offerCount = 412,
        scaleMinimumThousands = 30,
        scaleMaximumThousands = 48,
        middleHalf = SalaryRange(33, 44),
        medianThousands = 37,
        offerRange = SalaryRange(36, 41),
        negotiationTargetThousands = 40,
    )

    val skills = SkillsInsight(
        listOf(
            SkillDemand("Google Ads", 82, true),
            SkillDemand("GA4", 74, true),
            SkillDemand("Meta Ads", 69, true),
            SkillDemand("Looker Studio", 41, false),
            SkillDemand("Brevo / emailing", 37, true),
        ),
    )

    val trend = TrendInsight(
        points = listOf(150, 154, 143, 159, 155, 164, 161, 172, 181, 190, 204, 215).mapIndexed { index, value ->
            TrendPoint(LocalDate(2025, 11, 1).plus(DatePeriod(months = index)), value)
        },
        changeThreeMonthsPercent = 18,
    )

    val methodology = Methodology(
        applicants = ApplicantEstimate(210, 160, 260),
        reliability = Reliability.MEDIUM,
        reliabilityReasons = listOf("Offre en ligne depuis 2 jours seulement.", "Pas de salaire affiché."),
        comparableOfferCount = 1_284,
        regionName = "Rhône",
        salarySourceCount = 412,
    )
}

/** Analysis repository answering with [outcome]. */
class FakeAnalysisRepository(var outcome: Outcome<Analysis> = Outcome.Success(SampleData.analysis)) : AnalysisRepository {
    var loadCount = 0

    override fun analyze(source: OfferSource, requestKey: String): Flow<AnalysisProgress> = emptyFlow()

    override fun observeHistory(): Flow<List<AnalysisSummary>> = flowOf(emptyList())

    override suspend fun refreshHistory(): Outcome<Unit> = Outcome.Success(Unit)

    override suspend fun getAnalysis(analysisId: String): Outcome<Analysis> {
        loadCount++
        return outcome
    }

    override suspend fun deleteAnalysis(analysisId: String): Outcome<Unit> = Outcome.Success(Unit)

    /** Favorite identifiers. */
    val favoriteIds = kotlinx.coroutines.flow.MutableStateFlow<Set<String>>(emptySet())

    /** Error returned by the next star change (after undoing it), null for a success. */
    var favoriteError: JaugeError? = null

    override fun observeFavoriteIds(): Flow<Set<String>> = favoriteIds

    override suspend fun setFavorite(analysisId: String, isFavorite: Boolean): Outcome<Unit> {
        val before = favoriteIds.value
        favoriteIds.value = if (isFavorite) before + analysisId else before - analysisId
        favoriteError?.let {
            favoriteIds.value = before
            return Outcome.Failure(it)
        }
        return Outcome.Success(Unit)
    }
}

/** Insight repository with configurable answers. */
class FakeInsightRepository : InsightRepository {
    var salary: Outcome<SalaryInsight> = Outcome.Success(SampleData.salary)
    var skills: Outcome<SkillsInsight> = Outcome.Success(SampleData.skills)
    var trend: Outcome<TrendInsight> = Outcome.Success(SampleData.trend)
    var methodology: Outcome<Methodology> = Outcome.Success(SampleData.methodology)
    var feedback: Outcome<Unit> = Outcome.Success(Unit)
    val submitted = mutableListOf<Int>()

    override suspend fun getSalary(analysisId: String) = salary

    override suspend fun getSkills(analysisId: String) = skills

    override suspend fun getTrend(analysisId: String) = trend

    override suspend fun getMethodology(analysisId: String) = methodology

    override suspend fun submitActualApplicants(analysisId: String, actualApplicants: Int): Outcome<Unit> {
        submitted += actualApplicants
        return feedback
    }
}

/** A failure used by the tests. */
val noConnection: Outcome<Nothing> = Outcome.Failure(JaugeError.NoConnection)
