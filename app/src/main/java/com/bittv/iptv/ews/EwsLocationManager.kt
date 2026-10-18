package com.bittv.iptv.ews

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.os.Looper
import androidx.core.content.ContextCompat
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

/** Foreground location acquisition only; background EWS reads EwsLocationStore. */
object EwsLocationManager {
    fun hasPermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

    suspend fun refreshAndSave(context: Context): EwsLocationStore.SavedLocation? {
        if (!hasPermission(context)) return EwsLocationStore.read(context)
        val manager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
            ?: return EwsLocationStore.read(context)

        val current = runCatching {
            withTimeoutOrNull(8_000L) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    getCurrentLocationApi30(context, manager)
                } else {
                    getCurrentLocationLegacy(manager)
                }
            }
        }.getOrNull()

        val location = current ?: bestLastKnownLocation(manager)
        if (location != null && location.latitude.isFinite() && location.longitude.isFinite()) {
            EwsLocationStore.save(context, location.latitude, location.longitude)
        }
        return EwsLocationStore.read(context)
    }

    private suspend fun getCurrentLocationApi30(context: Context, manager: LocationManager): Location? =
        suspendCancellableCoroutine { continuation ->
            val provider = when {
                runCatching { manager.isProviderEnabled(LocationManager.GPS_PROVIDER) }.getOrDefault(false) -> LocationManager.GPS_PROVIDER
                runCatching { manager.isProviderEnabled(LocationManager.NETWORK_PROVIDER) }.getOrDefault(false) -> LocationManager.NETWORK_PROVIDER
                else -> null
            }
            if (provider == null) {
                continuation.resume(null)
                return@suspendCancellableCoroutine
            }

            val cancellationSignal = android.os.CancellationSignal()
            continuation.invokeOnCancellation { cancellationSignal.cancel() }
            try {
                manager.getCurrentLocation(
                    provider,
                    cancellationSignal,
                    ContextCompat.getMainExecutor(context),
                    { location -> if (continuation.isActive) continuation.resume(location) }
                )
            } catch (_: Throwable) {
                if (continuation.isActive) continuation.resume(null)
            }
        }

    @Suppress("DEPRECATION")
    private suspend fun getCurrentLocationLegacy(manager: LocationManager): Location? =
        suspendCancellableCoroutine { continuation ->
            val provider = when {
                runCatching { manager.isProviderEnabled(LocationManager.GPS_PROVIDER) }.getOrDefault(false) -> LocationManager.GPS_PROVIDER
                runCatching { manager.isProviderEnabled(LocationManager.NETWORK_PROVIDER) }.getOrDefault(false) -> LocationManager.NETWORK_PROVIDER
                else -> null
            }
            if (provider == null) {
                continuation.resume(null)
                return@suspendCancellableCoroutine
            }

            val listener = object : android.location.LocationListener {
                override fun onLocationChanged(location: Location) {
                    runCatching { manager.removeUpdates(this) }
                    if (continuation.isActive) continuation.resume(location)
                }
            }
            continuation.invokeOnCancellation { runCatching { manager.removeUpdates(listener) } }
            try {
                manager.requestSingleUpdate(provider, listener, Looper.getMainLooper())
            } catch (_: Throwable) {
                if (continuation.isActive) continuation.resume(null)
            }
        }

    private fun bestLastKnownLocation(manager: LocationManager): Location? {
        val gps = runCatching { manager.getLastKnownLocation(LocationManager.GPS_PROVIDER) }.getOrNull()
        val network = runCatching { manager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER) }.getOrNull()
        return listOfNotNull(gps, network).maxByOrNull { it.time }
    }
}
