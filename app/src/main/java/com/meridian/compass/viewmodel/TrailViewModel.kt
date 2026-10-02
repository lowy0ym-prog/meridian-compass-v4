package com.meridian.compass.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.meridian.compass.MeridianApplication
import com.meridian.compass.data.Trail
import com.meridian.compass.data.TrailPoint
import com.meridian.compass.location.LocationRepository
import com.meridian.compass.util.Bearing
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class TrailViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as MeridianApplication
    private val locationRepository = LocationRepository(application)

    private val _activeTrail = MutableStateFlow<Trail?>(null)
    val activeTrail: StateFlow<Trail?> = _activeTrail

    private val _recordedPoints = MutableStateFlow<List<TrailPoint>>(emptyList())
    val recordedPoints: StateFlow<List<TrailPoint>> = _recordedPoints

    private val _distanceMeters = MutableStateFlow(0.0)
    val distanceMeters: StateFlow<Double> = _distanceMeters

    val trails: StateFlow<List<Trail>> = app.trailRepository.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private var collectJob: Job? = null

    fun startRecording(name: String) {
        if (_activeTrail.value != null) return
        viewModelScope.launch {
            val id = app.trailRepository.startTrail(name.ifBlank { "Trail" })
            _activeTrail.value = Trail(id = id, name = name.ifBlank { "Trail" })
            _recordedPoints.value = emptyList()
            _distanceMeters.value = 0.0

            collectJob = viewModelScope.launch {
                locationRepository.locationUpdates().collect { loc ->
                    if (loc.latitude == null || loc.longitude == null) return@collect
                    val trail = _activeTrail.value ?: return@collect
                    val point = TrailPoint(
                        trailId = trail.id, latitude = loc.latitude, longitude = loc.longitude,
                        altitudeMeters = loc.bestAltitudeMeters
                    )
                    val previous = _recordedPoints.value.lastOrNull()
                    if (previous != null) {
                        _distanceMeters.value += Bearing.distanceMeters(
                            previous.latitude, previous.longitude, point.latitude, point.longitude
                        )
                    }
                    _recordedPoints.value = _recordedPoints.value + point
                    app.trailRepository.addPoint(trail.id, point.latitude, point.longitude, point.altitudeMeters)
                }
            }
        }
    }

    fun stopAndSave() {
        val trail = _activeTrail.value ?: return
        collectJob?.cancel()
        collectJob = null
        viewModelScope.launch {
            app.trailRepository.finishTrail(trail, _distanceMeters.value)
            _activeTrail.value = null
        }
    }

    fun discard() {
        val trail = _activeTrail.value ?: return
        collectJob?.cancel()
        collectJob = null
        viewModelScope.launch {
            app.trailRepository.deleteTrail(trail)
            _activeTrail.value = null
            _recordedPoints.value = emptyList()
            _distanceMeters.value = 0.0
        }
    }

    fun deleteTrail(trail: Trail) {
        viewModelScope.launch { app.trailRepository.deleteTrail(trail) }
    }

    override fun onCleared() {
        super.onCleared()
        collectJob?.cancel()
    }
}
