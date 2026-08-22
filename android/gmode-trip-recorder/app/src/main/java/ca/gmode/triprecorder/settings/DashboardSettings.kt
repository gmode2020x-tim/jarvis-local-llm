package ca.gmode.triprecorder.settings

import android.content.Context

data class VehicleProfile(val id: String, val label: String)

data class GaugeDefinition(val id: String, val label: String)

data class VehicleViewOption(val id: String, val label: String)

data class DashboardConfig(
    val vehicleId: String = DashboardSettings.DEFAULT_VEHICLE_ID,
    val gaugeIds: List<String> = DashboardSettings.defaultGauges(DashboardSettings.DEFAULT_VEHICLE_ID),
    val pitchOffsetDegrees: Double = 0.0,
    val rollOffsetDegrees: Double = 0.0,
    val vehicleViewModeId: String = DashboardSettings.DEFAULT_VIEW_MODE_ID,
) {
    fun normalized(): DashboardConfig {
        val migratedVehicleId = DashboardSettings.VEHICLE_ID_ALIASES[vehicleId] ?: vehicleId
        val vehicle = DashboardSettings.VEHICLES.firstOrNull { it.id == migratedVehicleId }?.id
            ?: DashboardSettings.DEFAULT_VEHICLE_ID
        val known = DashboardSettings.GAUGES.mapTo(mutableSetOf()) { it.id }
        val ordered = gaugeIds.filter { it in known }.distinct().take(DashboardSettings.MAX_GAUGES)
        return copy(
            vehicleId = vehicle,
            gaugeIds = ordered.ifEmpty { DashboardSettings.defaultGauges(vehicle) },
            pitchOffsetDegrees = pitchOffsetDegrees.takeIf { it.isFinite() }?.coerceIn(-180.0, 180.0) ?: 0.0,
            rollOffsetDegrees = rollOffsetDegrees.takeIf { it.isFinite() }?.coerceIn(-180.0, 180.0) ?: 0.0,
            vehicleViewModeId = vehicleViewModeId.takeIf { id -> DashboardSettings.VIEW_MODES.any { it.id == id } }
                ?: DashboardSettings.DEFAULT_VIEW_MODE_ID,
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
        vehicleViewModeId = preferences.getString(KEY_VEHICLE_VIEW_MODE, DEFAULT_VIEW_MODE_ID) ?: DEFAULT_VIEW_MODE_ID,
    ).normalized()

    fun save(config: DashboardConfig) {
        val normalized = config.normalized()
        preferences.edit()
            .putString(KEY_VEHICLE, normalized.vehicleId)
            .putString(KEY_GAUGES, normalized.gaugeIds.joinToString(","))
            .putString(KEY_PITCH_OFFSET, normalized.pitchOffsetDegrees.toString())
            .putString(KEY_ROLL_OFFSET, normalized.rollOffsetDegrees.toString())
            .putString(KEY_VEHICLE_VIEW_MODE, normalized.vehicleViewModeId)
            .remove(LEGACY_KEY_ROLL_VIEW)
            .apply()
    }

    companion object {
        const val DEFAULT_VEHICLE_ID = "sxs"
        const val DEFAULT_VIEW_MODE_ID = "auto"
        const val AUTOMATIC_ROLL_VIEW_ID = "rear"
        const val MAX_GAUGES = 2

        val VEHICLE_ID_ALIASES = mapOf(
            "atv_utv" to "sxs",
            "motorcycle" to "dirt_bike",
        )

        val VEHICLES = listOf(
            VehicleProfile("dirt_bike", "Dirt bike"),
            VehicleProfile("sxs", "SxS / side-by-side"),
            VehicleProfile("quad", "Quad ATV"),
            VehicleProfile("snowmobile", "Snowmobile"),
            VehicleProfile("three_wheeler", "Three-wheeler"),
            VehicleProfile("truck", "Truck / 4x4"),
            VehicleProfile("car", "Car"),
            VehicleProfile("boat", "Boat"),
            VehicleProfile("seadoo", "Sea-Doo / personal watercraft"),
        )

        val VIEW_MODES = listOf(
            VehicleViewOption("auto", "Automatic — phone sensors"),
            VehicleViewOption("side", "Always side"),
            VehicleViewOption("front", "Always front"),
            VehicleViewOption("rear", "Always rear"),
        )

        val FIXED_VIEW_IDS = setOf("side", "front", "rear")

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
            "boat", "seadoo" -> listOf("speed", "compass")
            "snowmobile" -> listOf("speed", "compass")
            "dirt_bike" -> listOf("speed", "roll")
            "car", "truck" -> listOf("speed", "compass")
            else -> listOf("pitch", "roll")
        }

        fun defaultTripType(vehicleId: String): String = when (
            VEHICLE_ID_ALIASES[vehicleId] ?: vehicleId
        ) {
            "car" -> "street"
            "snowmobile" -> "snow"
            "boat", "seadoo" -> "water"
            else -> "off_road"
        }

        fun resolveVehicleView(
            modeId: String,
            gaugeTitle: String,
            pitchDegrees: Double?,
            rollDegrees: Double?,
        ): String {
            if (modeId in FIXED_VIEW_IDS) return modeId
            if (gaugeTitle.equals("pitch", ignoreCase = true)) return "side"
            if (gaugeTitle.equals("roll", ignoreCase = true)) return AUTOMATIC_ROLL_VIEW_ID
            val pitch = kotlin.math.abs(pitchDegrees ?: 0.0)
            val roll = kotlin.math.abs(rollDegrees ?: 0.0)
            return if (roll > pitch + 2.0) AUTOMATIC_ROLL_VIEW_ID else "side"
        }

        private const val PREFS = "dashboard_settings"
        private const val KEY_VEHICLE = "vehicle_id"
        private const val KEY_GAUGES = "gauge_ids"
        private const val KEY_PITCH_OFFSET = "pitch_offset_degrees"
        private const val KEY_ROLL_OFFSET = "roll_offset_degrees"
        private const val KEY_VEHICLE_VIEW_MODE = "vehicle_view_mode"
        private const val LEGACY_KEY_ROLL_VIEW = "roll_vehicle_view"
    }
}
