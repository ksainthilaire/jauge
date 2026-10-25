package io.jauge.core.location

import io.jauge.core.domain.model.LocationPermission
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.useContents
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import platform.CoreLocation.CLLocation
import platform.CoreLocation.CLLocationManager
import platform.CoreLocation.CLLocationManagerDelegateProtocol
import platform.CoreLocation.kCLAuthorizationStatusAuthorizedAlways
import platform.CoreLocation.kCLAuthorizationStatusAuthorizedWhenInUse
import platform.CoreLocation.kCLAuthorizationStatusNotDetermined
import platform.CoreLocation.kCLLocationAccuracyReduced
import platform.Foundation.NSError
import platform.darwin.NSObject

/**
 * iOS: CoreLocation "When In Use" with reduced accuracy (about a few kilometers), the explanation shown by the system
 * comes from `NSLocationWhenInUseUsageDescription` (`Info.plist`, translated in `InfoPlist.strings`).
 */
class IosPositionProvider : DevicePositionProvider {

    private val manager = CLLocationManager()
    private val delegate = Delegate()

    init {
        manager.delegate = delegate
        manager.desiredAccuracy = kCLLocationAccuracyReduced
    }

    override suspend fun permission(): LocationPermission = withContext(Dispatchers.Main) { currentPermission() }

    override suspend fun requestPermission(): LocationPermission = withContext(Dispatchers.Main) {
        if (currentPermission() != LocationPermission.NOT_DETERMINED) return@withContext currentPermission()
        val answer = CompletableDeferred<Unit>()
        delegate.onAuthorizationChange = { if (currentPermission() != LocationPermission.NOT_DETERMINED) answer.complete(Unit) }
        manager.requestWhenInUseAuthorization()
        withTimeoutOrNull(PERMISSION_TIMEOUT_MILLISECONDS) { answer.await() }
        delegate.onAuthorizationChange = null
        currentPermission()
    }

    @OptIn(ExperimentalForeignApi::class)
    override suspend fun currentPosition(): DevicePosition? = withContext(Dispatchers.Main) {
        if (currentPermission() != LocationPermission.GRANTED) return@withContext null
        val result = CompletableDeferred<CLLocation?>()
        delegate.onLocation = { location -> result.complete(location) }
        manager.requestLocation()
        val location = withTimeoutOrNull(LOCATION_TIMEOUT_MILLISECONDS) { result.await() } ?: manager.location
        delegate.onLocation = null
        location?.coordinate?.useContents { DevicePosition(latitude, longitude) }
    }

    private fun currentPermission(): LocationPermission = when (manager.authorizationStatus) {
        kCLAuthorizationStatusAuthorizedWhenInUse, kCLAuthorizationStatusAuthorizedAlways -> LocationPermission.GRANTED
        kCLAuthorizationStatusNotDetermined -> LocationPermission.NOT_DETERMINED
        else -> LocationPermission.DENIED
    }

    /** Receives the answers of CoreLocation on the main thread. */
    private class Delegate : NSObject(), CLLocationManagerDelegateProtocol {
        var onAuthorizationChange: (() -> Unit)? = null
        var onLocation: ((CLLocation?) -> Unit)? = null

        override fun locationManagerDidChangeAuthorization(manager: CLLocationManager) {
            onAuthorizationChange?.invoke()
        }

        override fun locationManager(manager: CLLocationManager, didUpdateLocations: List<*>) {
            onLocation?.invoke(didUpdateLocations.lastOrNull() as? CLLocation)
        }

        override fun locationManager(manager: CLLocationManager, didFailWithError: NSError) {
            onLocation?.invoke(null)
        }
    }

    private companion object {
        const val PERMISSION_TIMEOUT_MILLISECONDS = 120_000L
        const val LOCATION_TIMEOUT_MILLISECONDS = 8_000L
    }
}
