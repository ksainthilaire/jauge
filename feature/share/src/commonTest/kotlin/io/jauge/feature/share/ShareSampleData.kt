package io.jauge.feature.share

import io.jauge.core.domain.model.Analysis
import io.jauge.core.domain.model.AnalysisProgress
import io.jauge.core.domain.model.AnalysisSummary
import io.jauge.core.domain.model.ApplicantEstimate
import io.jauge.core.domain.model.CalmAlert
import io.jauge.core.domain.model.ContractType
import io.jauge.core.domain.model.JobOffer
import io.jauge.core.domain.model.OfferSource
import io.jauge.core.domain.model.Outcome
import io.jauge.core.domain.model.Reliability
import io.jauge.core.domain.model.SalaryRange
import io.jauge.core.domain.model.Share
import io.jauge.core.domain.model.ShareFormat
import io.jauge.core.domain.model.ShareOptions
import io.jauge.core.domain.model.TwinOffer
import io.jauge.core.domain.model.TwinOffers
import io.jauge.core.domain.model.TwinSort
import io.jauge.core.domain.repository.AnalysisRepository
import io.jauge.core.domain.repository.ShareRepository
import io.jauge.core.domain.repository.TwinRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.datetime.LocalDate
import kotlin.time.Instant

/** Data of the mockups, shared by the view model tests and the screenshot tests. */
object ShareSampleData {
    val analysis = Analysis(
        id = "analysis-marketing",
        createdAt = Instant.parse("2026-10-23T08:00:00Z"),
        offer = JobOffer("Chargée de marketing digital", "Atelier Sorel", "Lyon 3e", ContractType.PERMANENT, null, null, null, emptyList(), null),
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
        timingAdvice = null,
    )

    val calmTwin = TwinOffer("brune", "Chargé·e marketing digital", "Coopérative Brune", "Écully", SalaryRange(38, 42), 35, 16, 9, LocalDate(2026, 10, 22), null)
    val disputedTwin = TwinOffer("mirabelle", "Growth marketer junior", "Studio Mirabelle", "Part-Dieu", SalaryRange(33, 37), 95, 41, 2, null, null)

    val summary = AnalysisSummary(analysis.id, analysis.createdAt, analysis.offer.jobTitle, "Atelier Sorel", "Lyon 3e", 78)

    val share = Share("share-1", analysis.id, "https://jauge.io/a/analysis-marketing", null, ShareOptions(), Instant.parse("2026-10-23T14:00:00Z"))
    val squareShare = Share("share-2", "analysis-traffic", "https://jauge.io/a/analysis-traffic", null, ShareOptions(format = ShareFormat.SQUARE), Instant.parse("2026-10-21T09:00:00Z"))
}

/** Analysis repository answering with the sample analysis. */
class FakeAnalysisRepository(var outcome: Outcome<Analysis> = Outcome.Success(ShareSampleData.analysis)) : AnalysisRepository {
    val history = MutableStateFlow(listOf(ShareSampleData.summary))

    override fun analyze(source: OfferSource, requestKey: String): Flow<AnalysisProgress> = emptyFlow()

    override fun observeHistory(): Flow<List<AnalysisSummary>> = history

    override suspend fun refreshHistory(): Outcome<Unit> = Outcome.Success(Unit)

    override suspend fun getAnalysis(analysisId: String): Outcome<Analysis> = outcome

    override suspend fun deleteAnalysis(analysisId: String): Outcome<Unit> = Outcome.Success(Unit)
}

/** Twin repository answering with the given twins. */
class FakeTwinRepository(private val offers: List<TwinOffer>) : TwinRepository {
    override suspend fun getTwinOffers(analysisId: String, sort: TwinSort): Outcome<TwinOffers> =
        Outcome.Success(TwinOffers("Lyon + 30 km", 30, offers))

    override fun observeAlerts(): Flow<List<CalmAlert>> = emptyFlow()

    override suspend fun refreshAlerts(): Outcome<Unit> = Outcome.Success(Unit)

    override suspend fun createAlert(analysisId: String, threshold: Int) = throw UnsupportedOperationException()

    override suspend fun deleteAlert(alertId: String) = throw UnsupportedOperationException()
}

/** Share repository recording the calls. */
class FakeShareRepository : ShareRepository {
    var createOutcome: Outcome<Share>? = null
    var shares: Outcome<List<Share>> = Outcome.Success(listOf(ShareSampleData.share, ShareSampleData.squareShare))
    val createdOptions = mutableListOf<ShareOptions>()
    val deleted = mutableListOf<String>()

    override suspend fun createShare(analysisId: String, options: ShareOptions): Outcome<Share> {
        createdOptions += options
        return createOutcome ?: Outcome.Success(Share("share-new", analysisId, "https://api.example/wrong", null, options, Instant.parse("2026-10-24T08:00:00Z")))
    }

    override suspend fun getShares(): Outcome<List<Share>> = shares

    override suspend fun deleteShare(shareId: String): Outcome<Unit> {
        deleted += shareId
        return Outcome.Success(Unit)
    }
}
