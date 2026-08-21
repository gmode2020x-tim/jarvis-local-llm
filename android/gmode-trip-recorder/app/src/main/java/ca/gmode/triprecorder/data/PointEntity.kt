package ca.gmode.triprecorder.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "points",
    foreignKeys = [
        ForeignKey(
            entity = TripEntity::class,
            parentColumns = ["id"],
            childColumns = ["tripId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("tripId"), Index(value = ["tripId", "sequence"], unique = true), Index("synced")],
)
data class PointEntity(
    @PrimaryKey val id: String,
    val tripId: String,
    val sequence: Long,
    val recordedAt: String,
    val latitude: Double,
    val longitude: Double,
    val accuracyMeters: Double?,
    val altitudeMeters: Double?,
    val verticalAccuracyMeters: Double?,
    val speedMps: Double?,
    val bearingDegrees: Double?,
    val pressureHpa: Double?,
    val accelerationRmsMs2: Double?,
    val accelerationPeakMs2: Double?,
    val gyroscopePeakRadS: Double?,
    val batteryPercent: Double?,
    val isCharging: Boolean,
    val networkType: String,
    val satelliteCount: Int?,
    val synced: Boolean = false,
)
