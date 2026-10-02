package com.meridian.compass.sensors

import android.content.Context
import android.hardware.GeomagneticField
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.view.Surface
import android.view.WindowManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlin.math.abs
import kotlin.math.roundToInt

enum class CompassAccuracy { UNRELIABLE, LOW, MEDIUM, HIGH }

data class OrientationState(
    val headingDegrees: Float = 0f,
    val pitchDegrees: Float = 0f,
    val rollDegrees: Float = 0f,
    val accuracy: CompassAccuracy = CompassAccuracy.MEDIUM,
    val magneticFieldMicroTesla: Float = 0f,
    val magneticInterferenceSuspected: Boolean = false,
    val usingRotationVector: Boolean = true
)

class OrientationSensorManager(private val context: Context) : SensorEventListener {

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager

    private val rotationVectorSensor: Sensor? =
        sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
    private val accelerometer: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private val magnetometer: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)

    private val rotationMatrix = FloatArray(9)
    private val remappedMatrix = FloatArray(9)
    private val orientationValues = FloatArray(3)

    private var lastAccel = FloatArray(3)
    private var lastMagnetic = FloatArray(3)
    private var haveAccel = false
    private var haveMagnetic = false

    private var smoothedHeading = 0f
    private var initialized = false

    private val _state = MutableStateFlow(OrientationState())
    val state: StateFlow<OrientationState> = _state

    private val smoothingAlpha = 0.18f

    fun start() {
        if (rotationVectorSensor != null) {
            sensorManager.registerListener(this, rotationVectorSensor, SensorManager.SENSOR_DELAY_GAME)
        } else {
            accelerometer?.let { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME) }
        }
        magnetometer?.let { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME) }
    }

    fun stop() {
        sensorManager.unregisterListener(this)
        initialized = false
    }

    override fun onSensorChanged(event: SensorEvent) {
        when (event.sensor.type) {
            Sensor.TYPE_ROTATION_VECTOR -> handleRotationVector(event)
            Sensor.TYPE_ACCELEROMETER -> {
                lastAccel = event.values.clone()
                haveAccel = true
                if (rotationVectorSensor == null) computeFromAccelMag()
            }
            Sensor.TYPE_MAGNETIC_FIELD -> {
                lastMagnetic = event.values.clone()
                haveMagnetic = true
                updateMagneticFieldReading(event.values)
                if (rotationVectorSensor == null) computeFromAccelMag()
            }
        }
    }

    private fun handleRotationVector(event: SensorEvent) {
        SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)
        remapForDisplayRotation(rotationMatrix)
        SensorManager.getOrientation(remappedMatrix, orientationValues)
        publishHeadingFromRadians(orientationValues[0], orientationValues[1], orientationValues[2], usingRotationVector = true)
    }

    private fun computeFromAccelMag() {
        if (!haveAccel || !haveMagnetic) return
        val success = SensorManager.getRotationMatrix(rotationMatrix, null, lastAccel, lastMagnetic)
        if (!success) return
        remapForDisplayRotation(rotationMatrix)
        SensorManager.getOrientation(remappedMatrix, orientationValues)
        publishHeadingFromRadians(orientationValues[0], orientationValues[1], orientationValues[2], usingRotationVector = false)
    }

    private fun remapForDisplayRotation(matrix: FloatArray) {
        val rotation = windowManager.defaultDisplay.rotation
        val (axisX, axisY) = when (rotation) {
            Surface.ROTATION_90 -> SensorManager.AXIS_Y to SensorManager.AXIS_MINUS_X
            Surface.ROTATION_180 -> SensorManager.AXIS_MINUS_X to SensorManager.AXIS_MINUS_Y
            Surface.ROTATION_270 -> SensorManager.AXIS_MINUS_Y to SensorManager.AXIS_X
            else -> SensorManager.AXIS_X to SensorManager.AXIS_Y
        }
        SensorManager.remapCoordinateSystem(matrix, axisX, axisY, remappedMatrix)
    }

    private fun publishHeadingFromRadians(azimuthRad: Float, pitchRad: Float, rollRad: Float, usingRotationVector: Boolean) {
        var azimuthDeg = Math.toDegrees(azimuthRad.toDouble()).toFloat()
        if (azimuthDeg < 0f) azimuthDeg += 360f

        if (!initialized) {
            smoothedHeading = azimuthDeg
            initialized = true
        } else {
            smoothedHeading = lowPassAngle(smoothedHeading, azimuthDeg, smoothingAlpha)
        }

        val current = _state.value
        _state.value = current.copy(
            headingDegrees = normalize(smoothedHeading),
            pitchDegrees = Math.toDegrees(pitchRad.toDouble()).toFloat(),
            rollDegrees = Math.toDegrees(rollRad.toDouble()).toFloat(),
            usingRotationVector = usingRotationVector
        )
    }

    private fun lowPassAngle(previous: Float, target: Float, alpha: Float): Float {
        var delta = target - previous
        if (delta > 180f) delta -= 360f
        if (delta < -180f) delta += 360f
        return normalize(previous + alpha * delta)
    }

    private fun normalize(deg: Float): Float {
        var d = deg % 360f
        if (d < 0f) d += 360f
        return d
    }

    private fun updateMagneticFieldReading(values: FloatArray) {
        val magnitude = kotlin.math.sqrt(values[0] * values[0] + values[1] * values[1] + values[2] * values[2])
        val interference = magnitude < 15f || magnitude > 90f
        val current = _state.value
        _state.value = current.copy(
            magneticFieldMicroTesla = magnitude,
            magneticInterferenceSuspected = interference
        )
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        if (sensor?.type != Sensor.TYPE_MAGNETIC_FIELD) return
        val mapped = when (accuracy) {
            SensorManager.SENSOR_STATUS_UNRELIABLE -> CompassAccuracy.UNRELIABLE
            SensorManager.SENSOR_STATUS_ACCURACY_LOW -> CompassAccuracy.LOW
            SensorManager.SENSOR_STATUS_ACCURACY_MEDIUM -> CompassAccuracy.MEDIUM
            SensorManager.SENSOR_STATUS_ACCURACY_HIGH -> CompassAccuracy.HIGH
            else -> CompassAccuracy.MEDIUM
        }
        _state.value = _state.value.copy(accuracy = mapped)
    }

    fun hasHardwareCompass(): Boolean = magnetometer != null

    companion object {
        fun declinationFor(latitude: Double, longitude: Double, altitudeMeters: Double, timeMillis: Long): Float {
            val field = GeomagneticField(
                latitude.toFloat(),
                longitude.toFloat(),
                altitudeMeters.toFloat(),
                timeMillis
            )
            return field.declination
        }
    }
}
