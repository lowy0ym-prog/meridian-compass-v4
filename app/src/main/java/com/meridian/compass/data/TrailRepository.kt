package com.meridian.compass.data

import kotlinx.coroutines.flow.Flow

class TrailRepository(private val dao: TrailDao) {
    fun observeAll(): Flow<List<Trail>> = dao.observeAll()
    fun observePoints(trailId: Long): Flow<List<TrailPoint>> = dao.observePoints(trailId)

    suspend fun startTrail(name: String): Long = dao.insertTrail(Trail(name = name))
    suspend fun finishTrail(trail: Trail, distanceMeters: Double) {
        dao.updateTrail(trail.copy(endedAtMillis = System.currentTimeMillis(), distanceMeters = distanceMeters))
    }
    suspend fun deleteTrail(trail: Trail) {
        dao.deletePointsForTrail(trail.id)
        dao.deleteTrail(trail)
    }
    suspend fun addPoint(trailId: Long, lat: Double, lon: Double, altitude: Double?) {
        dao.insertPoint(TrailPoint(trailId = trailId, latitude = lat, longitude = lon, altitudeMeters = altitude))
    }
}
