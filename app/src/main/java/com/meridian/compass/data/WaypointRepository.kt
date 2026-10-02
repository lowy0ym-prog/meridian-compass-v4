package com.meridian.compass.data

import kotlinx.coroutines.flow.Flow

class WaypointRepository(private val dao: WaypointDao) {
    fun observeAll(): Flow<List<Waypoint>> = dao.observeAll()

    suspend fun save(waypoint: Waypoint): Long = dao.upsert(waypoint)

    suspend fun update(waypoint: Waypoint) = dao.update(waypoint)

    suspend fun delete(waypoint: Waypoint) = dao.delete(waypoint)

    suspend fun getById(id: Long): Waypoint? = dao.getById(id)
}
