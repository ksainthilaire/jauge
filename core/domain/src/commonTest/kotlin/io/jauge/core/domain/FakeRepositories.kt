package io.jauge.core.domain

import io.jauge.core.domain.model.AnalysisProgress
import io.jauge.core.domain.model.AnalysisSummary
import io.jauge.core.domain.model.CalmAlert
import io.jauge.core.domain.model.OfferSource
import io.jauge.core.domain.model.Outcome
import io.jauge.core.domain.model.Share
import io.jauge.core.domain.model.ShareOptions
import io.jauge.core.domain.model.TwinOffers
import io.jauge.core.domain.model.TwinSort
import io.jauge.core.domain.repository.AnalysisRepository
import io.jauge.core.domain.repository.ShareRepository
import io.jauge.core.domain.repository.TwinRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlin.time.Instant

class FakeAnalysisRepository : AnalysisRepository {
    val receivedSources = mutableListOf<OfferSource>()

    override fun analyze(source: OfferSource, requestKey: String): Flow<AnalysisProgress> {
        receivedSources += source
        return flowOf(AnalysisProgress.Reading("new", 20, emptyList(), null, null))
    }

    override fun observeHistory(): Flow<List<AnalysisSummary>> = MutableStateFlow(emptyList())

    override suspend fun refreshHistory(): Outcome<Unit> = Outcome.Success(Unit)

    override suspend fun getAnalysis(analysisId: String) = throw UnsupportedOperationException()

    override suspend fun deleteAnalysis(analysisId: String): Outcome<Unit> = Outcome.Success(Unit)
}

class FakeTwinRepository(private val twins: TwinOffers) : TwinRepository {
    var createdThreshold: Int? = null

    override suspend fun getTwinOffers(analysisId: String, sort: TwinSort): Outcome<TwinOffers> = Outcome.Success(twins)

    override fun observeAlerts(): Flow<List<CalmAlert>> = MutableStateFlow(emptyList())

    override suspend fun refreshAlerts(): Outcome<Unit> = Outcome.Success(Unit)

    override suspend fun createAlert(analysisId: String, threshold: Int): Outcome<CalmAlert> {
        createdThreshold = threshold
        return Outcome.Success(CalmAlert("alert", analysisId, threshold, true))
    }

    override suspend fun deleteAlert(alertId: String): Outcome<Unit> = Outcome.Success(Unit)
}

class FakeShareRepository : ShareRepository {
    override suspend fun createShare(analysisId: String, options: ShareOptions): Outcome<Share> =
        Outcome.Success(Share("share", analysisId, "https://example.com/wrong", null, options, Instant.fromEpochSeconds(0)))

    override suspend fun getShares(): Outcome<List<Share>> = Outcome.Success(
        listOf(
            Share("old", "a", "u", null, ShareOptions(), Instant.fromEpochSeconds(10)),
            Share("new", "b", "u", null, ShareOptions(), Instant.fromEpochSeconds(20)),
        ),
    )

    override suspend fun deleteShare(shareId: String): Outcome<Unit> = Outcome.Success(Unit)
}
