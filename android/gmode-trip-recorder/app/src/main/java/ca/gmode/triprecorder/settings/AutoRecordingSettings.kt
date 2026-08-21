package ca.gmode.triprecorder.settings

import android.content.Context

data class AutoRecordingConfig(
    val enabled: Boolean = false,
    val homeLatitude: Double? = null,
    val homeLongitude: Double? = null,
    val homeRadiusMeters: Int = DEFAULT_HOME_RADIUS_METERS,
    val returnDwellMinutes: Int = DEFAULT_RETURN_DWELL_MINUTES,
    val locationIntervalSeconds: Int = DEFAULT_LOCATION_INTERVAL_SECONDS,
    val minimumDistanceMeters: Int = DEFAULT_MINIMUM_DISTANCE_METERS,
    val tripType: String = "street",
) {
    fun normalized(): AutoRecordingConfig = copy(
        homeLatitude = homeLatitude?.takeIf { it in -90.0..90.0 },
        homeLongitude = homeLongitude?.takeIf { it in -180.0..180.0 },
        homeRadiusMeters = homeRadiusMeters.coerceIn(100, 5_000),
        returnDwellMinutes = returnDwellMinutes.coerceIn(1, 120),
        locationIntervalSeconds = locationIntervalSeconds.coerceIn(2, 300),
        minimumDistanceMeters = minimumDistanceMeters.coerceIn(1, 500),
        tripType = tripType.takeIf { it in TRIP_TYPES } ?: "street",
    )

    val hasHomeLocation: Boolean
        get() = homeLatitude != null && homeLongitude != null

    companion object {
        const val DEFAULT_HOME_RADIUS_METERS = 250
        const val DEFAULT_RETURN_DWELL_MINUTES = 5
        const val DEFAULT_LOCATION_INTERVAL_SECONDS = 5
        const val DEFAULT_MINIMUM_DISTANCE_METERS = 5
        val TRIP_TYPES = setOf("street", "off_road", "snow", "water")
    }
}

class AutoRecordingSettings(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)

    fun read(): AutoRecordingConfig = AutoRecordingConfig(
        enabled = preferences.getBoolean(KEY_ENABLED, false),
        homeLatitude = preferences.getNullableDouble(KEY_HOME_LATITUDE),
        homeLongitude = preferences.getNullableDouble(KEY_HOME_LONGITUDE),
        homeRadiusMeters = preferences.getInt(KEY_HOME_RADIUS, AutoRecordingConfig.DEFAULT_HOME_RADIUS_METERS),
        returnDwellMinutes = preferences.getInt(KEY_RETURN_DWELL, AutoRecordingConfig.DEFAULT_RETURN_DWELL_MINUTES),
        locationIntervalSeconds = preferences.getInt(KEY_LOCATION_INTERVAL, AutoRecordingConfig.DEFAULT_LOCATION_INTERVAL_SECONDS),
        minimumDistanceMeters = preferences.getInt(KEY_MINIMUM_DISTANCE, AutoRecordingConfig.DEFAULT_MINIMUM_DISTANCE_METERS),
        tripType = preferences.getString(KEY_TRIP_TYPE, "street") ?: "street",
    ).normalized()

    fun save(config: AutoRecordingConfig) {
        val normalized = config.normalized()
        preferences.edit()
            .putBoolean(KEY_ENABLED, normalized.enabled)
            .putNullableDouble(KEY_HOME_LATITUDE, normalized.homeLatitude)
            .putNullableDouble(KEY_HOME_LONGITUDE, normalized.homeLongitude)
            .putInt(KEY_HOME_RADIUS, normalized.homeRadiusMeters)
            .putInt(KEY_RETURN_DWELL, normalized.returnDwellMinutes)
            .putInt(KEY_LOCATION_INTERVAL, normalized.locationIntervalSeconds)
            .putInt(KEY_MINIMUM_DISTANCE, normalized.minimumDistanceMeters)
            .putString(KEY_TRIP_TYPE, normalized.tripType)
            .apply()
    }

    private fun android.content.SharedPreferences.getNullableDouble(key: String): Double? =
        if (contains(key)) java.lang.Double.longBitsToDouble(getLong(key, 0L)) else null

    private fun android.content.SharedPreferences.Editor.putNullableDouble(
        key: String,
        value: Double?,
    ): android.content.SharedPreferences.Editor = if (value == null) remove(key) else putLong(
        key,
        java.lang.Double.doubleToRawLongBits(value),
    )

    private companion object {
        const val PREFERENCES = "auto_recording_settings"
        const val KEY_ENABLED = "enabled"
        const val KEY_HOME_LATITUDE = "home_latitude"
        const val KEY_HOME_LONGITUDE = "home_longitude"
        const val KEY_HOME_RADIUS = "home_radius_meters"
        const val KEY_RETURN_DWELL = "return_dwell_minutes"
        const val KEY_LOCATION_INTERVAL = "location_interval_seconds"
        const val KEY_MINIMUM_DISTANCE = "minimum_distance_meters"
        const val KEY_TRIP_TYPE = "trip_type"
    }
}

class AutoRecordingStateStore(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)

    var activeAutoTripId: String?
        get() = preferences.getString(KEY_ACTIVE_TRIP_ID, null)
        set(value) {
            preferences.edit().apply {
                if (value == null) remove(KEY_ACTIVE_TRIP_ID) else putString(KEY_ACTIVE_TRIP_ID, value)
            }.apply()
        }

    fun status(): String = preferences.getString(KEY_STATUS, "Automatic recording is off")
        ?: "Automatic recording is off"

    fun updateStatus(message: String) {
        preferences.edit().putString(KEY_STATUS, message.take(240)).apply()
    }

    private companion object {
        const val PREFERENCES = "auto_recording_state"
        const val KEY_ACTIVE_TRIP_ID = "active_auto_trip_id"
        const val KEY_STATUS = "status"
    }
}
