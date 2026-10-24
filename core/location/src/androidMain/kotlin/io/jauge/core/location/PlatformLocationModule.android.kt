package io.jauge.core.location

import org.koin.android.ext.koin.androidContext
import org.koin.core.module.Module
import org.koin.dsl.module

internal actual fun platformLocationModule(): Module = module {
    single<DevicePositionProvider> { AndroidPositionProvider(androidContext()) }
}
