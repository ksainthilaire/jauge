package io.jauge.feature.twins

import io.jauge.core.domain.model.AnalysisProgress
import io.jauge.core.domain.model.AnalysisSummary
import io.jauge.core.domain.model.CalmAlert
import io.jauge.core.domain.model.OfferSource
import io.jauge.core.domain.model.Outcome
import io.jauge.core.domain.model.SalaryRange
import io.jauge.core.domain.model.TwinOffer
import io.jauge.core.domain.model.TwinOffers
import io.jauge.core.domain.model.TwinSort
import io.jauge.core.domain.repository.AnalysisRepository
import io.jauge.core.domain.repository.TwinRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.datetime.LocalDate
import kotlin.time.Instant

/** Twin offers of the mockups, shared by the view model tests and the screenshot tests. */
object TwinsSampleData {
    val twins = TwinOffers(
        areaName = "Lyon + 30 km",
        radiusKilometers = 30,
        offers = listOf(
            TwinOffer("brune", "Chargé·e marketing digital", "Coopérative Brune", "Écully", SalaryRange(38, 42), 35, 16, 9, LocalDate(2026, 10, 22), "https://example.com/brune"),
            TwinOffer("ferme", "Chef·fe de projet acquisition", "Ferme Numérique", "Lyon 7e", SalaryRange(35, 39), 60, 27, 4, LocalDate(2026, 10, 20), null),
            TwinOffer("mirabelle", "Growth marketer junior", "Studio Mirabelle", "Part-Dieu", SalaryRange(33, 37), 95, 41, 2, LocalDate(2026, 10, 23), null),
            TwinOffer("odier", "Marketing digital & CRM", "Maison Odier", "Caluire", SalaryRange(37, 40), 120, 52, 6, LocalDate(2026, 10, 21), null),
        ),
    )

    val latestAnalysis = AnalysisSummary("analysis-marketing", Instant.parse("2026-10-23T08:00:00Z"), "Chargée de marketing digital", "Atelier Sorel", "Lyon 3e", 78)
}

/** Twin repository keeping alerts in memory. */
class FakeTwinRepository : TwinRepository {
    var twinsOutcome: Outcome<TwinOffers> = Outcome.Success(TwinsSampleData.twins)
    var alertOutcome: Outcome<CalmAlert>? = null
    val requestedAnalyses = mutableListOf<Pair<String, TwinSort>>()
    val alerts = MutableStateFlow<List<CalmAlert>>(emptyList())
    val deletedAlerts = mutableListOf<String>()

    override suspend fun getTwinOffers(analysisId: String, sort: TwinSort): Outcome<TwinOffers> {
        requestedAnalyses += analysisId to sort
        return twinsOutcome
    }

    override fun observeAlerts(): Flow<List<CalmAlert>> = alerts

    override suspend fun refreshAlerts(): Outcome<Unit> = Outcome.Success(Unit)

    override suspend fun createAlert(analysisId: String, threshold: Int): Outcome<CalmAlert> =
        alertOutcome ?: Outcome.Success(CalmAlert("alert-1", analysisId, threshold, true)).also { created ->
            alerts.value = listOf(created.value)
        }

    override suspend fun deleteAlert(alertId: String): Outcome<Unit> {
        deletedAlerts += alertId
        alerts.value = alerts.value.filterNot { it.id == alertId }
        return Outcome.Success(Unit)
    }
}

/** Analysis repository with a configurable history. */
class FakeHistoryRepository(history: List<AnalysisSummary>) : AnalysisRepository {
    val history = MutableStateFlow(history)

    override fun analyze(source: OfferSource): Flow<AnalysisProgress> = emptyFlow()

    override fun observeHistory(): Flow<List<AnalysisSummary>> = history

    override suspend fun refreshHistory(): Outcome<Unit> = Outcome.Success(Unit)

    override suspend fun getAnalysis(analysisId: String) = throw UnsupportedOperationException()

    override suspend fun deleteAnalysis(analysisId: String): Outcome<Unit> = Outcome.Success(Unit)
}
