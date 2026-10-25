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
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.network.sockets.ConnectTimeoutException
import io.ktor.client.network.sockets.SocketTimeoutException
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.delete
import io.ktor.client.request.forms.formData
import io.ktor.client.request.forms.submitFormWithBinaryData
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.serialization.ContentConvertException
import kotlinx.coroutines.CancellationException
import kotlinx.io.IOException
import kotlinx.serialization.SerializationException

/**
 * iOS implementation of [JaugeApi] with Ktor (Darwin engine). Every exception is translated into a [NetworkFailure].
 *
 * @param client Ktor client configured with the base URL, JSON and timeouts (see `NetworkModule.ios.kt`).
 */
internal class KtorJaugeApi(private val client: HttpClient) : JaugeApi {

    override suspend fun createAnalysis(request: CreateAnalysisRequestPayload): AnalysisPayload =
        call { client.post("v1/analyses") { json(request) } }.decode()

    override suspend fun createAnalysisFromImage(image: ByteArray, mimeType: String, origin: String, area: AreaPayload?): AnalysisPayload = call {
        val extension = mimeType.substringAfter('/', "jpg")
        client.submitFormWithBinaryData(
            url = "v1/analyses",
            formData = formData {
                append("origin", origin)
                area?.let {
                    append("areaCity", it.city)
                    it.departmentCode?.let { department -> append("areaDepartment", department) }
                    append("areaRadius", it.radiusKilometers.toString())
                }
                append(
                    key = "image",
                    value = image,
                    headers = Headers.build {
                        append(HttpHeaders.ContentType, mimeType)
                        append(HttpHeaders.ContentDisposition, "filename=\"offer.$extension\"")
                    },
                )
            },
        )
    }.decode()

    override suspend fun getAnalyses(cursor: String?, limit: Int): AnalysisPagePayload = call {
        client.get("v1/analyses") {
            if (cursor != null) parameter("cursor", cursor)
            parameter("limit", limit)
        }
    }.decode()

    override suspend fun getAnalysis(analysisId: String): AnalysisPayload = call { client.get("v1/analyses/${pathSegment(analysisId)}") }.decode()

    override suspend fun deleteAnalysis(analysisId: String) {
        call { client.delete("v1/analyses/${pathSegment(analysisId)}") }
    }

    override suspend fun addFavorite(analysisId: String) {
        call { client.put("v1/analyses/${pathSegment(analysisId)}/favorite") }
    }

    override suspend fun removeFavorite(analysisId: String) {
        call { client.delete("v1/analyses/${pathSegment(analysisId)}/favorite") }
    }

    override suspend fun getSalary(analysisId: String, area: AreaPayload?): SalaryInsightPayload = call {
        client.get("v1/analyses/${pathSegment(analysisId)}/salary") {
            area?.let {
                parameter("city", it.city)
                it.departmentCode?.let { department -> parameter("department", department) }
            }
        }
    }.decode()

    override suspend fun getSkills(analysisId: String): SkillsInsightPayload = call { client.get("v1/analyses/${pathSegment(analysisId)}/skills") }.decode()

    override suspend fun getTrend(analysisId: String): TrendInsightPayload = call { client.get("v1/analyses/${pathSegment(analysisId)}/trend") }.decode()

    override suspend fun getTwins(analysisId: String, sort: String, area: AreaPayload?): TwinOffersPayload = call {
        client.get("v1/analyses/${pathSegment(analysisId)}/twins") {
            parameter("sort", sort)
            area?.let {
                parameter("city", it.city)
                it.departmentCode?.let { department -> parameter("department", department) }
                parameter("radius", it.radiusKilometers)
            }
        }
    }.decode()

    override suspend fun sendApplicantFeedback(analysisId: String, request: ApplicantFeedbackRequestPayload) {
        call { client.post("v1/analyses/${pathSegment(analysisId)}/feedback") { json(request) } }
    }

    override suspend fun getMethodology(analysisId: String): MethodologyPayload =
        call { client.get("v1/methodology") { parameter("analysisId", analysisId) } }.decode()

    override suspend fun getAlerts(): AlertListPayload = call { client.get("v1/alerts") }.decode()

    override suspend fun createAlert(request: CreateAlertRequestPayload): AlertPayload = call { client.post("v1/alerts") { json(request) } }.decode()

    override suspend fun deleteAlert(alertId: String) {
        call { client.delete("v1/alerts/${pathSegment(alertId)}") }
    }

    override suspend fun getShares(): ShareListPayload = call { client.get("v1/shares") }.decode()

    override suspend fun createShare(request: CreateShareRequestPayload): SharePayload = call { client.post("v1/shares") { json(request) } }.decode()

    override suspend fun deleteShare(shareId: String) {
        call { client.delete("v1/shares/${pathSegment(shareId)}") }
    }

    override suspend fun getAccount(): AccountPayload = call { client.get("v1/account") }.decode()

    override suspend fun updateConsents(consents: ConsentsPayload): AccountPayload =
        call { client.patch("v1/account/consents") { json(consents) } }.decode()

    override suspend fun deleteAccount() {
        call { client.delete("v1/account") }
    }

    override suspend fun getSubscription(): SubscriptionPayload = call { client.get("v1/subscription") }.decode()

    private inline fun <reified T> HttpRequestBuilder.json(body: T) {
        contentType(ContentType.Application.Json)
        setBody(body)
    }

    private suspend inline fun <reified T> HttpResponse.decode(): T = try {
        body<T>()
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (other: Exception) {
        throw NetworkFailure.InvalidResponse(other)
    }

    private suspend fun call(block: suspend () -> HttpResponse): HttpResponse {
        val response = try {
            block()
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (failure: NetworkFailure) {
            throw failure
        } catch (timeout: HttpRequestTimeoutException) {
            throw NetworkFailure.Timeout(timeout)
        } catch (timeout: ConnectTimeoutException) {
            throw NetworkFailure.Timeout(timeout)
        } catch (timeout: SocketTimeoutException) {
            throw NetworkFailure.Timeout(timeout)
        } catch (serialization: SerializationException) {
            throw NetworkFailure.InvalidResponse(serialization)
        } catch (conversion: ContentConvertException) {
            throw NetworkFailure.InvalidResponse(conversion)
        } catch (input: IOException) {
            throw NetworkFailure.NoConnection(input)
        } catch (other: Exception) {
            throw darwinFailure(other)
        }
        if (!response.status.isSuccess()) {
            val body = runCatching { response.bodyAsText() }.getOrNull()
            throw NetworkFailure.Http(response.status.value, parseApiErrorCode(body))
        }
        return response
    }

    /**
     * The Darwin engine reports offline and timeout errors as `DarwinHttpRequestException` wrapping an `NSError`.
     * Its message contains the `NSURLErrorDomain` code: -1001 is a timeout, the other network codes mean no
     * connection.
     */
    private fun darwinFailure(other: Exception): NetworkFailure {
        val description = other.message.orEmpty() + " " + other.cause?.message.orEmpty()
        return when {
            "-1001" in description -> NetworkFailure.Timeout(other)
            NETWORK_ERROR_CODES.any { it in description } -> NetworkFailure.NoConnection(other)
            else -> NetworkFailure.Unexpected(other)
        }
    }

    private companion object {
        /** NSURLError codes meaning that the network is not reachable. */
        val NETWORK_ERROR_CODES = listOf("-1009", "-1004", "-1003", "-1005", "-1018", "-1020")
    }
}
