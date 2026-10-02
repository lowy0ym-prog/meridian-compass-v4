package com.meridian.compass.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.meridian.compass.MeridianApplication
import com.meridian.compass.data.Waypoint
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class WaypointViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = (application as MeridianApplication).waypointRepository

    val waypoints: StateFlow<List<Waypoint>> = repository.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun saveNew(name: String, lat: Double, lon: Double, altitude: Double?, category: com.meridian.compass.data.MarkerCategory, photoPath: String?) {
        viewModelScope.launch {
            repository.save(
                Waypoint(
                    name = name, latitude = lat, longitude = lon, altitudeMeters = altitude,
                    category = category.name, photoPath = photoPath
                )
            )
        }
    }

    fun update(waypoint: Waypoint) {
        viewModelScope.launch { repository.update(waypoint) }
    }

    fun delete(waypoint: Waypoint) {
        viewModelScope.launch { repository.delete(waypoint) }
    }
}
