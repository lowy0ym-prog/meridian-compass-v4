package com.meridian.compass.util

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

object Bearing {
    private const val EARTH_RADIUS_METERS = 6_371_000.0

    private fun toRad(deg: Double) = Math.toRadians(deg)
    private fun toDeg(rad: Double) = Math.toDegrees(rad)

    fun distanceMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val dLat = toRad(lat2 - lat1)
        val dLon = toRad(lon2 - lon1)
        val a = sin(dLat / 2).pow(2) + cos(toRad(lat1)) * cos(toRad(lat2)) * sin(dLon / 2).pow(2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return EARTH_RADIUS_METERS * c
    }

    fun bearingDegrees(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val phi1 = toRad(lat1)
        val phi2 = toRad(lat2)
        val dLon = toRad(lon2 - lon1)
        val y = sin(dLon) * cos(phi2)
        val x = cos(phi1) * sin(phi2) - sin(phi1) * cos(phi2) * cos(dLon)
        var bearing = toDeg(atan2(y, x))
        bearing = (bearing + 360) % 360
        return bearing
    }

    fun metersToFeet(meters: Double): Double = meters * 3.28084
    fun metersToMiles(meters: Double): Double = meters / 1609.344
    fun metersToKilometers(meters: Double): Double = meters / 1000.0

    fun compassPointLabel(degrees: Double): String {
        val points = arrayOf(
            "N", "NNE", "NE", "ENE", "E", "ESE", "SE", "SSE",
            "S", "SSW", "SW", "WSW", "W", "WNW", "NW", "NNW"
        )
        val index = (((degrees % 360) / 22.5) + 0.5).toInt() % 16
        return points[index]
    }
}
