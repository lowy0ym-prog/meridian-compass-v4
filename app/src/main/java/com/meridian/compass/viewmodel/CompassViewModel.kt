package com.meridian.compass.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.meridian.compass.MeridianApplication
import com.meridian.compass.astronomy.MoonInfo
import com.meridian.compass.astronomy.SunInfo
import com.meridian.compass.astronomy.SunMoonCalculator
import com.meridian.compass.data.Waypoint
import com.meridian.compass.location.LocationRepository
import com.meridian.compass.location.LocationState
import com.meridian.compass.sensors.CompassAccuracy
import com.meridian.compass.sensors.HapticStepManager
import com.meridian.compass.sensors.OrientationSensorManager
import com.meridian.compass.sensors.OrientationState
import com.meridian.compass.settings.AppSettings
import com.meridian.compass.settings.NorthReference
import com.meridian.compass.util.Bearing
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.TimeZone

data class CompassUiState(
    val orientation: OrientationState = OrientationState(),
    val location: LocationState = LocationState(),
    val settings: AppSettings = AppSettings(),
    val declinationDegrees: Float = 0f,
    val displayHeadingDegrees: Float = 0f,
    val sunInfo: SunInfo? = null,
    val moonInfo: MoonInfo? = null,
    val selectedWaypoint: Waypoint? = null,
    val bearingToWaypointDegrees: Double? = null,
    val distanceToWaypointMeters: Double? = null,
    val relativeBearingDegrees: Double? = null,
    val relativeDirectionLabel: String? = null,
    val hasArrivedAtWaypoint: Boolean = false,
    val lockedBearingDegrees: Double? = null,
    val lockedBearingTurnDegrees: Double? = null,
    val lockedBearingLabel: String? = null
)

class CompassViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as MeridianApplication
    private val orientationManager = OrientationSensorManager(application)
    private val locationRepository = LocationRepository(application)
    val hapticManager = HapticStepManager(application)

    private val _selectedWaypoint = MutableStateFlow<Waypoint?>(null)
    private val _celestialTick = MutableStateFlow(System.currentTimeMillis())
    private val _lockedBearing = MutableStateFlow<Double?>(null)

    val uiState: StateFlow<CompassUiState> = combine(
        orientationManager.state,
        locationRepository.state,
        app.settingsRepository.settingsFlow,
        _selectedWaypoint,
        _celestialTick,
        _lockedBearing
    ) { flows: Array<Any?> ->
        @Suppress("UNCHECKED_CAST")
        val orientation = flows[0] as OrientationState
        @Suppress("UNCHECKED_CAST")
        val location = flows[1] as LocationState
        @Suppress("UNCHECKED_CAST")
        val settings = flows[2] as AppSettings
        val selected = flows[3] as Waypoint?
        val nowMillis = flows[4] as Long
        val lockedBearing = flows[5] as Double?

        val declination = if (location.latitude != null && location.longitude != null) {
            OrientationSensorManager.declinationFor(
                location.latitude, location.longitude, location.bestAltitudeMeters ?: 0.0, nowMillis
            )
        } else 0f

        val displayHeading = if (settings.northReference == NorthReference.TRUE) {
            var h = orientation.headingDegrees + declination
            h = ((h % 360f) + 360f) % 360f
            h
        } else {
            orientation.headingDegrees
        }

        var sun: SunInfo? = null
        var moon: MoonInfo? = null
        if (location.latitude != null && location.longitude != null) {
            val tz = TimeZone.getDefault()
            sun = SunMoonCalculator.sunInfo(location.latitude, location.longitude, nowMillis, tz)
            moon = SunMoonCalculator.moonInfo(location.latitude, location.longitude, nowMillis, tz)
        }

        var bearingToWaypoint: Double? = null
        var distanceToWaypoint: Double? = null
        var relativeBearing: Double? = null
        var relativeLabel: String? = null
        var arrived = false
        if (selected != null && location.latitude != null && location.longitude != null) {
            bearingToWaypoint = Bearing.bearingDegrees(location.latitude, location.longitude, selected.latitude, selected.longitude)
            distanceToWaypoint = Bearing.distanceMeters(location.latitude, location.longitude, selected.latitude, selected.longitude)

            var diff = bearingToWaypoint - displayHeading
            diff = ((diff + 540) % 360) - 180
            relativeBearing = diff
            relativeLabel = when {
                distanceToWaypoint < ARRIVAL_THRESHOLD_METERS -> "You're here"
                kotlin.math.abs(diff) <= 20 -> "Ahead"
                kotlin.math.abs(diff) >= 160 -> "Behind"
                diff > 0 -> "To your right"
                else -> "To your left"
            }
            arrived = distanceToWaypoint < ARRIVAL_THRESHOLD_METERS
        }

        var lockedTurn: Double? = null
        var lockedLabel: String? = null
        if (lockedBearing != null) {
            var diff = lockedBearing - displayHeading
            diff = ((diff + 540) % 360) - 180
            lockedTurn = diff
            lockedLabel = when {
                kotlin.math.abs(diff) <= 3 -> "ON BEARING"
                diff > 0 -> "TURN RIGHT"
                else -> "TURN LEFT"
            }
        }

        CompassUiState(
            orientation = orientation,
            location = location,
            settings = settings,
            declinationDegrees = declination,
            displayHeadingDegrees = displayHeading,
            sunInfo = sun,
            moonInfo = moon,
            selectedWaypoint = selected,
            bearingToWaypointDegrees = bearingToWaypoint,
            distanceToWaypointMeters = distanceToWaypoint,
            relativeBearingDegrees = relativeBearing,
            relativeDirectionLabel = relativeLabel,
            hasArrivedAtWaypoint = arrived,
            lockedBearingDegrees = lockedBearing,
            lockedBearingTurnDegrees = lockedTurn,
            lockedBearingLabel = lockedLabel
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), CompassUiState())

    init {
        viewModelScope.launch {
            app.settingsRepository.settingsFlow.collect { settings ->
                hapticManager.enabled = settings.hapticEnabled
                hapticManager.stepIntervalDegrees = settings.hapticStepDegrees
            }
        }
        viewModelScope.launch {
            orientationManager.state.collect { state ->
                hapticManager.onHeadingUpdated(state.headingDegrees)
            }
        }
    }

    fun startSensors() {
        orientationManager.start()
        locationRepository.startBarometer()
    }

    fun stopSensors() {
        orientationManager.stop()
        locationRepository.stopBarometer()
    }

    fun beginLocationUpdates() {
        viewModelScope.launch {
            locationRepository.locationUpdates().collect { }
        }
    }

    fun refreshCelestialClock() {
        _celestialTick.value = System.currentTimeMillis()
    }

    fun selectWaypoint(waypoint: Waypoint?) {
        _selectedWaypoint.value = waypoint
    }

    fun lockCurrentBearing() {
        _lockedBearing.value = uiState.value.displayHeadingDegrees.toDouble()
    }

    fun releaseLockedBearing() {
        _lockedBearing.value = null
    }

    fun hasHardwareCompass(): Boolean = orientationManager.hasHardwareCompass()
    fun hasBarometer(): Boolean = locationRepository.hasBarometer()

    fun accuracyLabel(accuracy: CompassAccuracy): String = when (accuracy) {
        CompassAccuracy.UNRELIABLE -> "Unreliable — please calibrate"
        CompassAccuracy.LOW -> "Low accuracy"
        CompassAccuracy.MEDIUM -> "Medium accuracy"
        CompassAccuracy.HIGH -> "High accuracy"
    }

    companion object {
        const val ARRIVAL_THRESHOLD_METERS = 8.0
    }
}
