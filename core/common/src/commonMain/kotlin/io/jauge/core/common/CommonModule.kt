package io.jauge.core.common

import io.jauge.core.common.configuration.ApiModeController
import io.jauge.core.common.coroutines.DefaultDispatcherProvider
import io.jauge.core.common.coroutines.DispatcherProvider
import org.koin.dsl.module

/**
 * Koin module of `core:common`. The [io.jauge.core.common.configuration.AppEnvironment] itself is declared by the
 * platform entry point (it knows whether the build is a debug build).
 */
val commonModule = module {
    single<DispatcherProvider> { DefaultDispatcherProvider() }
    single { ApiModeController(get()) }
}
