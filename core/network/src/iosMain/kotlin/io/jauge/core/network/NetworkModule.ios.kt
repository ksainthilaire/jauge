package io.jauge.core.network

import io.jauge.core.common.configuration.AppEnvironment
import io.ktor.client.HttpClient
import io.ktor.client.engine.darwin.Darwin
import io.ktor.client.plugins.HttpSend
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.plugin
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.header
import io.ktor.http.HttpHeaders
import io.ktor.serialization.kotlinx.json.json
import org.koin.core.module.Module
import org.koin.core.qualifier.named
import org.koin.dsl.module
import platform.Foundation.NSLocale
import platform.Foundation.currentLocale
import platform.Foundation.languageCode

/** iOS client: Ktor with the Darwin engine, JSON, timeouts, language and authorization headers. */
internal actual fun platformNetworkModule(): Module = module {
    single {
        val environment: AppEnvironment = get()
        val tokenProvider: AccessTokenProvider = get()
        HttpClient(Darwin) {
            expectSuccess = false
            install(ContentNegotiation) { json(JaugeJson) }
            install(HttpTimeout) {
                connectTimeoutMillis = NetworkTimeouts.CONNECT_SECONDS * 1_000
                requestTimeoutMillis = NetworkTimeouts.READ_WRITE_SECONDS * 1_000
                socketTimeoutMillis = NetworkTimeouts.READ_WRITE_SECONDS * 1_000
            }
            defaultRequest {
                url(environment.apiBaseUrl)
                header(HttpHeaders.Accept, "application/json")
                header(HttpHeaders.AcceptLanguage, NSLocale.currentLocale.languageCode)
            }
        }.also { client ->
            client.plugin(HttpSend).intercept { request ->
                val token = tokenProvider.accessToken()
                if (!token.isNullOrBlank()) request.headers.append(HttpHeaders.Authorization, "Bearer $token")
                execute(request)
            }
        }
    }
    single<JaugeApi>(named(REMOTE_API)) { KtorJaugeApi(get()) }
}
