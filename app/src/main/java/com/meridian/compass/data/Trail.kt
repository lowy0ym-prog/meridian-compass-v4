package com.meridian.compass.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "trails")
data class Trail(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val startedAtMillis: Long = System.currentTimeMillis(),
    val endedAtMillis: Long? = null,
    val distanceMeters: Double = 0.0
)

@Entity(tableName = "trail_points")
data class TrailPoint(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val trailId: Long,
    val latitude: Double,
    val longitude: Double,
    val altitudeMeters: Double? = null,
    val timestampMillis: Long = System.currentTimeMillis()
)
