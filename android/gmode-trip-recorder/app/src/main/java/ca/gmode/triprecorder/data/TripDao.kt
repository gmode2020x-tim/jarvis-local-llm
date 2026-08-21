package ca.gmode.triprecorder.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert

@Dao
interface TripDao {
    @Upsert
    suspend fun upsertTrip(trip: TripEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertPoint(point: PointEntity): Long

    @Transaction
    suspend fun insertPointAndTrip(point: PointEntity, trip: TripEntity) {
        if (insertPoint(point) != -1L) {
            upsertTrip(trip)
        }
    }

    @Query("SELECT * FROM trips WHERE id = :tripId LIMIT 1")
    suspend fun getTrip(tripId: String): TripEntity?

    @Query("SELECT * FROM trips WHERE status = 'active' ORDER BY updatedAtEpochMs DESC LIMIT 1")
    suspend fun getActiveTrip(): TripEntity?

    @Query("SELECT * FROM trips WHERE needsSync = 1 ORDER BY updatedAtEpochMs ASC LIMIT 1")
    suspend fun getOldestDirtyTrip(): TripEntity?

    @Query("SELECT * FROM points WHERE tripId = :tripId AND synced = 0 ORDER BY sequence LIMIT :limit")
    suspend fun getPendingPoints(tripId: String, limit: Int): List<PointEntity>

    @Query("SELECT COUNT(*) FROM points WHERE tripId = :tripId AND synced = 0")
    suspend fun getPendingPointCount(tripId: String): Int

    @Query("SELECT COUNT(*) FROM points WHERE synced = 0")
    suspend fun getTotalPendingPointCount(): Int

    @Query("SELECT COALESCE(MAX(sequence), -1) FROM points WHERE tripId = :tripId")
    suspend fun getLastSequence(tripId: String): Long

    @Query("UPDATE points SET synced = 1 WHERE id IN (:pointIds)")
    suspend fun markPointsSynced(pointIds: List<String>)

    @Query(
        """UPDATE trips SET needsSync = 0
           WHERE id = :tripId AND updatedAtEpochMs = :expectedUpdatedAtEpochMs
           AND NOT EXISTS (SELECT 1 FROM points WHERE tripId = :tripId AND synced = 0)""",
    )
    suspend fun markTripSyncedIfUnchanged(tripId: String, expectedUpdatedAtEpochMs: Long)
}
