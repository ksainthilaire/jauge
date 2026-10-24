package io.jauge.core.network

import io.jauge.core.common.configuration.AppEnvironment
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import org.koin.core.module.Module
import org.koin.core.qualifier.named
import org.koin.dsl.module
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.Locale
import java.util.concurrent.TimeUnit

/** Android client: OkHttp (timeouts, authorization, language, debug logs) and Retrofit. */
internal actual fun platformNetworkModule(): Module = module {
    single {
        val environment: AppEnvironment = get()
        val tokenProvider: AccessTokenProvider = get()
        OkHttpClient.Builder()
            .connectTimeout(NetworkTimeouts.CONNECT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(NetworkTimeouts.READ_WRITE_SECONDS, TimeUnit.SECONDS)
            .writeTimeout(NetworkTimeouts.READ_WRITE_SECONDS, TimeUnit.SECONDS)
            .addInterceptor(headersInterceptor(tokenProvider))
            .apply {
                // Debug builds only: method, URL, status and duration. Headers and bodies are never logged, and the
                // authorization header is redacted in case the level is raised while debugging.
                if (environment.isDebugBuild) {
                    addInterceptor(
                        HttpLoggingInterceptor().apply {
                            level = HttpLoggingInterceptor.Level.BASIC
                            redactHeader("Authorization")
                            redactHeader("Cookie")
                        },
                    )
                }
            }
            .build()
    }
    single {
        val environment: AppEnvironment = get()
        Retrofit.Builder()
            .baseUrl(environment.apiBaseUrl)
            .client(get())
            .addConverterFactory(JaugeJson.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(RetrofitJaugeService::class.java)
    }
    single<JaugeApi>(named(REMOTE_API)) { RetrofitJaugeApi(get()) }
}

/**
 * Adds the language of the user and the access token of the session to every request.
 *
 * @param tokenProvider source of the access token.
 */
private fun headersInterceptor(tokenProvider: AccessTokenProvider) = Interceptor { chain ->
    val token = runBlocking { tokenProvider.accessToken() }
    val request = chain.request().newBuilder()
        .header("Accept", "application/json")
        .header("Accept-Language", Locale.getDefault().toLanguageTag())
        .apply { if (!token.isNullOrBlank()) header("Authorization", "Bearer $token") }
        .build()
    chain.proceed(request)
}
