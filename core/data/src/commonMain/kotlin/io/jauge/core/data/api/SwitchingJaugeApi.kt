package io.jauge.core.data.api

import io.jauge.core.common.configuration.ApiModeController
import io.jauge.core.network.JaugeApi
import io.jauge.core.network.payload.ApplicantFeedbackRequestPayload
import io.jauge.core.network.payload.ConsentsPayload
import io.jauge.core.network.payload.CreateAlertRequestPayload
import io.jauge.core.network.payload.CreateAnalysisRequestPayload
import io.jauge.core.network.payload.CreateShareRequestPayload

/**
 * [JaugeApi] that sends every call either to the real API or to the offline implementation, following
 * [ApiModeController] (build setting, and the hidden switch in debug builds).
 *
 * @param remote Retrofit (Android) or Ktor (iOS) client.
 * @param offline offline client.
 * @param apiModeController current mode.
 */
class SwitchingJaugeApi(
    private val remote: JaugeApi,
    private val offline: JaugeApi,
    private val apiModeController: ApiModeController,
) : JaugeApi {

    private val current: JaugeApi
        get() = if (apiModeController.isOfflineMode.value) offline else remote

    override suspend fun createAnalysis(request: CreateAnalysisRequestPayload) = current.createAnalysis(request)

    override suspend fun createAnalysisFromImage(image: ByteArray, mimeType: String, origin: String) =
        current.createAnalysisFromImage(image, mimeType, origin)

    override suspend fun getAnalyses(cursor: String?, limit: Int) = current.getAnalyses(cursor, limit)

    override suspend fun getAnalysis(analysisId: String) = current.getAnalysis(analysisId)

    override suspend fun deleteAnalysis(analysisId: String) = current.deleteAnalysis(analysisId)

    override suspend fun getSalary(analysisId: String) = current.getSalary(analysisId)

    override suspend fun getSkills(analysisId: String) = current.getSkills(analysisId)

    override suspend fun getTrend(analysisId: String) = current.getTrend(analysisId)

    override suspend fun getTwins(analysisId: String, sort: String) = current.getTwins(analysisId, sort)

    override suspend fun sendApplicantFeedback(analysisId: String, request: ApplicantFeedbackRequestPayload) =
        current.sendApplicantFeedback(analysisId, request)

    override suspend fun getMethodology(analysisId: String) = current.getMethodology(analysisId)

    override suspend fun getAlerts() = current.getAlerts()

    override suspend fun createAlert(request: CreateAlertRequestPayload) = current.createAlert(request)

    override suspend fun deleteAlert(alertId: String) = current.deleteAlert(alertId)

    override suspend fun getShares() = current.getShares()

    override suspend fun createShare(request: CreateShareRequestPayload) = current.createShare(request)

    override suspend fun deleteShare(shareId: String) = current.deleteShare(shareId)

    override suspend fun getAccount() = current.getAccount()

    override suspend fun updateConsents(consents: ConsentsPayload) = current.updateConsents(consents)

    override suspend fun deleteAccount() = current.deleteAccount()

    override suspend fun getSubscription() = current.getSubscription()
}
