package ca.gmode.triprecorder.data

import android.location.Location
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Instant
import java.util.UUID

data class SensorSnapshot(
    val pressureHpa: Double?,
    val accelerationRmsMs2: Double?,
    val accelerationPeakMs2: Double?,
    val gyroscopePeakRadS: Double?,
)

data class PhoneSnapshot(
    val batteryPercent: Double?,
    val isCharging: Boolean,
    val networkType: String,
    val satelliteCount: Int?,
)

class RecordingRepository(private val dao: TripDao) {
    private val writeMutex = Mutex()

    suspend fun startTrip(title: String, tripType: String): TripEntity = writeMutex.withLock {
        dao.getActiveTrip()?.let { return@withLock it }
        val now = Instant.now()
        val trip = TripEntity(
            id = UUID.randomUUID().toString(),
            title = title.trim().ifBlank { "Trip ${now.toString().take(16).replace('T', ' ')}" },
            tripType = tripType,
            status = "active",
            startAt = now.toString(),
            updatedAtEpochMs = System.currentTimeMillis(),
        )
        dao.upsertTrip(trip)
        trip
    }

    suspend fun stopTrip(): TripEntity? = writeMutex.withLock {
        val trip = dao.getActiveTrip() ?: return@withLock null
        val completed = trip.copy(
            status = "complete",
            endAt = Instant.now().toString(),
            needsSync = true,
            updatedAtEpochMs = System.currentTimeMillis(),
        )
        dao.upsertTrip(completed)
        completed
    }

    suspend fun recordLocation(
        tripId: String,
        location: Location,
        sensors: SensorSnapshot,
        phone: PhoneSnapshot,
    ): PointEntity? = writeMutex.withLock {
        val trip = dao.getTrip(tripId) ?: return@withLock null
        if (trip.status != "active") return@withLock null
        val sequence = dao.getLastSequence(tripId) + 1
        val point = PointEntity(
            id = "$tripId:$sequence",
            tripId = tripId,
            sequence = sequence,
            recordedAt = Instant.ofEpochMilli(location.time).toString(),
            latitude = location.latitude,
            longitude = location.longitude,
            accuracyMeters = location.accuracy.takeIf { location.hasAccuracy() }?.toDouble(),
            altitudeMeters = location.altitude.takeIf { location.hasAltitude() },
            verticalAccuracyMeters = location.verticalAccuracyMeters
                .takeIf { location.hasVerticalAccuracy() }
                ?.toDouble(),
            speedMps = location.speed.takeIf { location.hasSpeed() }?.toDouble(),
            bearingDegrees = location.bearing.takeIf { location.hasBearing() }?.toDouble(),
            pressureHpa = sensors.pressureHpa,
            accelerationRmsMs2 = sensors.accelerationRmsMs2,
            accelerationPeakMs2 = sensors.accelerationPeakMs2,
            gyroscopePeakRadS = sensors.gyroscopePeakRadS,
            batteryPercent = phone.batteryPercent,
            isCharging = phone.isCharging,
            networkType = phone.networkType,
            satelliteCount = phone.satelliteCount,
        )
        val addedDistance = if (trip.lastLatitude != null && trip.lastLongitude != null) {
            distanceMeters(trip.lastLatitude, trip.lastLongitude, location.latitude, location.longitude)
        } else {
            0.0
        }
        val updated = trip.copy(
            needsSync = true,
            updatedAtEpochMs = System.currentTimeMillis(),
            distanceMeters = trip.distanceMeters + addedDistance,
            pointCount = trip.pointCount + 1,
            lastLatitude = location.latitude,
            lastLongitude = location.longitude,
            lastSpeedMps = point.speedMps,
            lastAccuracyMeters = point.accuracyMeters,
            lastAltitudeMeters = point.altitudeMeters,
        )
        dao.insertPointAndTrip(point, updated)
        point
    }

    suspend fun activeTrip(): TripEntity? = dao.getActiveTrip()

    suspend fun pendingPointCount(): Int = dao.getTotalPendingPointCount()

    suspend fun recentTrips(limit: Int = 100): List<TripEntity> = dao.getRecentTrips(limit.coerceIn(1, 250))

    suspend fun trip(tripId: String): TripEntity? = dao.getTrip(tripId)

    suspend fun tripPoints(tripId: String): List<PointEntity> = dao.getPointsForTrip(tripId)
}

fun distanceMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
    val radiusMeters = 6_371_000.0
    val latitudeDelta = Math.toRadians(lat2 - lat1)
    val longitudeDelta = Math.toRadians(lon2 - lon1)
    val a = kotlin.math.sin(latitudeDelta / 2) * kotlin.math.sin(latitudeDelta / 2) +
        kotlin.math.cos(Math.toRadians(lat1)) * kotlin.math.cos(Math.toRadians(lat2)) *
        kotlin.math.sin(longitudeDelta / 2) * kotlin.math.sin(longitudeDelta / 2)
    return 2 * radiusMeters * kotlin.math.atan2(kotlin.math.sqrt(a), kotlin.math.sqrt(1 - a))
}
