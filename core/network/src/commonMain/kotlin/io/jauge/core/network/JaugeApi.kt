package io.jauge.core.network

import io.jauge.core.network.payload.AccountPayload
import io.jauge.core.network.payload.AlertPayload
import io.jauge.core.network.payload.AlertListPayload
import io.jauge.core.network.payload.AnalysisPayload
import io.jauge.core.network.payload.AnalysisPagePayload
import io.jauge.core.network.payload.ApplicantFeedbackRequestPayload
import io.jauge.core.network.payload.AreaPayload
import io.jauge.core.network.payload.ConsentsPayload
import io.jauge.core.network.payload.CreateAlertRequestPayload
import io.jauge.core.network.payload.CreateAnalysisRequestPayload
import io.jauge.core.network.payload.CreateShareRequestPayload
import io.jauge.core.network.payload.MethodologyPayload
import io.jauge.core.network.payload.SalaryInsightPayload
import io.jauge.core.network.payload.SharePayload
import io.jauge.core.network.payload.ShareListPayload
import io.jauge.core.network.payload.SkillsInsightPayload
import io.jauge.core.network.payload.SubscriptionPayload
import io.jauge.core.network.payload.TrendInsightPayload
import io.jauge.core.network.payload.TwinOffersPayload

/** Name of the header that makes `POST /v1/analyses` idempotent. */
const val IDEMPOTENCY_KEY_HEADER = "Idempotency-Key"

/**
 * Client of the Jauge API (`https://api.jauge.io/`, contract in `docs/api.md` and `docs/openapi.yaml`).
 *
 * The same interface has three implementations: Retrofit and OkHttp on Android, Ktor on iOS, and the offline
 * implementation of `core:data` (mode sans API). Every function throws a [NetworkFailure] on failure, never another
 * exception type.
 */
interface JaugeApi {
    /**
     * `POST /v1/analyses` with JSON: analysis of a link, of a manual entry or of the text of a capture read on the
     * phone. Images are never sent.
     *
     * @param request link, manual entry or text.
     * @param idempotencyKey sent as the `Idempotency-Key` header: the same key gives back the analysis already
     *   created (kept 24 hours by the server), null to send none.
     */
    suspend fun createAnalysis(request: CreateAnalysisRequestPayload, idempotencyKey: String? = null): AnalysisPayload

    /**
     * `GET /v1/analyses?cursor=&limit=&query=`: analyses of the user, newest first.
     *
     * @param cursor cursor of the page, null for the first page.
     * @param limit page size.
     * @param query optional search on the job title, the company and the city (accents and case ignored), null or
     *   blank for every analysis.
     */
    suspend fun getAnalyses(cursor: String?, limit: Int, query: String? = null): AnalysisPagePayload

    /**
     * `GET /v1/analyses/{id}`: one analysis, polled while its status is `processing`.
     *
     * @param analysisId identifier.
     */
    suspend fun getAnalysis(analysisId: String): AnalysisPayload

    /**
     * `DELETE /v1/analyses/{id}`.
     *
     * @param analysisId identifier.
     */
    suspend fun deleteAnalysis(analysisId: String)

    /**
     * `PUT /v1/analyses/{id}/favorite`: marks the analysis as a favorite. Idempotent.
     *
     * @param analysisId identifier.
     */
    suspend fun addFavorite(analysisId: String)

    /**
     * `DELETE /v1/analyses/{id}/favorite`: removes the analysis from the favorites. Idempotent.
     *
     * @param analysisId identifier.
     */
    suspend fun removeFavorite(analysisId: String)

    /**
     * `GET /v1/analyses/{id}/salary?city=&department=`.
     *
     * @param analysisId identifier.
     * @param area search area of the user, null to let the server use the place of the offer.
     */
    suspend fun getSalary(analysisId: String, area: AreaPayload?): SalaryInsightPayload

    /**
     * `GET /v1/analyses/{id}/skills`.
     *
     * @param analysisId identifier.
     */
    suspend fun getSkills(analysisId: String): SkillsInsightPayload

    /**
     * `GET /v1/analyses/{id}/trend`.
     *
     * @param analysisId identifier.
     */
    suspend fun getTrend(analysisId: String): TrendInsightPayload

    /**
     * `GET /v1/analyses/{id}/twins?sort=&city=&department=&radius=&query=`.
     *
     * @param analysisId identifier.
     * @param sort `least_disputed`, `best_paid` or `most_recent`.
     * @param area search area of the user (twins within its radius), null to let the server use the place of the offer.
     * @param query optional search on the job title, the company and the place (accents and case ignored), null or
     *   blank for every twin.
     */
    suspend fun getTwins(analysisId: String, sort: String, area: AreaPayload?, query: String? = null): TwinOffersPayload

    /**
     * `POST /v1/analyses/{id}/feedback`.
     *
     * @param analysisId identifier.
     * @param request the real number of applicants.
     */
    suspend fun sendApplicantFeedback(analysisId: String, request: ApplicantFeedbackRequestPayload)

    /**
     * `GET /v1/methodology?analysisId=`.
     *
     * @param analysisId identifier of the analysis to explain.
     */
    suspend fun getMethodology(analysisId: String): MethodologyPayload

    /** `GET /v1/alerts`. */
    suspend fun getAlerts(): AlertListPayload

    /**
     * `POST /v1/alerts`.
     *
     * @param request watched analysis and threshold.
     */
    suspend fun createAlert(request: CreateAlertRequestPayload): AlertPayload

    /**
     * `DELETE /v1/alerts/{id}`.
     *
     * @param alertId identifier.
     */
    suspend fun deleteAlert(alertId: String)

    /** `GET /v1/shares`. */
    suspend fun getShares(): ShareListPayload

    /**
     * `POST /v1/shares`.
     *
     * @param request shared analysis and card options.
     */
    suspend fun createShare(request: CreateShareRequestPayload): SharePayload

    /**
     * `DELETE /v1/shares/{id}`.
     *
     * @param shareId identifier.
     */
    suspend fun deleteShare(shareId: String)

    /** `GET /v1/account`. */
    suspend fun getAccount(): AccountPayload

    /**
     * `PATCH /v1/account/consents`.
     *
     * @param consents new consents.
     */
    suspend fun updateConsents(consents: ConsentsPayload): AccountPayload

    /**
     * `DELETE /v1/account`.
     *
     * @param appleAuthorizationCode fresh Sign in with Apple code sent in the body, null to send no body.
     */
    suspend fun deleteAccount(appleAuthorizationCode: String? = null)

    /** `GET /v1/subscription`. */
    suspend fun getSubscription(): SubscriptionPayload
}
