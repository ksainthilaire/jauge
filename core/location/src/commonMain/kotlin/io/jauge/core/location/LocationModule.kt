package io.jauge.core.location

import io.jauge.core.domain.repository.LocationRepository
import org.koin.core.module.Module
import org.koin.dsl.module

/** Koin module of `core:location`: the platform [DevicePositionProvider] and the [LocationRepository]. */
val locationModule = module {
    includes(platformLocationModule())
    single<LocationRepository> { LocationRepositoryImplementation(get()) }
}

/** Declares the [DevicePositionProvider] of the platform: LocationManager on Android, CoreLocation on iOS. */
internal expect fun platformLocationModule(): Module
