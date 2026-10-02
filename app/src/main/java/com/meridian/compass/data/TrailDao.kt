package com.meridian.compass.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface TrailDao {
    @Query("SELECT * FROM trails ORDER BY startedAtMillis DESC")
    fun observeAll(): Flow<List<Trail>>

    @Query("SELECT * FROM trail_points WHERE trailId = :trailId ORDER BY timestampMillis ASC")
    fun observePoints(trailId: Long): Flow<List<TrailPoint>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTrail(trail: Trail): Long

    @Update
    suspend fun updateTrail(trail: Trail)

    @Delete
    suspend fun deleteTrail(trail: Trail)

    @Insert
    suspend fun insertPoint(point: TrailPoint)

    @Query("DELETE FROM trail_points WHERE trailId = :trailId")
    suspend fun deletePointsForTrail(trailId: Long)
}
