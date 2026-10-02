package com.meridian.compass.location

import android.annotation.SuppressLint
import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.Looper
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.callbackFlow

data class LocationState(
    val latitude: Double? = null,
    val longitude: Double? = null,
    val horizontalAccuracyMeters: Float? = null,
    val gpsAltitudeMeters: Double? = null,
    val barometricAltitudeMeters: Double? = null,
    val hasFix: Boolean = false,
    val activeProvider: String? = null
) {
    val bestAltitudeMeters: Double?
        get() = barometricAltitudeMeters ?: gpsAltitudeMeters

    val altitudeIsRoughEstimate: Boolean
        get() = barometricAltitudeMeters == null && (horizontalAccuracyMeters == null || horizontalAccuracyMeters > 15f)
}

class LocationRepository(private val context: Context) {

    private val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val pressureSensor: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_PRESSURE)

    private val _state = MutableStateFlow(LocationState())
    val state: StateFlow<LocationState> = _state

    private val seaLevelReferenceHPa = 1013.25f

    fun hasBarometer(): Boolean = pressureSensor != null

    private val pressureListener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent) {
            val pressureHpa = event.values[0]
            val altitude = SensorManager.getAltitude(seaLevelReferenceHPa, pressureHpa)
            _state.value = _state.value.copy(barometricAltitudeMeters = altitude.toDouble())
        }
        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
    }

    fun startBarometer() {
        pressureSensor?.let {
            sensorManager.registerListener(this.pressureListener, it, SensorManager.SENSOR_DELAY_NORMAL)
        }
    }

    fun stopBarometer() {
        sensorManager.unregisterListener(pressureListener)
    }

    @SuppressLint("MissingPermission")
    fun locationUpdates() = callbackFlow<LocationState> {
        val listener = LocationListener { location -> emitLocation(location) }

        val candidateProviders = buildList {
            add(LocationManager.GPS_PROVIDER)
            add(LocationManager.NETWORK_PROVIDER)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                add(LocationManager.FUSED_PROVIDER)
            }
        }

        val registeredProviders = mutableListOf<String>()
        for (provider in candidateProviders) {
            if (locationManager.allProviders.contains(provider) && isProviderUsable(provider)) {
                runCatching {
                    locationManager.requestLocationUpdates(provider, 1000L, 0f, listener, Looper.getMainLooper())
                    registeredProviders += provider
                }
            }
        }

        registeredProviders
            .mapNotNull { provider -> runCatching { locationManager.getLastKnownLocation(provider) }.getOrNull() }
            .maxByOrNull { it.time }
            ?.let { emitLocation(it) }

        awaitClose { locationManager.removeUpdates(listener) }
    }

    private fun isProviderUsable(provider: String): Boolean = try {
        locationManager.isProviderEnabled(provider)
    } catch (e: Exception) {
        false
    }

    private fun emitLocation(location: Location) {
        val updated = _state.value.copy(
            latitude = location.latitude,
            longitude = location.longitude,
            horizontalAccuracyMeters = if (location.hasAccuracy()) location.accuracy else null,
            gpsAltitudeMeters = if (location.hasAltitude()) location.altitude else null,
            hasFix = true,
            activeProvider = location.provider
        )
        _state.value = updated
    }
}
