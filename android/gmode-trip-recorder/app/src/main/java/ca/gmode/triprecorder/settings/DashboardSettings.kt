package ca.gmode.triprecorder.settings

import android.content.Context

data class VehicleProfile(
    val id: String,
    val label: String,
    val tripTypes: Set<String>,
    val funny: Boolean = false,
)

data class GaugeDefinition(val id: String, val label: String)

data class VehicleViewOption(val id: String, val label: String)

data class GaugeSceneOption(val id: String, val label: String)

data class DashboardConfig(
    val vehicleId: String = DashboardSettings.DEFAULT_VEHICLE_ID,
    val streetVehicleId: String = DashboardSettings.DEFAULT_STREET_VEHICLE_ID,
    val snowVehicleId: String = DashboardSettings.DEFAULT_SNOW_VEHICLE_ID,
    val waterVehicleId: String = DashboardSettings.DEFAULT_WATER_VEHICLE_ID,
    val offRoadSceneId: String = DashboardSettings.DEFAULT_OFF_ROAD_SCENE_ID,
    val gaugeIds: List<String> = DashboardSettings.defaultGauges(DashboardSettings.DEFAULT_VEHICLE_ID),
    val pitchOffsetDegrees: Double = 0.0,
    val rollOffsetDegrees: Double = 0.0,
    val vehicleViewModeId: String = DashboardSettings.DEFAULT_VIEW_MODE_ID,
) {
    fun normalized(): DashboardConfig {
        val offRoadVehicle = DashboardSettings.normalizeVehicleId(vehicleId, "off_road")
        val streetVehicle = DashboardSettings.normalizeVehicleId(streetVehicleId, "street")
        val snowVehicle = DashboardSettings.normalizeVehicleId(snowVehicleId, "snow")
        val waterVehicle = DashboardSettings.normalizeVehicleId(waterVehicleId, "water")
        val known = DashboardSettings.GAUGES.mapTo(mutableSetOf()) { it.id }
        val ordered = gaugeIds.filter { it in known }.distinct().take(DashboardSettings.MAX_GAUGES)
        return copy(
            vehicleId = offRoadVehicle,
            streetVehicleId = streetVehicle,
            snowVehicleId = snowVehicle,
            waterVehicleId = waterVehicle,
            offRoadSceneId = offRoadSceneId.takeIf { id -> DashboardSettings.OFF_ROAD_SCENES.any { it.id == id } }
                ?: DashboardSettings.DEFAULT_OFF_ROAD_SCENE_ID,
            gaugeIds = ordered.ifEmpty { DashboardSettings.defaultGauges(offRoadVehicle) },
            pitchOffsetDegrees = pitchOffsetDegrees.takeIf { it.isFinite() }?.coerceIn(-180.0, 180.0) ?: 0.0,
            rollOffsetDegrees = rollOffsetDegrees.takeIf { it.isFinite() }?.coerceIn(-180.0, 180.0) ?: 0.0,
            vehicleViewModeId = vehicleViewModeId.takeIf { id -> DashboardSettings.VIEW_MODES.any { it.id == id } }
                ?: DashboardSettings.DEFAULT_VIEW_MODE_ID,
        )
    }

    fun vehicleIdForTripType(tripType: String): String = when (DashboardSettings.normalizeTripType(tripType)) {
        "street" -> streetVehicleId
        "snow" -> snowVehicleId
        "water" -> waterVehicleId
        else -> vehicleId
    }
}

class DashboardSettings(context: Context) {
    private val preferences = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun read(): DashboardConfig {
        val legacyVehicle = preferences.getString(KEY_VEHICLE, DEFAULT_VEHICLE_ID) ?: DEFAULT_VEHICLE_ID
        fun selectedVehicle(key: String, tripType: String): String = preferences.getString(key, null)
            ?: legacyVehicle.takeIf { vehicleSupportsTripType(it, tripType) }
            ?: defaultVehicleId(tripType)

        return DashboardConfig(
            vehicleId = selectedVehicle(KEY_OFF_ROAD_VEHICLE, "off_road"),
            streetVehicleId = selectedVehicle(KEY_STREET_VEHICLE, "street"),
            snowVehicleId = selectedVehicle(KEY_SNOW_VEHICLE, "snow"),
            waterVehicleId = selectedVehicle(KEY_WATER_VEHICLE, "water"),
            offRoadSceneId = preferences.getString(KEY_OFF_ROAD_SCENE, DEFAULT_OFF_ROAD_SCENE_ID)
                ?: DEFAULT_OFF_ROAD_SCENE_ID,
            gaugeIds = preferences.getString(KEY_GAUGES, null)?.split(',')?.filter { it.isNotBlank() }
                ?: defaultGauges(DEFAULT_VEHICLE_ID),
            pitchOffsetDegrees = preferences.getString(KEY_PITCH_OFFSET, null)?.toDoubleOrNull() ?: 0.0,
            rollOffsetDegrees = preferences.getString(KEY_ROLL_OFFSET, null)?.toDoubleOrNull() ?: 0.0,
            vehicleViewModeId = preferences.getString(KEY_VEHICLE_VIEW_MODE, DEFAULT_VIEW_MODE_ID) ?: DEFAULT_VIEW_MODE_ID,
        ).normalized()
    }

    fun save(config: DashboardConfig) {
        val normalized = config.normalized()
        preferences.edit()
            .putString(KEY_VEHICLE, normalized.vehicleId)
            .putString(KEY_OFF_ROAD_VEHICLE, normalized.vehicleId)
            .putString(KEY_STREET_VEHICLE, normalized.streetVehicleId)
            .putString(KEY_SNOW_VEHICLE, normalized.snowVehicleId)
            .putString(KEY_WATER_VEHICLE, normalized.waterVehicleId)
            .putString(KEY_OFF_ROAD_SCENE, normalized.offRoadSceneId)
            .putString(KEY_GAUGES, normalized.gaugeIds.joinToString(","))
            .putString(KEY_PITCH_OFFSET, normalized.pitchOffsetDegrees.toString())
            .putString(KEY_ROLL_OFFSET, normalized.rollOffsetDegrees.toString())
            .putString(KEY_VEHICLE_VIEW_MODE, normalized.vehicleViewModeId)
            .remove(LEGACY_KEY_ROLL_VIEW)
            .apply()
    }

    companion object {
        const val DEFAULT_VEHICLE_ID = "sxs"
        const val DEFAULT_STREET_VEHICLE_ID = "car"
        const val DEFAULT_SNOW_VEHICLE_ID = "snowmobile"
        const val DEFAULT_WATER_VEHICLE_ID = "boat"
        const val DEFAULT_OFF_ROAD_SCENE_ID = "dirt"
        const val DEFAULT_VIEW_MODE_ID = "auto"
        const val AUTOMATIC_ROLL_VIEW_ID = "rear"
        const val MAX_GAUGES = 2

        val VEHICLE_ID_ALIASES = mapOf(
            "atv_utv" to "sxs",
            "motorcycle" to "dirt_bike",
        )

        val TRIP_TYPES = listOf("street", "off_road", "snow", "water")

        val VEHICLES = listOf(
            VehicleProfile("dirt_bike", "Dirt bike", setOf("off_road")),
            VehicleProfile("sxs", "SxS / side-by-side", setOf("off_road")),
            VehicleProfile("quad", "Quad ATV", setOf("off_road")),
            VehicleProfile("three_wheeler", "Three-wheeler", setOf("off_road")),
            VehicleProfile("sand_rail", "Sand rail", setOf("off_road")),
            VehicleProfile("trophy_truck", "Trophy truck", setOf("off_road")),
            VehicleProfile("unicycle", "Extreme unicycle — funny", setOf("off_road"), funny = true),
            VehicleProfile("truck", "Truck / 4x4", setOf("street", "off_road")),
            VehicleProfile("car", "Car", setOf("street")),
            VehicleProfile("street_motorcycle", "Street motorcycle", setOf("street")),
            VehicleProfile("clown_car", "Clown car — funny", setOf("street"), funny = true),
            VehicleProfile("snowmobile", "Snowmobile", setOf("snow")),
            VehicleProfile("snow_bike", "Snow bike", setOf("snow")),
            VehicleProfile("snowcat", "Snowcat", setOf("snow")),
            VehicleProfile("tracked_utv", "Tracked SxS", setOf("snow")),
            VehicleProfile("boat", "Boat", setOf("water")),
            VehicleProfile("seadoo", "Sea-Doo / personal watercraft", setOf("water")),
            VehicleProfile("hovercraft", "Hovercraft", setOf("water")),
            VehicleProfile("kayak", "Kayak", setOf("water")),
            VehicleProfile("mini_jet_boat", "Mini jet boat", setOf("water")),
        )

        val OFF_ROAD_SCENES = listOf(
            GaugeSceneOption("dirt", "Dirt / rocky terrain"),
            GaugeSceneOption("sand", "Sand dunes"),
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
            "boat", "seadoo", "hovercraft", "kayak", "mini_jet_boat" -> listOf("speed", "compass")
            "snowmobile", "snowcat", "tracked_utv" -> listOf("speed", "compass")
            "dirt_bike", "street_motorcycle", "snow_bike" -> listOf("speed", "roll")
            "car", "truck", "clown_car", "sand_rail", "trophy_truck" -> listOf("speed", "compass")
            else -> listOf("pitch", "roll")
        }

        fun defaultTripType(vehicleId: String): String = when (
            VEHICLE_ID_ALIASES[vehicleId] ?: vehicleId
        ) {
            "car", "street_motorcycle", "clown_car" -> "street"
            "snowmobile", "snow_bike", "snowcat", "tracked_utv" -> "snow"
            "boat", "seadoo", "hovercraft", "kayak", "mini_jet_boat" -> "water"
            else -> "off_road"
        }

        fun normalizeTripType(value: String): String = value.trim().lowercase().replace('-', '_').replace(' ', '_')

        fun vehiclesForTripType(tripType: String): List<VehicleProfile> {
            val normalized = normalizeTripType(tripType)
            return VEHICLES.filter { normalized in it.tripTypes }
        }

        fun vehicleSupportsTripType(vehicleId: String, tripType: String): Boolean {
            val migrated = VEHICLE_ID_ALIASES[vehicleId] ?: vehicleId
            return VEHICLES.firstOrNull { it.id == migrated }?.let { normalizeTripType(tripType) in it.tripTypes } == true
        }

        fun defaultVehicleId(tripType: String): String = when (normalizeTripType(tripType)) {
            "street" -> DEFAULT_STREET_VEHICLE_ID
            "snow" -> DEFAULT_SNOW_VEHICLE_ID
            "water" -> DEFAULT_WATER_VEHICLE_ID
            else -> DEFAULT_VEHICLE_ID
        }

        fun normalizeVehicleId(vehicleId: String, tripType: String): String {
            val migrated = VEHICLE_ID_ALIASES[vehicleId] ?: vehicleId
            return migrated.takeIf { vehicleSupportsTripType(it, tripType) } ?: defaultVehicleId(tripType)
        }

        fun vehicle(vehicleId: String): VehicleProfile = VEHICLES.firstOrNull { it.id == vehicleId }
            ?: VEHICLES.first { it.id == DEFAULT_VEHICLE_ID }

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
        private const val KEY_OFF_ROAD_VEHICLE = "off_road_vehicle_id"
        private const val KEY_STREET_VEHICLE = "street_vehicle_id"
        private const val KEY_SNOW_VEHICLE = "snow_vehicle_id"
        private const val KEY_WATER_VEHICLE = "water_vehicle_id"
        private const val KEY_OFF_ROAD_SCENE = "off_road_scene_id"
        private const val KEY_GAUGES = "gauge_ids"
        private const val KEY_PITCH_OFFSET = "pitch_offset_degrees"
        private const val KEY_ROLL_OFFSET = "roll_offset_degrees"
        private const val KEY_VEHICLE_VIEW_MODE = "vehicle_view_mode"
        private const val LEGACY_KEY_ROLL_VIEW = "roll_vehicle_view"
    }
}
