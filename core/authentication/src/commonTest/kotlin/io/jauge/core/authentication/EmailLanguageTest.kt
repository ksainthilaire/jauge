package io.jauge.core.authentication

import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.FlowType
import io.github.jan.supabase.auth.MemoryCodeVerifierCache
import io.github.jan.supabase.auth.MemorySessionManager
import io.github.jan.supabase.createSupabaseClient
import io.jauge.core.domain.model.Outcome
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.toByteArray
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class EmailLanguageTest {

    @Test
    fun englishTagsGiveEnglishAndEverythingElseFrench() {
        assertEquals(EmailLanguage.ENGLISH, EmailLanguage.fromLanguageTag("en"))
        assertEquals(EmailLanguage.ENGLISH, EmailLanguage.fromLanguageTag("en-GB"))
        assertEquals(EmailLanguage.ENGLISH, EmailLanguage.fromLanguageTag("EN_us"))
        assertEquals(EmailLanguage.FRENCH, EmailLanguage.fromLanguageTag("fr-FR"))
        assertEquals(EmailLanguage.FRENCH, EmailLanguage.fromLanguageTag("de"))
        assertEquals(EmailLanguage.FRENCH, EmailLanguage.fromLanguageTag("eng-not-a-tag"))
        assertEquals(EmailLanguage.FRENCH, EmailLanguage.fromLanguageTag(""))
        assertEquals(EmailLanguage.FRENCH, EmailLanguage.fromLanguageTag(null))
    }

    @Test
    fun magicLinkRequestCarriesTheEnglishLocale() = runTest {
        val body = sentMagicLinkBody(languageTag = "en-US")
        assertEquals("ines@example.com", body.getValue("email").jsonPrimitive.content)
        assertEquals("en", body.getValue("data").jsonObject.getValue("locale").jsonPrimitive.content)
    }

    @Test
    fun magicLinkRequestCarriesFrenchByDefault() = runTest {
        assertEquals("fr", sentMagicLinkBody(languageTag = "fr-FR").getValue("data").jsonObject.getValue("locale").jsonPrimitive.content)
        assertEquals("fr", sentMagicLinkBody(languageTag = null).getValue("data").jsonObject.getValue("locale").jsonPrimitive.content)
    }

    /** Sends a magic link through a Supabase client whose HTTP calls are captured, and returns the JSON body of `/otp`. */
    private suspend fun sentMagicLinkBody(languageTag: String?): JsonObject {
        val bodies = mutableListOf<Pair<String, String>>()
        val client = createSupabaseClient(supabaseUrl = "https://project.supabase.co", supabaseKey = "sb_publishable_test") {
            httpEngine = MockEngine { request ->
                bodies += request.url.encodedPath to request.body.toByteArray().decodeToString()
                respond("{}", HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
            }
            install(Auth) {
                sessionManager = MemorySessionManager()
                codeVerifierCache = MemoryCodeVerifierCache()
                flowType = FlowType.PKCE
                autoLoadFromStorage = false
                alwaysAutoRefresh = false
                enableLifecycleCallbacks = false
            }
        }
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            val repository = SupabaseAuthenticationRepository(client, scope) { languageTag }
            assertIs<Outcome.Success<Unit>>(repository.sendMagicLink("ines@example.com"))
        } finally {
            scope.cancel()
            client.close()
        }
        val otpBody = bodies.single { (path, _) -> path.endsWith("/otp") }.second
        return Json.parseToJsonElement(otpBody).jsonObject
    }
}
