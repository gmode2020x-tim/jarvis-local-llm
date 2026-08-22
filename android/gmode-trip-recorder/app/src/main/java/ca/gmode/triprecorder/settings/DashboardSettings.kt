package ca.gmode.triprecorder.settings

import android.content.Context

data class VehicleProfile(val id: String, val label: String)

data class GaugeDefinition(val id: String, val label: String)

data class DashboardConfig(
    val vehicleId: String = DashboardSettings.DEFAULT_VEHICLE_ID,
    val gaugeIds: List<String> = DashboardSettings.defaultGauges(DashboardSettings.DEFAULT_VEHICLE_ID),
    val pitchOffsetDegrees: Double = 0.0,
    val rollOffsetDegrees: Double = 0.0,
) {
    fun normalized(): DashboardConfig {
        val vehicle = DashboardSettings.VEHICLES.firstOrNull { it.id == vehicleId }?.id
            ?: DashboardSettings.DEFAULT_VEHICLE_ID
        val known = DashboardSettings.GAUGES.mapTo(mutableSetOf()) { it.id }
        val ordered = gaugeIds.filter { it in known }.distinct().take(DashboardSettings.MAX_GAUGES)
        return copy(
            vehicleId = vehicle,
            gaugeIds = ordered.ifEmpty { DashboardSettings.defaultGauges(vehicle) },
            pitchOffsetDegrees = pitchOffsetDegrees.takeIf { it.isFinite() }?.coerceIn(-180.0, 180.0) ?: 0.0,
            rollOffsetDegrees = rollOffsetDegrees.takeIf { it.isFinite() }?.coerceIn(-180.0, 180.0) ?: 0.0,
        )
    }
}

class DashboardSettings(context: Context) {
    private val preferences = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun read(): DashboardConfig = DashboardConfig(
        vehicleId = preferences.getString(KEY_VEHICLE, DEFAULT_VEHICLE_ID) ?: DEFAULT_VEHICLE_ID,
        gaugeIds = preferences.getString(KEY_GAUGES, null)?.split(',')?.filter { it.isNotBlank() }
            ?: defaultGauges(DEFAULT_VEHICLE_ID),
        pitchOffsetDegrees = preferences.getString(KEY_PITCH_OFFSET, null)?.toDoubleOrNull() ?: 0.0,
        rollOffsetDegrees = preferences.getString(KEY_ROLL_OFFSET, null)?.toDoubleOrNull() ?: 0.0,
    ).normalized()

    fun save(config: DashboardConfig) {
        val normalized = config.normalized()
        preferences.edit()
            .putString(KEY_VEHICLE, normalized.vehicleId)
            .putString(KEY_GAUGES, normalized.gaugeIds.joinToString(","))
            .putString(KEY_PITCH_OFFSET, normalized.pitchOffsetDegrees.toString())
            .putString(KEY_ROLL_OFFSET, normalized.rollOffsetDegrees.toString())
            .apply()
    }

    companion object {
        const val DEFAULT_VEHICLE_ID = "atv_utv"
        const val MAX_GAUGES = 2

        val VEHICLES = listOf(
            VehicleProfile("car", "Car"),
            VehicleProfile("truck", "Truck / 4x4"),
            VehicleProfile("atv_utv", "ATV / UTV"),
            VehicleProfile("motorcycle", "Motorcycle"),
            VehicleProfile("snowmobile", "Snowmobile"),
            VehicleProfile("boat", "Boat"),
        )

        val GAUGES = listOf(
            GaugeDefinition("speed", "Speed"),
            GaugeDefinition("trip_time", "Trip time"),
            GaugeDefinition("distance", "Distance"),
            GaugeDefinition("altitude", "Altitude"),
            GaugeDefinition("elevation_gain", "Elevation gain"),
            GaugeDefinition("compass", "Compass / heading"),
            GaugeDefinition("pitch", "Pitch"),
            GaugeDefinition("roll", "Roll"),
            GaugeDefinition("g_force", "G-force"),
            GaugeDefinition("battery", "Phone battery"),
            GaugeDefinition("gps_satellites", "GPS satellites"),
            GaugeDefinition("gps_accuracy", "GPS accuracy"),
            GaugeDefinition("coordinates", "Coordinates"),
            GaugeDefinition("pressure", "Barometer"),
        )

        fun defaultGauges(vehicleId: String): List<String> = when (vehicleId) {
            "boat" -> listOf("speed", "compass")
            "snowmobile" -> listOf("speed", "compass")
            "motorcycle" -> listOf("speed", "roll")
            "car", "truck" -> listOf("speed", "compass")
            else -> listOf("pitch", "roll")
        }

        private const val PREFS = "dashboard_settings"
        private const val KEY_VEHICLE = "vehicle_id"
        private const val KEY_GAUGES = "gauge_ids"
        private const val KEY_PITCH_OFFSET = "pitch_offset_degrees"
        private const val KEY_ROLL_OFFSET = "roll_offset_degrees"
    }
}
