package com.meridian.compass.sensors

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import kotlin.math.floor

class HapticStepManager(context: Context) {

    private val vibrator: Vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
        vm.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
    }

    var enabled: Boolean = true
    var stepIntervalDegrees: Int = 5
        set(value) {
            field = value.coerceIn(1, 45)
        }

    private var lastStepIndex: Int? = null
    private val hysteresisDegrees = 1.2f
    private var lastFiredHeading: Float? = null

    private val majorHeadings = setOf(0, 45, 90, 135, 180, 225, 270, 315)

    fun onHeadingUpdated(headingDegrees: Float) {
        if (!enabled) return
        val step = stepIntervalDegrees
        val stepIndex = floor(headingDegrees / step).toInt()

        val last = lastStepIndex
        if (last == null) {
            lastStepIndex = stepIndex
            lastFiredHeading = headingDegrees
            return
        }

        if (stepIndex != last) {
            val lastFired = lastFiredHeading ?: headingDegrees
            val movedEnough = angularDistance(headingDegrees, lastFired) >= (0f).coerceAtLeast(hysteresisDegrees - 0.01f)
            if (movedEnough) {
                lastStepIndex = stepIndex
                lastFiredHeading = headingDegrees
                val nearestMajor = ((headingDegrees / 45f).let { kotlin.math.round(it) }.toInt() * 45) % 360
                val isMajor = majorHeadings.contains(nearestMajor) && angularDistance(headingDegrees, nearestMajor.toFloat()) < (step / 2f)
                fireClick(strong = isMajor)
            } else {
                lastStepIndex = stepIndex
            }
        }
    }

    private fun angularDistance(a: Float, b: Float): Float {
        var d = kotlin.math.abs(a - b) % 360f
        if (d > 180f) d = 360f - d
        return d
    }

    private fun fireClick(strong: Boolean) {
        if (!vibrator.hasVibrator()) return
        val durationMs = if (strong) 18L else 9L
        val amplitude = if (strong) 90 else 40
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val effect = VibrationEffect.createOneShot(durationMs, amplitude.coerceIn(1, 255))
            vibrator.vibrate(effect)
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(durationMs)
        }
    }

    fun reset() {
        lastStepIndex = null
        lastFiredHeading = null
    }

    fun fireArrivalCelebration() {
        if (!vibrator.hasVibrator()) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val pattern = longArrayOf(0, 60, 80, 60)
            val amplitudes = intArrayOf(0, 180, 0, 180)
            vibrator.vibrate(VibrationEffect.createWaveform(pattern, amplitudes, -1))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(longArrayOf(0, 60, 80, 60), -1)
        }
    }
}
