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
import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Part
import retrofit2.http.Path
import retrofit2.http.Query

/** Retrofit description of the Jauge API. Used only through [RetrofitJaugeApi]. */
internal interface RetrofitJaugeService {
    @POST("v1/analyses")
    suspend fun createAnalysis(@Body request: CreateAnalysisRequestPayload): AnalysisPayload

    @Multipart
    @POST("v1/analyses")
    suspend fun createAnalysisFromImage(
        @Part image: MultipartBody.Part,
        @Part("origin") origin: RequestBody,
        @Part("areaCity") areaCity: RequestBody?,
        @Part("areaDepartment") areaDepartment: RequestBody?,
        @Part("areaRadius") areaRadius: RequestBody?,
    ): AnalysisPayload

    @GET("v1/analyses")
    suspend fun getAnalyses(@Query("cursor") cursor: String?, @Query("limit") limit: Int): AnalysisPagePayload

    @GET("v1/analyses/{id}")
    suspend fun getAnalysis(@Path("id") analysisId: String): AnalysisPayload

    @DELETE("v1/analyses/{id}")
    suspend fun deleteAnalysis(@Path("id") analysisId: String): Response<Unit>

    @PUT("v1/analyses/{id}/favorite")
    suspend fun addFavorite(@Path("id") analysisId: String): Response<Unit>

    @DELETE("v1/analyses/{id}/favorite")
    suspend fun removeFavorite(@Path("id") analysisId: String): Response<Unit>

    @GET("v1/analyses/{id}/salary")
    suspend fun getSalary(
        @Path("id") analysisId: String,
        @Query("city") city: String?,
        @Query("department") department: String?,
    ): SalaryInsightPayload

    @GET("v1/analyses/{id}/skills")
    suspend fun getSkills(@Path("id") analysisId: String): SkillsInsightPayload

    @GET("v1/analyses/{id}/trend")
    suspend fun getTrend(@Path("id") analysisId: String): TrendInsightPayload

    @GET("v1/analyses/{id}/twins")
    suspend fun getTwins(
        @Path("id") analysisId: String,
        @Query("sort") sort: String,
        @Query("city") city: String?,
        @Query("department") department: String?,
        @Query("radius") radiusKilometers: Int?,
    ): TwinOffersPayload

    @POST("v1/analyses/{id}/feedback")
    suspend fun sendApplicantFeedback(@Path("id") analysisId: String, @Body request: ApplicantFeedbackRequestPayload): Response<Unit>

    @GET("v1/methodology")
    suspend fun getMethodology(@Query("analysisId") analysisId: String): MethodologyPayload

    @GET("v1/alerts")
    suspend fun getAlerts(): AlertListPayload

    @POST("v1/alerts")
    suspend fun createAlert(@Body request: CreateAlertRequestPayload): AlertPayload

    @DELETE("v1/alerts/{id}")
    suspend fun deleteAlert(@Path("id") alertId: String): Response<Unit>

    @GET("v1/shares")
    suspend fun getShares(): ShareListPayload

    @POST("v1/shares")
    suspend fun createShare(@Body request: CreateShareRequestPayload): SharePayload

    @DELETE("v1/shares/{id}")
    suspend fun deleteShare(@Path("id") shareId: String): Response<Unit>

    @GET("v1/account")
    suspend fun getAccount(): AccountPayload

    @PATCH("v1/account/consents")
    suspend fun updateConsents(@Body consents: ConsentsPayload): AccountPayload

    @DELETE("v1/account")
    suspend fun deleteAccount(): Response<Unit>

    @GET("v1/subscription")
    suspend fun getSubscription(): SubscriptionPayload
}
