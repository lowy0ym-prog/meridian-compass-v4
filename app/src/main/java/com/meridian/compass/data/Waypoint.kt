package com.meridian.compass.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "waypoints")
data class Waypoint(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val altitudeMeters: Double? = null,
    val notes: String? = null,
    val category: String = MarkerCategory.OTHER.name,
    val photoPath: String? = null,
    val tripName: String = "General",
    val createdAtMillis: Long = System.currentTimeMillis()
)
