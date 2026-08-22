package ca.gmode.triprecorder.tracking

import android.content.Context
import ca.gmode.triprecorder.data.PointEntity
import kotlin.math.abs

data class LiveTelemetry(
    val tripId: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val speedKph: Double? = null,
    val altitudeMeters: Double? = null,
    val elevationGainMeters: Double = 0.0,
    val bearingDegrees: Double? = null,
    val accuracyMeters: Double? = null,
    val pressureHpa: Double? = null,
    val accelerationPeakMs2: Double? = null,
    val batteryPercent: Double? = null,
    val satelliteCount: Int? = null,
    val pitchDegrees: Double? = null,
    val rollDegrees: Double? = null,
    val updatedAtEpochMs: Long = 0,
)

class LiveTelemetryStore(context: Context) {
    private val preferences = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    @Synchronized
    fun update(point: PointEntity, orientation: OrientationSnapshot) {
        val old = read()
        val sameTrip = old.tripId == point.tripId
        val altitudeGain = if (sameTrip && old.altitudeMeters != null && point.altitudeMeters != null) {
            (point.altitudeMeters - old.altitudeMeters).takeIf { it > ALTITUDE_NOISE_METERS } ?: 0.0
        } else {
            0.0
        }
        preferences.edit().apply {
            putString(KEY_TRIP_ID, point.tripId)
            putDouble(KEY_LATITUDE, point.latitude)
            putDouble(KEY_LONGITUDE, point.longitude)
            putNullableDouble(KEY_SPEED, point.speedMps?.times(3.6))
            putNullableDouble(KEY_ALTITUDE, point.altitudeMeters)
            putDouble(KEY_ELEVATION_GAIN, (if (sameTrip) old.elevationGainMeters else 0.0) + altitudeGain)
            putNullableDouble(KEY_BEARING, point.bearingDegrees)
            putNullableDouble(KEY_ACCURACY, point.accuracyMeters)
            putNullableDouble(KEY_PRESSURE, point.pressureHpa)
            putNullableDouble(KEY_ACCELERATION, point.accelerationPeakMs2)
            putNullableDouble(KEY_BATTERY, point.batteryPercent)
            if (point.satelliteCount == null) remove(KEY_SATELLITES) else putInt(KEY_SATELLITES, point.satelliteCount)
            putNullableDouble(KEY_PITCH, orientation.pitchDegrees)
            putNullableDouble(KEY_ROLL, orientation.rollDegrees)
            putLong(KEY_UPDATED, System.currentTimeMillis())
        }.apply()
    }

    fun read(): LiveTelemetry = LiveTelemetry(
        tripId = preferences.getString(KEY_TRIP_ID, null),
        latitude = preferences.getDouble(KEY_LATITUDE),
        longitude = preferences.getDouble(KEY_LONGITUDE),
        speedKph = preferences.getDouble(KEY_SPEED),
        altitudeMeters = preferences.getDouble(KEY_ALTITUDE),
        elevationGainMeters = preferences.getDouble(KEY_ELEVATION_GAIN) ?: 0.0,
        bearingDegrees = preferences.getDouble(KEY_BEARING),
        accuracyMeters = preferences.getDouble(KEY_ACCURACY),
        pressureHpa = preferences.getDouble(KEY_PRESSURE),
        accelerationPeakMs2 = preferences.getDouble(KEY_ACCELERATION),
        batteryPercent = preferences.getDouble(KEY_BATTERY),
        satelliteCount = preferences.getInt(KEY_SATELLITES, -1).takeIf { it >= 0 },
        pitchDegrees = preferences.getDouble(KEY_PITCH),
        rollDegrees = preferences.getDouble(KEY_ROLL),
        updatedAtEpochMs = preferences.getLong(KEY_UPDATED, 0),
    )

    private fun android.content.SharedPreferences.Editor.putNullableDouble(key: String, value: Double?) {
        if (value == null || !value.isFinite()) remove(key) else putDouble(key, value)
    }

    private fun android.content.SharedPreferences.Editor.putDouble(key: String, value: Double) =
        putString(key, value.toString())

    private fun android.content.SharedPreferences.getDouble(key: String): Double? =
        getString(key, null)?.toDoubleOrNull()?.takeIf { it.isFinite() && abs(it) < Double.MAX_VALUE }

    companion object {
        private const val ALTITUDE_NOISE_METERS = 1.5
        private const val PREFS = "live_telemetry"
        private const val KEY_TRIP_ID = "trip_id"
        private const val KEY_LATITUDE = "latitude"
        private const val KEY_LONGITUDE = "longitude"
        private const val KEY_SPEED = "speed_kph"
        private const val KEY_ALTITUDE = "altitude_meters"
        private const val KEY_ELEVATION_GAIN = "elevation_gain_meters"
        private const val KEY_BEARING = "bearing_degrees"
        private const val KEY_ACCURACY = "accuracy_meters"
        private const val KEY_PRESSURE = "pressure_hpa"
        private const val KEY_ACCELERATION = "acceleration_peak"
        private const val KEY_BATTERY = "battery_percent"
        private const val KEY_SATELLITES = "satellites"
        private const val KEY_PITCH = "pitch_degrees"
        private const val KEY_ROLL = "roll_degrees"
        private const val KEY_UPDATED = "updated_at"
    }
}
