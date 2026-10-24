package io.jauge.core.network

import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * Koin module of the network layer: shared settings plus the platform client ([platformNetworkModule]).
 * The [JaugeApi] declared here is the remote client; `core:data` decides between it and the offline client.
 */
val networkModule: Module = module {
    single<AccessTokenProvider> { NoAccessTokenProvider }
    includes(platformNetworkModule())
}

/** Platform client: Retrofit and OkHttp on Android, Ktor on iOS. Declares a `RemoteJaugeApi` named binding. */
internal expect fun platformNetworkModule(): Module

/** Koin qualifier of the remote [JaugeApi]. */
const val REMOTE_API = "remoteJaugeApi"
