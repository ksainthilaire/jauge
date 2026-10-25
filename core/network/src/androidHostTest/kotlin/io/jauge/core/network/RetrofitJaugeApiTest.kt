package io.jauge.core.network

import io.jauge.core.network.payload.CreateAnalysisRequestPayload
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.concurrent.TimeUnit
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class RetrofitJaugeApiTest {

    private lateinit var server: MockWebServer
    private lateinit var api: JaugeApi

    @BeforeTest
    fun setUp() {
        server = MockWebServer()
        server.start()
        val client = OkHttpClient.Builder().readTimeout(1, TimeUnit.SECONDS).build()
        val service = Retrofit.Builder()
            .baseUrl(server.url("/"))
            .client(client)
            .addConverterFactory(JaugeJson.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(RetrofitJaugeService::class.java)
        api = RetrofitJaugeApi(service)
    }

    @AfterTest
    fun tearDown() {
        server.close()
    }

    @Test
    fun createsAnAnalysisFromALink() = runTest {
        server.enqueue(
            MockResponse.Builder()
                .code(202)
                .body("""{"id":"a1","status":"processing","createdAt":"2026-10-23T08:00:00Z","progressPercent":20}""")
                .build(),
        )
        val analysis = api.createAnalysis(CreateAnalysisRequestPayload(type = "link", url = "https://example.com/job"))
        assertEquals("a1", analysis.id)
        val request = server.takeRequest()
        assertEquals("/v1/analyses", request.url.encodedPath)
        assertTrue(request.body?.utf8().orEmpty().contains("\"url\":\"https://example.com/job\""))
    }

    @Test
    fun createsAnAnalysisFromTheTextOfACaptureAsJson() = runTest {
        server.enqueue(
            MockResponse.Builder()
                .code(202)
                .body("""{"id":"a2","status":"processing","createdAt":"2026-10-23T08:00:00Z"}""")
                .build(),
        )
        api.createAnalysis(
            CreateAnalysisRequestPayload(
                type = CreateAnalysisRequestPayload.TYPE_TEXT,
                text = "Chargé(e) de marketing digital, Maison Odier, Lyon 3e",
                origin = ImageOrigins.SCREENSHOT,
            ),
        )
        val request = server.takeRequest()
        val body = request.body?.utf8().orEmpty()
        assertTrue(request.headers["Content-Type"].orEmpty().startsWith("application/json"))
        assertTrue(body.contains("\"type\":\"text\""))
        assertTrue(body.contains("\"origin\":\"screenshot\""))
        assertTrue(body.contains("\"text\":\"Chargé(e) de marketing digital, Maison Odier, Lyon 3e\""))
    }

    @Test
    fun theIdempotencyKeyIsSentAsAHeader() = runTest {
        repeat(2) {
            server.enqueue(MockResponse.Builder().code(202).body("""{"id":"a3","status":"processing","createdAt":"2026-10-23T08:00:00Z"}""").build())
        }
        api.createAnalysis(CreateAnalysisRequestPayload(type = "link", url = "https://example.com/job"), idempotencyKey = "key-0123456789")
        assertEquals("key-0123456789", server.takeRequest().headers[IDEMPOTENCY_KEY_HEADER])
        api.createAnalysis(CreateAnalysisRequestPayload(type = "link", url = "https://example.com/job"))
        assertEquals(null, server.takeRequest().headers[IDEMPOTENCY_KEY_HEADER])
    }

    @Test
    fun accountDeletionSendsTheAppleCodeOnlyWhenThereIsOne() = runTest {
        repeat(2) { server.enqueue(MockResponse.Builder().code(202).build()) }
        api.deleteAccount(appleAuthorizationCode = "c0de.apple-123")
        val withCode = server.takeRequest()
        assertEquals("DELETE", withCode.method)
        assertEquals("/v1/account", withCode.url.encodedPath)
        assertEquals("""{"appleAuthorizationCode":"c0de.apple-123"}""", withCode.body?.utf8())
        api.deleteAccount()
        val withoutCode = server.takeRequest()
        assertEquals("DELETE", withoutCode.method)
        assertTrue(withoutCode.body?.utf8().isNullOrEmpty())
    }

    @Test
    fun adviceIsReadAndOptional() = runTest {
        val result = """{"score":78,"percentileInRegion":84,"regionName":"Rhône","applicants":{"estimate":210,"low":160,"high":260},"reliability":"medium""""
        server.enqueue(
            MockResponse.Builder().body(
                """{"id":"a4","status":"completed","createdAt":"2026-10-23T08:00:00Z","result":$result,""" +
                    """"advice":{"text":"Postule avant jeudi.","negotiationArgument":null,"missingSkills":["SQL"]}}}""",
            ).build(),
        )
        server.enqueue(MockResponse.Builder().body("""{"id":"a5","status":"completed","createdAt":"2026-10-23T08:00:00Z","result":$result}}""").build())
        val complete = api.getAnalysis("a4").result
        assertEquals(listOf("SQL"), complete?.advice?.missingSkills)
        val bare = api.getAnalysis("a5").result
        assertEquals(null, bare?.advice)
    }

    @Test
    fun errorBodiesBecomeHttpFailuresWithTheirCode() = runTest {
        server.enqueue(MockResponse.Builder().code(402).body("""{"error":{"code":"quota_reached"}}""").build())
        val failure = assertFailsWith<NetworkFailure.Http> { api.getAnalysis("a1") }
        assertEquals(402, failure.statusCode)
        assertEquals("quota_reached", failure.errorCode)
    }

    @Test
    fun deleteFailuresAreReported() = runTest {
        server.enqueue(MockResponse.Builder().code(404).body("""{"error":{"code":"not_found"}}""").build())
        val failure = assertFailsWith<NetworkFailure.Http> { api.deleteAlert("missing") }
        assertEquals("not_found", failure.errorCode)
    }

    @Test
    fun malformedJsonIsAnInvalidResponse() = runTest {
        server.enqueue(MockResponse.Builder().code(200).body("""{"unexpected": """).build())
        assertFailsWith<NetworkFailure.InvalidResponse> { api.getAccount() }
    }

    @Test
    fun slowServersTimeOut() = runTest {
        server.enqueue(MockResponse.Builder().code(200).body("{}").headersDelay(3, TimeUnit.SECONDS).build())
        assertFailsWith<NetworkFailure.Timeout> { api.getSubscription() }
    }

    @Test
    fun favoritesUsePutAndDelete() = runTest {
        server.enqueue(MockResponse.Builder().code(204).build())
        server.enqueue(MockResponse.Builder().code(204).build())
        api.addFavorite("a1")
        api.removeFavorite("a1")
        val added = server.takeRequest()
        assertEquals("PUT", added.method)
        assertEquals("/v1/analyses/a1/favorite", added.url.encodedPath)
        val removed = server.takeRequest()
        assertEquals("DELETE", removed.method)
        assertEquals("/v1/analyses/a1/favorite", removed.url.encodedPath)
    }
}
