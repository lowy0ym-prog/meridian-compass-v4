package com.meridian.compass

import android.app.Application
import android.content.Context
import android.hardware.GeomagneticField
import android.os.Build
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import kotlin.math.abs
import kotlin.math.min

/** Subtle click-style haptics. */
class Haptics(ctx: Context) {
    private val vib: Vibrator? = findVibrator(ctx)

    @Suppress("DEPRECATION")
    private fun findVibrator(ctx: Context): Vibrator? =
        if (Build.VERSION.SDK_INT >= 31) {
            ctx.getSystemService(VibratorManager::class.java)?.defaultVibrator
        } else {
            ctx.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }

    fun tick(strong: Boolean) {
        val v = vib ?: return
        if (!v.hasVibrator()) return
        try {
            if (Build.VERSION.SDK_INT >= 30) {
                val effect = if (strong) VibrationEffect.EFFECT_CLICK else VibrationEffect.EFFECT_TICK
                if (v.areAllEffectsSupported(effect) == Vibrator.VIBRATION_EFFECT_SUPPORT_YES) {
                    v.vibrate(VibrationEffect.createPredefined(effect))
                    return
                }
            }
            v.vibrate(VibrationEffect.createOneShot(if (strong) 22L else 8L, if (strong) 160 else 60))
        } catch (e: Exception) {
            // ignore vibrator errors
        }
    }
}

/**
 * Detects crossings of fixed heading increments ("detents") with hysteresis so that sensor jitter
 * around a boundary cannot retrigger. Returns the boundary angle crossed, or null.
 */
class StepTracker {
    private var last = -1
    private var lastTime = 0L

    fun reset() {
        last = -1
    }

    fun update(h: Float, step: Int): Int? {
        val bins = 360 / step
        val idx = (h / step).toInt().coerceIn(0, bins - 1)
        if (last < 0 || last >= bins) {
            last = idx
            return null
        }
        if (idx == last) return null
        val center = (last + 0.5f) * step
        val diff = Geo.angleDiff(h, center)
        val hyst = min(1.0f, step * 0.2f)
        if (abs(diff) < step / 2f + hyst) return null
        val boundary = if (diff > 0) ((last + 1) * step) % 360 else (last * step) % 360
        last = idx
        val now = SystemClock.elapsedRealtime()
        if (now - lastTime < 15) return null
        lastTime = now
        return boundary
    }
}

class AppModel(app: Application) : AndroidViewModel(app) {
    val settings = AppSettings(app)
    val waypoints = WaypointStore(app)
    val compass = CompassSensor(app)
    val location = LocationTracker(app)
    private val haptics = Haptics(app)
    private val tracker = StepTracker()

    /** Magnetic declination (degrees, east positive) at the last known location. */
    var declination by mutableFloatStateOf(0f); private set

    /** True North is only actually applied once we have a location fix. */
    val trueActive: Boolean get() = settings.trueNorth && location.location != null

    /** Heading in the currently active reference (True or Magnetic north). */
    val heading: Float
        get() = Geo.norm360(compass.magneticHeading + if (trueActive) declination else 0f)

    val selected: Waypoint?
        get() = waypoints.items.firstOrNull { it.id == waypoints.selectedId }

    /** Converts a TRUE-north bearing/azimuth to the active display reference. */
    fun toDisplay(trueDeg: Double): Float =
        Geo.norm360((trueDeg - (if (trueActive) 0f else declination)).toFloat())

    init {
        location.onFix = { l ->
            try {
                declination = GeomagneticField(
                    l.latitude.toFloat(),
                    l.longitude.toFloat(),
                    if (l.hasAltitude()) l.altitude.toFloat() else 0f,
                    System.currentTimeMillis()
                ).declination
            } catch (e: Exception) { }
        }
        location.location?.let { location.onFix?.invoke(it) }
        compass.onHeading = {
            if (settings.haptics && compass.hasReading) {
                val boundary = tracker.update(heading, settings.stepDeg)
                if (boundary != null) {
                    haptics.tick(settings.majorStrong && boundary % 90 == 0)
                }
            }
        }
    }

    fun setStep(v: Int) {
        settings.setStepDeg(v)
        tracker.reset()
    }

    fun onResume() {
        compass.start()
        location.start()
    }

    fun onPause() {
        compass.stop()
        location.stop()
        tracker.reset()
    }
}
