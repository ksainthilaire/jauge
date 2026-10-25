package io.jauge.core.data.offline

import io.jauge.core.common.text.SearchText
import io.jauge.core.data.preferences.FavoriteStore
import io.jauge.core.network.payload.AreaPayload
import io.jauge.core.network.payload.TwinOfferPayload
import io.jauge.core.network.payload.TwinOffersPayload
import io.jauge.core.data.preferences.InMemoryKeyValueStore
import io.jauge.core.data.error.ApiErrorCodes
import io.jauge.core.network.JaugeApi
import io.jauge.core.network.NetworkFailure
import io.jauge.core.network.payload.AccountPayload
import io.jauge.core.network.payload.AlertPayload
import io.jauge.core.network.payload.AlertListPayload
import io.jauge.core.network.payload.AnalysisPayload
import io.jauge.core.network.payload.AnalysisFailurePayload
import io.jauge.core.network.payload.AnalysisPagePayload
import io.jauge.core.network.payload.ApplicantFeedbackRequestPayload
import io.jauge.core.network.payload.ConsentsPayload
import io.jauge.core.network.payload.CreateAlertRequestPayload
import io.jauge.core.network.payload.CreateAnalysisRequestPayload
import io.jauge.core.network.payload.CreateShareRequestPayload
import io.jauge.core.network.payload.MethodologyPayload
import io.jauge.core.network.payload.ReadingStepPayload
import io.jauge.core.network.payload.SalaryInsightPayload
import io.jauge.core.network.payload.SharePayload
import io.jauge.core.network.payload.ShareListPayload
import io.jauge.core.network.payload.SkillsInsightPayload
import io.jauge.core.network.payload.SubscriptionPayload
import io.jauge.core.network.payload.TrendInsightPayload
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.random.Random

/**
 * Offline implementation of [JaugeApi] ("mode sans API"): realistic data from the mockups, simulated latency, an
 * analysis that progresses step by step when polled, and failures chosen with [OfflineScenarioController].
 * It keeps its state in memory (new analyses, alerts, shares) until the app is closed, except the favorites, kept in
 * [favoriteStore] like a server would.
 *
 * @param dataset sample payloads.
 * @param scenarios simulated scenario and latency.
 * @param random source of randomness for the latency.
 * @param favoriteStore favorites known by the simulated server.
 */
class OfflineJaugeApi(
    private val dataset: OfflineDataset,
    private val scenarios: OfflineScenarioController,
    private val random: Random = Random.Default,
    private val favoriteStore: FavoriteStore = FavoriteStore(InMemoryKeyValueStore(), FavoriteStore.OFFLINE_KEY),
) : JaugeApi {

    private val mutex = Mutex()
    private val analyses = dataset.initialAnalyses().associateBy { it.id }.toMutableMap()
    private val order = analyses.keys.toMutableList()
    private val pending = mutableMapOf<String, PendingAnalysis>()
    private val alerts = mutableListOf<AlertPayload>()
    private val favorites = favoriteStore.read().toMutableSet()
    private val shares = dataset.initialShares().toMutableList()
    private var consents = ConsentsPayload(marketingEmails = false)
    private var counter = 0
    private var accountDeleted = false

    private class PendingAnalysis(val completed: AnalysisPayload, val unreadable: Boolean, var stage: Int = 0)

    override suspend fun createAnalysis(request: CreateAnalysisRequestPayload): AnalysisPayload = simulate(isAnalysisCreation = true) {
        val base = dataset.marketingAnalysis(id = nextId(), createdHoursAgo = 0)
        val completed = when (request.type) {
            CreateAnalysisRequestPayload.TYPE_MANUAL -> base.copy(
                offer = base.offer?.copy(
                    jobTitle = request.jobTitle.orEmpty(),
                    company = null,
                    city = request.city.orEmpty(),
                    skills = emptyList(),
                    sourceUrl = null,
                ),
            )
            else -> base.copy(offer = base.offer?.copy(sourceUrl = request.url))
        }
        startAnalysis(completed.withArea(request.area), unreadable = false)
    }

    override suspend fun createAnalysisFromImage(image: ByteArray, mimeType: String, origin: String, area: AreaPayload?): AnalysisPayload =
        simulate(isAnalysisCreation = true) {
            val completed = dataset.marketingAnalysis(id = nextId(), createdHoursAgo = 0).withArea(area)
            startAnalysis(completed, unreadable = scenarios.scenario.value == OfflineScenario.UNREADABLE_OFFER)
        }

    override suspend fun getAnalyses(cursor: String?, limit: Int, query: String?): AnalysisPagePayload = simulate {
        if (isEmptyScenario()) return@simulate AnalysisPagePayload(emptyList())
        val matching = order.filter { id ->
            val offer = analyses[id]?.offer
            query.isNullOrBlank() || (offer != null && SearchText.matches(query, offer.jobTitle, offer.company, offer.city))
        }
        val start = cursor?.toIntOrNull() ?: 0
        val ids = matching.drop(start).take(limit)
        val next = (start + ids.size).takeIf { it < matching.size }?.toString()
        AnalysisPagePayload(
            items = ids.mapNotNull { id -> analyses[id]?.let(dataset::summaryOf)?.copy(isFavorite = id in favorites) },
            nextCursor = next,
        )
    }

    override suspend fun getAnalysis(analysisId: String): AnalysisPayload = simulate {
        pending[analysisId]?.let { return@simulate advance(analysisId, it) }
        analyses[analysisId]?.copy(isFavorite = analysisId in favorites) ?: throw notFound()
    }

    override suspend fun deleteAnalysis(analysisId: String) = simulate {
        if (analyses.remove(analysisId) == null) throw notFound()
        order.remove(analysisId)
        if (favorites.remove(analysisId)) favoriteStore.write(favorites)
    }

    override suspend fun addFavorite(analysisId: String) = simulate {
        requireAnalysis(analysisId)
        favorites.add(analysisId)
        favoriteStore.write(favorites)
    }

    override suspend fun removeFavorite(analysisId: String) = simulate {
        requireAnalysis(analysisId)
        favorites.remove(analysisId)
        favoriteStore.write(favorites)
    }

    override suspend fun getSalary(analysisId: String, area: AreaPayload?): SalaryInsightPayload = simulate {
        requireAnalysis(analysisId)
        val salary = dataset.salary()
        if (area == null) salary else salary.copy(areaName = area.city)
    }

    override suspend fun getSkills(analysisId: String): SkillsInsightPayload = simulate { requireAnalysis(analysisId); dataset.skills() }

    override suspend fun getTrend(analysisId: String): TrendInsightPayload = simulate { requireAnalysis(analysisId); dataset.trend() }

    override suspend fun getTwins(analysisId: String, sort: String, area: AreaPayload?, query: String?): TwinOffersPayload = simulate {
        requireAnalysis(analysisId)
        val twins = dataset.twins().around(area)
        if (isEmptyScenario()) return@simulate twins.copy(items = emptyList())
        twins.copy(
            items = twins.items
                .filter { query.isNullOrBlank() || SearchText.matches(query, it.jobTitle, it.company, it.place) }
                .sortedWith(twinOrder(sort)),
        )
    }

    /** Order of the twins for a sort value of the API, like the server would apply it. */
    private fun twinOrder(sort: String): Comparator<TwinOfferPayload> = when (sort) {
        SORT_BEST_PAID -> compareByDescending<TwinOfferPayload> { it.salary?.maximum ?: 0 }.thenBy { it.score }
        SORT_MOST_RECENT -> compareByDescending<TwinOfferPayload> { it.publishedOn.orEmpty() }.thenBy { it.score }
        else -> compareBy { it.score }
    }

    /** Twins of the sample data moved around the area of the user: inside the radius, in its city outside Rhône. */
    private fun TwinOffersPayload.around(area: AreaPayload?): TwinOffersPayload {
        if (area == null) return this
        val isSampleArea = area.departmentCode == OfflineDataset.SAMPLE_DEPARTMENT_CODE
        return TwinOffersPayload(
            areaName = "${area.city} + ${area.radiusKilometers} km",
            radiusKilometers = area.radiusKilometers,
            items = items
                .filter { it.distanceKilometers <= area.radiusKilometers }
                .map { if (isSampleArea) it else it.copy(place = area.city) },
        )
    }

    /** Sample analysis with the radius of the search area of the user. */
    private fun AnalysisPayload.withArea(area: AreaPayload?): AnalysisPayload =
        if (area == null) this else copy(result = result?.copy(twinRadiusKilometers = area.radiusKilometers))

    override suspend fun sendApplicantFeedback(analysisId: String, request: ApplicantFeedbackRequestPayload) = simulate {
        requireAnalysis(analysisId)
        Unit
    }

    override suspend fun getMethodology(analysisId: String): MethodologyPayload = simulate { requireAnalysis(analysisId); dataset.methodology() }

    override suspend fun getAlerts(): AlertListPayload = simulate { AlertListPayload(if (isEmptyScenario()) emptyList() else alerts.toList()) }

    override suspend fun createAlert(request: CreateAlertRequestPayload): AlertPayload = simulate {
        requireAnalysis(request.analysisId)
        AlertPayload(id = "alert-${nextId()}", analysisId = request.analysisId, threshold = request.threshold, active = true)
            .also { alerts.add(0, it) }
    }

    override suspend fun deleteAlert(alertId: String) = simulate {
        if (!alerts.removeAll { it.id == alertId }) throw notFound()
        Unit
    }

    override suspend fun getShares(): ShareListPayload = simulate { ShareListPayload(if (isEmptyScenario()) emptyList() else shares.toList()) }

    override suspend fun createShare(request: CreateShareRequestPayload): SharePayload = simulate {
        requireAnalysis(request.analysisId)
        SharePayload(
            id = "share-${nextId()}",
            analysisId = request.analysisId,
            url = "https://jauge.io/a/${request.analysisId}",
            imageUrl = null,
            format = request.format,
            hideCompany = request.hideCompany,
            showCalmTwin = request.showCalmTwin,
            createdAt = dataset.createdAt(0),
        ).also { shares.add(0, it) }
    }

    override suspend fun deleteShare(shareId: String) = simulate {
        if (!shares.removeAll { it.id == shareId }) throw notFound()
        Unit
    }

    override suspend fun getAccount(): AccountPayload = simulate {
        if (accountDeleted) AccountPayload(id = "anonymous") else dataset.account(analyses.size + 9).copy(consents = consents)
    }

    override suspend fun updateConsents(consents: ConsentsPayload): AccountPayload = simulate {
        this.consents = consents
        dataset.account(analyses.size + 9).copy(consents = consents)
    }

    override suspend fun deleteAccount() = simulate {
        accountDeleted = true
        analyses.clear()
        order.clear()
        alerts.clear()
        favorites.clear()
        favoriteStore.write(favorites)
        shares.clear()
    }

    override suspend fun getSubscription(): SubscriptionPayload = simulate {
        val quotaUsed = scenarios.scenario.value == OfflineScenario.QUOTA_REACHED
        SubscriptionPayload(status = "free", freeAnalysesLeftThisWeek = if (quotaUsed) 0 else 1)
    }

    private fun startAnalysis(completed: AnalysisPayload, unreadable: Boolean): AnalysisPayload {
        val state = PendingAnalysis(completed, unreadable)
        pending[completed.id] = state
        return readingSnapshot(completed, stage = 0)
    }

    private fun advance(analysisId: String, state: PendingAnalysis): AnalysisPayload {
        state.stage += 1
        if (state.unreadable && state.stage >= 1) {
            pending.remove(analysisId)
            return state.completed.copy(
                status = AnalysisPayload.STATUS_FAILED,
                offer = null,
                result = null,
                failure = AnalysisFailurePayload(ApiErrorCodes.UNREADABLE_OFFER, OfflineDataset.UNREADABLE_COMPANY),
            )
        }
        if (state.stage >= COMPLETED_STAGE) {
            pending.remove(analysisId)
            analyses[analysisId] = state.completed
            order.add(0, analysisId)
            return state.completed
        }
        return readingSnapshot(state.completed, state.stage)
    }

    private fun readingSnapshot(completed: AnalysisPayload, stage: Int): AnalysisPayload {
        val offer = completed.offer
        fun status(step: Int) = when {
            stage > step -> "done"
            stage == step -> "running"
            else -> "pending"
        }
        val placeDetail = offer?.let { listOfNotNull(it.jobTitle, it.city, "CDI".takeIf { _ -> it.contractType == "permanent" }).joinToString(" · ") }
        return completed.copy(
            status = AnalysisPayload.STATUS_PROCESSING,
            progressPercent = PROGRESS_BY_STAGE[stage.coerceIn(0, PROGRESS_BY_STAGE.lastIndex)],
            steps = listOf(
                ReadingStepPayload("position_and_place", status(0), placeDetail.takeIf { stage > 0 }),
                ReadingStepPayload("skills", status(1), offer?.skills?.joinToString(", ")?.takeIf { stage > 1 && it.isNotEmpty() }),
                ReadingStepPayload("comparison", status(2), null),
                ReadingStepPayload("applicants", status(3), null),
            ),
            publishedDaysAgo = if (stage >= 2) 2 else null,
            hasDisplayedSalary = if (stage >= 2) offer?.displayedSalary != null else null,
            offer = null,
            result = null,
        )
    }

    private fun requireAnalysis(analysisId: String) {
        if (analysisId !in analyses) throw notFound()
    }

    private fun isEmptyScenario() = scenarios.scenario.value == OfflineScenario.EMPTY

    private fun notFound() = NetworkFailure.Http(404, "not_found")

    private fun nextId(): String {
        counter += 1
        return "offline-$counter"
    }

    private suspend fun <T> simulate(isAnalysisCreation: Boolean = false, block: suspend () -> T): T {
        val latency = scenarios.latencyMilliseconds
        if (latency.last > 0) delay(random.nextLong(latency.first, latency.last + 1))
        when (scenarios.scenario.value) {
            OfflineScenario.NO_CONNECTION -> throw NetworkFailure.NoConnection()
            OfflineScenario.TIMEOUT -> throw NetworkFailure.Timeout()
            OfflineScenario.SERVER_UNAVAILABLE -> throw NetworkFailure.Http(503, null)
            OfflineScenario.SESSION_EXPIRED -> throw NetworkFailure.Http(401, ApiErrorCodes.UNAUTHORIZED)
            OfflineScenario.INVALID_RESPONSE -> throw NetworkFailure.InvalidResponse()
            OfflineScenario.QUOTA_REACHED -> if (isAnalysisCreation) throw NetworkFailure.Http(402, ApiErrorCodes.QUOTA_REACHED)
            OfflineScenario.NORMAL, OfflineScenario.UNREADABLE_OFFER, OfflineScenario.EMPTY -> Unit
        }
        return mutex.withLock { block() }
    }

    private companion object {
        /** Progress shown at each poll (mockup 03 shows 62 %). */
        val PROGRESS_BY_STAGE = listOf(20, 40, 62, 85)

        /** Poll after which the analysis is complete. */
        const val COMPLETED_STAGE = 4

        /** API value of the "Mieux payées" order. */
        const val SORT_BEST_PAID = "best_paid"

        /** API value of the "Récentes" order. */
        const val SORT_MOST_RECENT = "most_recent"
    }
}
