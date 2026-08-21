package ca.gmode.triprecorder.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "trips")
data class TripEntity(
    @PrimaryKey val id: String,
    val title: String,
    val tripType: String,
    val status: String,
    val startAt: String,
    val endAt: String? = null,
    val needsSync: Boolean = true,
    val updatedAtEpochMs: Long,
    val distanceMeters: Double = 0.0,
    val pointCount: Int = 0,
    val lastLatitude: Double? = null,
    val lastLongitude: Double? = null,
    val lastSpeedMps: Double? = null,
    val lastAccuracyMeters: Double? = null,
    val lastAltitudeMeters: Double? = null,
)
