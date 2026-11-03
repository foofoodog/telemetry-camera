package com.telemetry.camera.sensors

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Looper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class LocationData(
    val latitude: Double,
    val longitude: Double,
    val altitudeMeters: Double?,
    val accuracyMeters: Float?,
    val speedMps: Float?,
    val bearingDegrees: Float?,
    val declination: Float = 0f,
    val sunPosition: CelestialPosition? = null,
    val moonPosition: CelestialPosition? = null,
    val timestamp: Long
)

class LocationTracker(context: Context) {

    private val locationManager =
        context.getSystemService(Context.LOCATION_SERVICE) as LocationManager

    private val _locationFlow = MutableStateFlow<LocationData?>(null)
    val locationFlow: StateFlow<LocationData?> = _locationFlow.asStateFlow()
    val currentLocation: LocationData?
        get() = _locationFlow.value

    private val locationListener = object : LocationListener {
        override fun onLocationChanged(loc: Location) {
            if (isBetterLocation(loc, _locationFlow.value)) {
                updateLocation(loc)
            }
        }

        @Deprecated("Deprecated in Java")
        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
        override fun onProviderEnabled(provider: String) {}
        override fun onProviderDisabled(provider: String) {}
    }

    @SuppressLint("MissingPermission")
    fun start() {
        try {
            // Seed with most recent last-known location across available providers
            val lastGps = runCatching {
                locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
            }.getOrNull()
            val lastNetwork = runCatching {
                locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
            }.getOrNull()

            val bestLast = when {
                lastGps != null && lastNetwork != null ->
                    if (lastGps.time >= lastNetwork.time) lastGps else lastNetwork
                lastGps != null -> lastGps
                else -> lastNetwork
            }
            bestLast?.let { updateLocation(it) }

            // Register location updates with available providers (GPS & Network)
            val providers = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
            val availableProviders = locationManager.allProviders
            for (provider in providers) {
                if (availableProviders.contains(provider)) {
                    try {
                        locationManager.requestLocationUpdates(
                            provider,
                            1000L, // 1 second interval
                            0f,    // 0 meters min distance
                            locationListener,
                            Looper.getMainLooper()
                        )
                    } catch (e: Exception) {
                        // Ignore individual provider registration failure
                    }
                }
            }
        } catch (e: SecurityException) {
            // Permission not granted
        }
    }

    fun stop() {
        try {
            locationManager.removeUpdates(locationListener)
        } catch (e: SecurityException) {
            // Permission not granted or already removed
        }
    }

    private fun isBetterLocation(newLoc: Location, current: LocationData?): Boolean {
        if (current == null) return true

        val timeDelta = newLoc.time - current.timestamp
        val isSignificantlyNewer = timeDelta > 10_000L
        val isSignificantlyOlder = timeDelta < -10_000L

        if (isSignificantlyNewer) return true
        if (isSignificantlyOlder) return false

        val currentAccuracy = current.accuracyMeters ?: Float.MAX_VALUE
        val isMoreAccurate = newLoc.accuracy <= currentAccuracy

        // Prefer GPS fixes unless significantly less accurate than existing fix
        if (newLoc.provider == LocationManager.GPS_PROVIDER) {
            return isMoreAccurate || (newLoc.accuracy - currentAccuracy) < 20f || timeDelta > 0
        }

        return isMoreAccurate
    }

    private fun updateLocation(loc: Location) {
        val altFloat = if (loc.hasAltitude()) loc.altitude.toFloat() else 0f
        val geomag = android.hardware.GeomagneticField(
            loc.latitude.toFloat(),
            loc.longitude.toFloat(),
            altFloat,
            loc.time
        )
        val declination = geomag.declination

        val sun = EphemerisCalculator.calculateSunPosition(loc.latitude, loc.longitude, loc.time)
        val moon = EphemerisCalculator.calculateMoonPosition(loc.latitude, loc.longitude, loc.time)

        _locationFlow.value = LocationData(
            latitude = loc.latitude,
            longitude = loc.longitude,
            altitudeMeters = if (loc.hasAltitude()) loc.altitude else null,
            accuracyMeters = if (loc.hasAccuracy()) loc.accuracy else null,
            speedMps = if (loc.hasSpeed()) loc.speed else null,
            bearingDegrees = if (loc.hasBearing()) loc.bearing else null,
            declination = declination,
            sunPosition = sun,
            moonPosition = moon,
            timestamp = loc.time
        )
    }
}
