package io.jauge.core.data.repository

import io.jauge.core.data.error.outcomeOf
import io.jauge.core.data.error.toJaugeError
import io.jauge.core.data.mapper.toDomainAnalysis
import io.jauge.core.data.mapper.toDomainOrNull
import io.jauge.core.data.mapper.toProgress
import io.jauge.core.domain.error.JaugeError
import io.jauge.core.domain.model.Analysis
import io.jauge.core.domain.model.AnalysisProgress
import io.jauge.core.domain.model.AnalysisSummary
import io.jauge.core.domain.model.ImageOrigin
import io.jauge.core.domain.model.OfferSource
import io.jauge.core.domain.model.Outcome
import io.jauge.core.domain.model.map
import io.jauge.core.domain.repository.AnalysisRepository
import io.jauge.core.network.ImageOrigins
import io.jauge.core.network.JaugeApi
import io.jauge.core.network.payload.AnalysisPayload
import io.jauge.core.network.payload.CreateAnalysisRequestPayload
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.update

/**
 * [AnalysisRepository] backed by the Jauge API (real or offline). An analysis is created, then polled every
 * [pollIntervalMilliseconds] until it completes, fails or [maximumPolls] is reached (then [JaugeError.Timeout]).
 * The history is cached in memory and updated after every change.
 *
 * @param api the API client.
 * @param pollIntervalMilliseconds delay between two polls.
 * @param maximumPolls number of polls before giving up.
 */
class AnalysisRepositoryImplementation(
    private val api: JaugeApi,
    private val pollIntervalMilliseconds: Long = 1_000,
    private val maximumPolls: Int = 90,
) : AnalysisRepository {

    private val history = MutableStateFlow<List<AnalysisSummary>>(emptyList())
    private val analysesById = mutableMapOf<String, Analysis>()

    override fun analyze(source: OfferSource): Flow<AnalysisProgress> = flow {
        try {
            var payload = create(source)
            var polls = 0
            while (payload.status == AnalysisPayload.STATUS_PROCESSING) {
                emit(payload.toProgress())
                if (polls >= maximumPolls) {
                    emit(AnalysisProgress.Failed(JaugeError.Timeout))
                    return@flow
                }
                delay(pollIntervalMilliseconds)
                payload = api.getAnalysis(payload.id)
                polls += 1
            }
            val final = payload.toProgress()
            if (final is AnalysisProgress.Completed) remember(final.analysis)
            emit(final)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (failure: Exception) {
            emit(AnalysisProgress.Failed(failure.toJaugeError()))
        }
    }

    override fun observeHistory(): Flow<List<AnalysisSummary>> = history.asStateFlow()

    override suspend fun refreshHistory(): Outcome<Unit> = outcomeOf {
        val page = api.getAnalyses(cursor = null, limit = HISTORY_PAGE_SIZE)
        history.value = page.items.mapNotNull { it.toDomainOrNull() }.sortedByDescending { it.createdAt }
    }

    override suspend fun getAnalysis(analysisId: String): Outcome<Analysis> {
        analysesById[analysisId]?.let { return Outcome.Success(it) }
        return outcomeOf { api.getAnalysis(analysisId) }.let { outcome ->
            when (outcome) {
                is Outcome.Failure -> outcome
                is Outcome.Success -> when (outcome.value.status) {
                    AnalysisPayload.STATUS_COMPLETED -> outcomeOf { outcome.value.toDomainAnalysis().also(::remember) }
                    else -> Outcome.Failure(JaugeError.NotFound)
                }
            }
        }
    }

    override suspend fun deleteAnalysis(analysisId: String): Outcome<Unit> =
        outcomeOf { api.deleteAnalysis(analysisId) }.map {
            analysesById.remove(analysisId)
            history.update { list -> list.filterNot { it.id == analysisId } }
        }

    private suspend fun create(source: OfferSource): AnalysisPayload = when (source) {
        is OfferSource.Link -> api.createAnalysis(CreateAnalysisRequestPayload(type = CreateAnalysisRequestPayload.TYPE_LINK, url = source.url))
        is OfferSource.Manual -> api.createAnalysis(
            CreateAnalysisRequestPayload(type = CreateAnalysisRequestPayload.TYPE_MANUAL, jobTitle = source.jobTitle, city = source.city),
        )
        is OfferSource.Image -> api.createAnalysisFromImage(
            image = source.bytes,
            mimeType = source.mimeType,
            origin = when (source.origin) {
                ImageOrigin.SCREENSHOT -> ImageOrigins.SCREENSHOT
                ImageOrigin.PHOTO -> ImageOrigins.PHOTO
            },
        )
    }

    private fun remember(analysis: Analysis) {
        analysesById[analysis.id] = analysis
        val summary = AnalysisSummary(
            id = analysis.id,
            createdAt = analysis.createdAt,
            jobTitle = analysis.offer.jobTitle,
            company = analysis.offer.company,
            place = analysis.offer.city,
            score = analysis.score,
        )
        history.update { list -> (listOf(summary) + list.filterNot { it.id == analysis.id }).sortedByDescending { it.createdAt } }
    }

    private companion object {
        const val HISTORY_PAGE_SIZE = 50
    }
}
