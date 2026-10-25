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
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.SerializationException
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import retrofit2.HttpException
import retrofit2.Response
import java.io.IOException
import java.io.InterruptedIOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

/**
 * Android implementation of [JaugeApi] with Retrofit and OkHttp. Every exception is translated into a
 * [NetworkFailure].
 *
 * @param service the Retrofit service.
 */
internal class RetrofitJaugeApi(private val service: RetrofitJaugeService) : JaugeApi {

    override suspend fun createAnalysis(request: CreateAnalysisRequestPayload): AnalysisPayload = call { service.createAnalysis(request) }

    override suspend fun createAnalysisFromImage(image: ByteArray, mimeType: String, origin: String, area: AreaPayload?): AnalysisPayload = call {
        val extension = mimeType.substringAfter('/', "jpg")
        val imagePart = MultipartBody.Part.createFormData(
            name = "image",
            filename = "offer.$extension",
            body = image.toRequestBody(mimeType.toMediaType()),
        )
        val text = "text/plain".toMediaType()
        service.createAnalysisFromImage(
            image = imagePart,
            origin = origin.toRequestBody(text),
            areaCity = area?.city?.toRequestBody(text),
            areaDepartment = area?.departmentCode?.toRequestBody(text),
            areaRadius = area?.radiusKilometers?.toString()?.toRequestBody(text),
        )
    }

    override suspend fun getAnalyses(cursor: String?, limit: Int): AnalysisPagePayload = call { service.getAnalyses(cursor, limit) }

    override suspend fun getAnalysis(analysisId: String): AnalysisPayload = call { service.getAnalysis(analysisId) }

    override suspend fun deleteAnalysis(analysisId: String) = callWithoutBody { service.deleteAnalysis(analysisId) }

    override suspend fun addFavorite(analysisId: String) = callWithoutBody { service.addFavorite(analysisId) }

    override suspend fun removeFavorite(analysisId: String) = callWithoutBody { service.removeFavorite(analysisId) }

    override suspend fun getSalary(analysisId: String, area: AreaPayload?): SalaryInsightPayload =
        call { service.getSalary(analysisId, area?.city, area?.departmentCode) }

    override suspend fun getSkills(analysisId: String): SkillsInsightPayload = call { service.getSkills(analysisId) }

    override suspend fun getTrend(analysisId: String): TrendInsightPayload = call { service.getTrend(analysisId) }

    override suspend fun getTwins(analysisId: String, sort: String, area: AreaPayload?): TwinOffersPayload =
        call { service.getTwins(analysisId, sort, area?.city, area?.departmentCode, area?.radiusKilometers) }

    override suspend fun sendApplicantFeedback(analysisId: String, request: ApplicantFeedbackRequestPayload) =
        callWithoutBody { service.sendApplicantFeedback(analysisId, request) }

    override suspend fun getMethodology(analysisId: String): MethodologyPayload = call { service.getMethodology(analysisId) }

    override suspend fun getAlerts(): AlertListPayload = call { service.getAlerts() }

    override suspend fun createAlert(request: CreateAlertRequestPayload): AlertPayload = call { service.createAlert(request) }

    override suspend fun deleteAlert(alertId: String) = callWithoutBody { service.deleteAlert(alertId) }

    override suspend fun getShares(): ShareListPayload = call { service.getShares() }

    override suspend fun createShare(request: CreateShareRequestPayload): SharePayload = call { service.createShare(request) }

    override suspend fun deleteShare(shareId: String) = callWithoutBody { service.deleteShare(shareId) }

    override suspend fun getAccount(): AccountPayload = call { service.getAccount() }

    override suspend fun updateConsents(consents: ConsentsPayload): AccountPayload = call { service.updateConsents(consents) }

    override suspend fun deleteAccount() = callWithoutBody { service.deleteAccount() }

    override suspend fun getSubscription(): SubscriptionPayload = call { service.getSubscription() }

    private suspend fun callWithoutBody(block: suspend () -> Response<Unit>) {
        val response = call(block)
        if (!response.isSuccessful) {
            throw NetworkFailure.Http(response.code(), parseApiErrorCode(response.errorBody()?.string()))
        }
    }

    private suspend fun <T> call(block: suspend () -> T): T = try {
        block()
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (failure: NetworkFailure) {
        throw failure
    } catch (httpException: HttpException) {
        throw NetworkFailure.Http(httpException.code(), parseApiErrorCode(httpException.response()?.errorBody()?.string()))
    } catch (timeout: SocketTimeoutException) {
        throw NetworkFailure.Timeout(timeout)
    } catch (interrupted: InterruptedIOException) {
        throw NetworkFailure.Timeout(interrupted)
    } catch (unknownHost: UnknownHostException) {
        throw NetworkFailure.NoConnection(unknownHost)
    } catch (serialization: SerializationException) {
        throw NetworkFailure.InvalidResponse(serialization)
    } catch (illegalArgument: IllegalArgumentException) {
        throw NetworkFailure.InvalidResponse(illegalArgument)
    } catch (input: IOException) {
        throw NetworkFailure.NoConnection(input)
    } catch (other: Exception) {
        throw NetworkFailure.Unexpected(other)
    }
}
