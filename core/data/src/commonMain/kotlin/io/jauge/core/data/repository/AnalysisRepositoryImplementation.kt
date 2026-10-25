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
import io.jauge.core.data.preferences.FavoriteStore
import io.jauge.core.data.preferences.InMemoryKeyValueStore
import io.jauge.core.data.preferences.SearchAreaSource
import io.jauge.core.data.preferences.toPayload
import io.jauge.core.network.payload.AnalysisPayload
import io.jauge.core.network.payload.CreateAnalysisRequestPayload
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * [AnalysisRepository] backed by the Jauge API (real or offline). An analysis is created, then polled every
 * [pollIntervalMilliseconds] until it completes, fails or [maximumPolls] is reached (then [JaugeError.Timeout]).
 * The history is cached in memory and updated after every change.
 *
 * Favorites are kept on the device ([favoriteStore]) so the stars survive a restart and show at once. A star change
 * is optimistic: the local set changes first, then the API is called, and the change is undone if the call fails.
 * The server is the reference for the analyses it lists: a refresh copies their `isFavorite` into the local set.
 *
 * @param api the API client.
 * @param favoriteStore favorites kept on the device.
 * @param pollIntervalMilliseconds delay between two polls.
 * @param maximumPolls number of polls before giving up.
 * @param searchArea search area of the user, sent with every new analysis.
 */
class AnalysisRepositoryImplementation(
    private val api: JaugeApi,
    private val favoriteStore: FavoriteStore = FavoriteStore(InMemoryKeyValueStore()),
    private val pollIntervalMilliseconds: Long = 1_000,
    private val maximumPolls: Int = 90,
    private val searchArea: SearchAreaSource = SearchAreaSource.NONE,
) : AnalysisRepository {

    private val history = MutableStateFlow<List<AnalysisSummary>>(emptyList())
    private val analysesById = mutableMapOf<String, Analysis>()
    private val favoriteIds = MutableStateFlow(favoriteStore.read())
    private val favoriteMutex = Mutex()

    override fun analyze(source: OfferSource): Flow<AnalysisProgress> = flow {
        if (source is OfferSource.Image) {
            // Images are read on the phone by AnalyzeOfferUseCase and only their text is sent: an image is never
            // uploaded, even if one reaches this point.
            emit(AnalysisProgress.Failed(JaugeError.UnreadableOffer()))
            return@flow
        }
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

    override fun observeHistory(): Flow<List<AnalysisSummary>> =
        combine(history, favoriteIds) { list, favorites -> list.map { it.copy(isFavorite = it.id in favorites) } }

    override suspend fun refreshHistory(): Outcome<Unit> = outcomeOf {
        val page = api.getAnalyses(cursor = null, limit = HISTORY_PAGE_SIZE)
        val summaries = page.items.mapNotNull { it.toDomainOrNull() }.sortedByDescending { it.createdAt }
        applyServerFavorites(summaries.associate { it.id to it.isFavorite })
        history.value = summaries
    }

    override suspend fun getAnalysis(analysisId: String): Outcome<Analysis> {
        analysesById[analysisId]?.let { return Outcome.Success(it.copy(isFavorite = analysisId in favoriteIds.value)) }
        return outcomeOf { api.getAnalysis(analysisId) }.let { outcome ->
            when (outcome) {
                is Outcome.Failure -> outcome
                is Outcome.Success -> when (outcome.value.status) {
                    AnalysisPayload.STATUS_COMPLETED -> outcomeOf {
                        val analysis = outcome.value.toDomainAnalysis()
                        applyServerFavorites(mapOf(analysis.id to analysis.isFavorite))
                        remember(analysis)
                        analysis
                    }
                    else -> Outcome.Failure(JaugeError.NotFound)
                }
            }
        }
    }

    override suspend fun deleteAnalysis(analysisId: String): Outcome<Unit> =
        outcomeOf { api.deleteAnalysis(analysisId) }.map {
            analysesById.remove(analysisId)
            history.update { list -> list.filterNot { it.id == analysisId } }
            changeFavorites { it - analysisId }
        }

    override fun observeFavoriteIds(): Flow<Set<String>> = favoriteIds.asStateFlow()

    override suspend fun setFavorite(analysisId: String, isFavorite: Boolean): Outcome<Unit> {
        val wasFavorite = analysisId in favoriteIds.value
        if (wasFavorite == isFavorite) return Outcome.Success(Unit)
        changeFavorites { if (isFavorite) it + analysisId else it - analysisId }
        val outcome = outcomeOf { if (isFavorite) api.addFavorite(analysisId) else api.removeFavorite(analysisId) }
        if (outcome is Outcome.Failure) {
            changeFavorites { if (wasFavorite) it + analysisId else it - analysisId }
        }
        return outcome
    }

    /** Forgets every favorite kept on the device (account deleted). */
    suspend fun clearLocalFavorites() = changeFavorites { emptySet() }

    private suspend fun applyServerFavorites(serverValues: Map<String, Boolean>) = changeFavorites { current ->
        current - serverValues.filterValues { !it }.keys + serverValues.filterValues { it }.keys
    }

    private suspend fun changeFavorites(transform: (Set<String>) -> Set<String>) = favoriteMutex.withLock {
        val updated = transform(favoriteIds.value)
        if (updated != favoriteIds.value) {
            favoriteIds.value = updated
            favoriteStore.write(updated)
        }
    }

    private suspend fun create(source: OfferSource): AnalysisPayload {
        val area = searchArea.current()?.toPayload()
        return when (source) {
            is OfferSource.Link -> api.createAnalysis(
                CreateAnalysisRequestPayload(type = CreateAnalysisRequestPayload.TYPE_LINK, url = source.url, area = area),
            )
            is OfferSource.Manual -> api.createAnalysis(
                CreateAnalysisRequestPayload(type = CreateAnalysisRequestPayload.TYPE_MANUAL, jobTitle = source.jobTitle, city = source.city, area = area),
            )
            is OfferSource.Text -> api.createAnalysis(
                CreateAnalysisRequestPayload(
                    type = CreateAnalysisRequestPayload.TYPE_TEXT,
                    text = source.text,
                    origin = when (source.origin) {
                        ImageOrigin.SCREENSHOT -> ImageOrigins.SCREENSHOT
                        ImageOrigin.PHOTO -> ImageOrigins.PHOTO
                    },
                    area = area,
                ),
            )
            is OfferSource.Image -> error("Images are read on the phone by AnalyzeOfferUseCase, never uploaded")
        }
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

