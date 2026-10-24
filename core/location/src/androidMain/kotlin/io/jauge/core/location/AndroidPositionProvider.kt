package io.jauge.core.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.os.CancellationSignal
import androidx.core.content.ContextCompat
import io.jauge.core.domain.model.LocationPermission
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

/**
 * Android: approximate position from [LocationManager] (network or fused provider), without Google Play services.
 * The permission is asked once by [LocationPermissionActivity]; a refusal is remembered so that the app does not ask
 * again by itself.
 *
 * @param context application context.
 */
class AndroidPositionProvider(private val context: Context) : DevicePositionProvider {

    private val preferences by lazy { context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE) }

    override suspend fun permission(): LocationPermission = when {
        isGranted() -> LocationPermission.GRANTED
        preferences.getBoolean(KEY_ASKED, false) -> LocationPermission.DENIED
        else -> LocationPermission.NOT_DETERMINED
    }

    override suspend fun requestPermission(): LocationPermission {
        if (isGranted()) return LocationPermission.GRANTED
        preferences.edit().putBoolean(KEY_ASKED, true).apply()
        val granted = withContext(Dispatchers.Main) { LocationPermissionActivity.request(context) }
        return if (granted || isGranted()) LocationPermission.GRANTED else LocationPermission.DENIED
    }

    @SuppressLint("MissingPermission")
    override suspend fun currentPosition(): DevicePosition? {
        if (!isGranted()) return null
        val manager = ContextCompat.getSystemService(context, LocationManager::class.java) ?: return null
        val providers = buildList {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) add(LocationManager.FUSED_PROVIDER)
            add(LocationManager.NETWORK_PROVIDER)
            add(LocationManager.PASSIVE_PROVIDER)
        }.filter { runCatching { manager.isProviderEnabled(it) }.getOrDefault(false) }
        if (providers.isEmpty()) return null
        val fresh = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            withTimeoutOrNull(TIMEOUT_MILLISECONDS) { currentLocation(manager, providers.first()) }
        } else {
            null
        }
        val location = fresh ?: providers.firstNotNullOfOrNull { runCatching { manager.getLastKnownLocation(it) }.getOrNull() }
        return location?.let { DevicePosition(it.latitude, it.longitude) }
    }

    @SuppressLint("MissingPermission", "NewApi")
    private suspend fun currentLocation(manager: LocationManager, provider: String): Location? =
        suspendCancellableCoroutine { continuation ->
            val cancellation = CancellationSignal()
            continuation.invokeOnCancellation { cancellation.cancel() }
            runCatching {
                manager.getCurrentLocation(provider, cancellation, ContextCompat.getMainExecutor(context)) { location ->
                    if (continuation.isActive) continuation.resume(location)
                }
            }.onFailure { if (continuation.isActive) continuation.resume(null) }
        }

    private fun isGranted(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

    private companion object {
        const val PREFERENCES = "jauge_location"
        const val KEY_ASKED = "permission_asked"
        const val TIMEOUT_MILLISECONDS = 8_000L
    }
}
