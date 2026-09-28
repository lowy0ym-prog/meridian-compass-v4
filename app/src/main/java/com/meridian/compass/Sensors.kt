package com.meridian.compass

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Compass built on Android's fused ROTATION_VECTOR sensor (gyro + accelerometer + magnetometer),
 * with extra circular low-pass smoothing. Also reports magnetometer accuracy/field strength
 * (for calibration + interference warnings) and barometric pressure when present.
 */
class CompassSensor(ctx: Context) : SensorEventListener {
    private val sm = ctx.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val rotSensor: Sensor? = sm.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
    private val magSensor: Sensor? = sm.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)
    private val pressSensor: Sensor? = sm.getDefaultSensor(Sensor.TYPE_PRESSURE)

    val hasCompass: Boolean = rotSensor != null
    val hasBarometer: Boolean = pressSensor != null

    /** Heading relative to MAGNETIC north, 0..360. */
    var magneticHeading by mutableFloatStateOf(0f); private set
    var hasReading by mutableStateOf(false); private set
    var accuracy by mutableIntStateOf(SensorManager.SENSOR_STATUS_UNRELIABLE); private set
    var fieldStrength by mutableFloatStateOf(0f); private set // microtesla
    var pressure by mutableFloatStateOf(0f); private set // hPa, 0 = none
    var onHeading: (() -> Unit)? = null

    private val rMat = FloatArray(9)
    private var sinF = 0f
    private var cosF = 0f
    private var seeded = false
    private var running = false

    fun start() {
        if (running) return
        running = true
        rotSensor?.let { sm.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME) }
        magSensor?.let { sm.registerListener(this, it, SensorManager.SENSOR_DELAY_UI) }
        pressSensor?.let { sm.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL) }
    }

    fun stop() {
        sm.unregisterListener(this)
        running = false
        seeded = false
    }

    override fun onSensorChanged(e: SensorEvent) {
        when (e.sensor.type) {
            Sensor.TYPE_ROTATION_VECTOR -> handleRotation(e.values)
            Sensor.TYPE_MAGNETIC_FIELD -> {
                val v = e.values
                val mag = sqrt(v[0] * v[0] + v[1] * v[1] + v[2] * v[2])
                fieldStrength = if (fieldStrength == 0f) mag else fieldStrength + 0.2f * (mag - fieldStrength)
                accuracy = e.accuracy
            }
            Sensor.TYPE_PRESSURE -> pressure = e.values[0]
        }
    }

    override fun onAccuracyChanged(sensor: Sensor, acc: Int) {
        if (sensor.type == Sensor.TYPE_MAGNETIC_FIELD) accuracy = acc
    }

    private fun handleRotation(values: FloatArray) {
        SensorManager.getRotationMatrixFromVector(rMat, values)
        // World frame: x=East, y=North, z=Up. rMat maps device -> world.
        val east: Float
        val north: Float
        if (abs(rMat[8]) > 0.75f) {
            // Phone roughly flat: heading of the top edge (device +Y axis).
            east = rMat[1]
            north = rMat[4]
        } else {
            // Phone upright: heading of the back camera direction (device -Z axis).
            east = -rMat[2]
            north = -rMat[5]
        }
        val h = atan2(east, north)
        if (!seeded) {
            sinF = sin(h)
            cosF = cos(h)
            seeded = true
        } else {
            val a = 0.12f
            sinF += a * (sin(h) - sinF)
            cosF += a * (cos(h) - cosF)
        }
        var deg = Math.toDegrees(atan2(sinF, cosF).toDouble()).toFloat()
        if (deg < 0) deg += 360f
        if (!hasReading || abs(Geo.angleDiff(deg, magneticHeading)) > 0.2f) {
            magneticHeading = deg
            hasReading = true
            onHeading?.invoke()
        }
    }
}

/** GPS/network location via the platform LocationManager (no Google Play Services, works offline for GPS). */
class LocationTracker(private val ctx: Context) : LocationListener {
    private val lm = ctx.getSystemService(Context.LOCATION_SERVICE) as LocationManager

    var location by mutableStateOf<Location?>(null); private set
    var permissionGranted by mutableStateOf(checkPermission()); private set
    var gpsEnabled by mutableStateOf(true); private set
    var onFix: ((Location) -> Unit)? = null
    private var listening = false

    private fun checkPermission(): Boolean =
        ctx.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ctx.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    fun refreshPermission() {
        permissionGranted = checkPermission()
    }

    @SuppressLint("MissingPermission")
    fun start() {
        refreshPermission()
        if (!permissionGranted || listening) return
        try {
            gpsEnabled = lm.isProviderEnabled(LocationManager.GPS_PROVIDER)
            if (location == null) {
                listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER, LocationManager.PASSIVE_PROVIDER)
                    .mapNotNull { p ->
                        try { lm.getLastKnownLocation(p) } catch (e: Exception) { null }
                    }
                    .maxByOrNull { it.time }
                    ?.let { accept(it) }
            }
            if (gpsEnabled) lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000L, 0f, this)
            if (lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                lm.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 5000L, 0f, this)
            }
            listening = true
        } catch (e: Exception) {
            // Permission revoked or provider unavailable.
        }
    }

    fun stop() {
        try { lm.removeUpdates(this) } catch (e: Exception) { }
        listening = false
    }

    private fun accept(l: Location) {
        val cur = location
        val better = cur == null ||
            l.provider == LocationManager.GPS_PROVIDER ||
            System.currentTimeMillis() - cur.time > 15000 ||
            l.accuracy <= cur.accuracy
        if (better) {
            location = l
            onFix?.invoke(l)
        }
    }

    override fun onLocationChanged(l: Location) {
        accept(l)
    }

    override fun onProviderEnabled(provider: String) {
        stop()
        start()
    }

    override fun onProviderDisabled(provider: String) {
        if (provider == LocationManager.GPS_PROVIDER) gpsEnabled = false
    }

    @Deprecated("Deprecated in Java")
    override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {
    }
}
