package io.jauge.core.network

import io.jauge.core.network.payload.AccountPayload
import io.jauge.core.network.payload.AlertPayload
import io.jauge.core.network.payload.AlertListPayload
import io.jauge.core.network.payload.AnalysisPayload
import io.jauge.core.network.payload.AnalysisPagePayload
import io.jauge.core.network.payload.ApplicantFeedbackRequestPayload
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

/**
 * Client of the Jauge API (`https://api.jauge.io/`, contract in `docs/api.md` and `docs/openapi.yaml`).
 *
 * The same interface has three implementations: Retrofit and OkHttp on Android, Ktor on iOS, and the offline
 * implementation of `core:data` (mode sans API). Every function throws a [NetworkFailure] on failure, never another
 * exception type.
 */
interface JaugeApi {
    /**
     * `POST /v1/analyses` with JSON: analysis of a link or of a manual entry.
     *
     * @param request link or manual entry.
     */
    suspend fun createAnalysis(request: CreateAnalysisRequestPayload): AnalysisPayload

    /**
     * `POST /v1/analyses` with multipart: analysis of a screenshot or a photo.
     *
     * @param image encoded image.
     * @param mimeType type of the image.
     * @param origin `screenshot` or `photo`.
     */
    suspend fun createAnalysisFromImage(image: ByteArray, mimeType: String, origin: String): AnalysisPayload

    /**
     * `GET /v1/analyses`: analyses of the user, newest first.
     *
     * @param cursor cursor of the page, null for the first page.
     * @param limit page size.
     */
    suspend fun getAnalyses(cursor: String?, limit: Int): AnalysisPagePayload

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
     * `GET /v1/analyses/{id}/salary`.
     *
     * @param analysisId identifier.
     */
    suspend fun getSalary(analysisId: String): SalaryInsightPayload

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
     * `GET /v1/analyses/{id}/twins?sort=`.
     *
     * @param analysisId identifier.
     * @param sort `least_disputed`, `best_paid` or `most_recent`.
     */
    suspend fun getTwins(analysisId: String, sort: String): TwinOffersPayload

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

    /** `DELETE /v1/account`. */
    suspend fun deleteAccount()

    /** `GET /v1/subscription`. */
    suspend fun getSubscription(): SubscriptionPayload
}
