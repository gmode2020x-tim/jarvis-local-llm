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
    val attitudeCautionDegrees: Double = DashboardSettings.DEFAULT_ATTITUDE_CAUTION_DEGREES,
    val attitudeLimitDegrees: Double = DashboardSettings.DEFAULT_ATTITUDE_LIMIT_DEGREES,
) {
    fun normalized(): DashboardConfig {
        val scene = offRoadSceneId.takeIf { id -> DashboardSettings.OFF_ROAD_SCENES.any { it.id == id } }
            ?: DashboardSettings.DEFAULT_OFF_ROAD_SCENE_ID
        val known = DashboardSettings.GAUGES.mapTo(mutableSetOf()) { it.id }
        val ordered = gaugeIds
            .map { if (it == "pitch" || it == "roll") "attitude" else it }
            .filter { it in known }
            .distinct()
        val caution = attitudeCautionDegrees.takeIf { it.isFinite() }?.coerceIn(5.0, 40.0)
            ?: DashboardSettings.DEFAULT_ATTITUDE_CAUTION_DEGREES
        val limit = attitudeLimitDegrees.takeIf { it.isFinite() }?.coerceIn(caution + 5.0, 60.0)
            ?: maxOf(DashboardSettings.DEFAULT_ATTITUDE_LIMIT_DEGREES, caution + 5.0)
        return copy(
            vehicleId = if (scene == "sand") "sand_rail" else DashboardSettings.DEFAULT_VEHICLE_ID,
            streetVehicleId = DashboardSettings.DEFAULT_STREET_VEHICLE_ID,
            snowVehicleId = DashboardSettings.DEFAULT_SNOW_VEHICLE_ID,
            waterVehicleId = DashboardSettings.DEFAULT_WATER_VEHICLE_ID,
            offRoadSceneId = scene,
            gaugeIds = ordered.ifEmpty { DashboardSettings.defaultGauges(DashboardSettings.DEFAULT_VEHICLE_ID) },
            pitchOffsetDegrees = pitchOffsetDegrees.takeIf { it.isFinite() }?.coerceIn(-180.0, 180.0) ?: 0.0,
            rollOffsetDegrees = rollOffsetDegrees.takeIf { it.isFinite() }?.coerceIn(-180.0, 180.0) ?: 0.0,
            vehicleViewModeId = vehicleViewModeId.takeIf { id -> DashboardSettings.VIEW_MODES.any { it.id == id } }
                ?: DashboardSettings.DEFAULT_VIEW_MODE_ID,
            attitudeCautionDegrees = caution,
            attitudeLimitDegrees = limit,
        )
    }

    fun vehicleIdForTripType(tripType: String): String = when (DashboardSettings.normalizeTripType(tripType)) {
        "street" -> DashboardSettings.DEFAULT_STREET_VEHICLE_ID
        "snow" -> DashboardSettings.DEFAULT_SNOW_VEHICLE_ID
        "water" -> DashboardSettings.DEFAULT_WATER_VEHICLE_ID
        else -> if (offRoadSceneId == "sand") "sand_rail" else DashboardSettings.DEFAULT_VEHICLE_ID
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
            attitudeCautionDegrees = preferences.getString(KEY_ATTITUDE_CAUTION, null)?.toDoubleOrNull()
                ?: DEFAULT_ATTITUDE_CAUTION_DEGREES,
            attitudeLimitDegrees = preferences.getString(KEY_ATTITUDE_LIMIT, null)?.toDoubleOrNull()
                ?: DEFAULT_ATTITUDE_LIMIT_DEGREES,
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
            .putString(KEY_ATTITUDE_CAUTION, normalized.attitudeCautionDegrees.toString())
            .putString(KEY_ATTITUDE_LIMIT, normalized.attitudeLimitDegrees.toString())
            .remove(LEGACY_KEY_ROLL_VIEW)
            .apply()
    }

    companion object {
        const val DEFAULT_VEHICLE_ID = "sxs"
        const val DEFAULT_STREET_VEHICLE_ID = "truck"
        const val DEFAULT_SNOW_VEHICLE_ID = "snowmobile"
        const val DEFAULT_WATER_VEHICLE_ID = "mini_jet_boat"
        const val DEFAULT_OFF_ROAD_SCENE_ID = "dirt"
        const val DEFAULT_VIEW_MODE_ID = "auto"
        const val DEFAULT_ATTITUDE_CAUTION_DEGREES = 15.0
        const val DEFAULT_ATTITUDE_LIMIT_DEGREES = 30.0
        const val AUTOMATIC_ROLL_VIEW_ID = "rear"
        val VEHICLE_ID_ALIASES = mapOf(
            "atv_utv" to "sxs",
            "motorcycle" to "dirt_bike",
        )

        val TRIP_TYPES = listOf("street", "off_road", "snow", "water")

        val VEHICLES = listOf(
            VehicleProfile("sxs", "SxS", setOf("off_road")),
            VehicleProfile("sand_rail", "Sand rail", setOf("off_road")),
            VehicleProfile("truck", "Truck", setOf("street")),
            VehicleProfile("mini_jet_boat", "Mini jet boat", setOf("water")),
            VehicleProfile("snowmobile", "Snowmobile", setOf("snow")),
        )

        val OFF_ROAD_SCENES = listOf(
            GaugeSceneOption("dirt", "Dirt / rocky terrain"),
            GaugeSceneOption("sand", "Sand dunes"),
        )

        val VIEW_MODES = listOf(
            VehicleViewOption("auto", "Chase — touch orbit, then return"),
            VehicleViewOption("free", "Free orbit — keep selected view"),
            VehicleViewOption("rear", "Locked high rear"),
        )

        val FIXED_VIEW_IDS = setOf("side", "front", "rear")

        val GAUGES = listOf(
            GaugeDefinition("speed", "Speed"),
            GaugeDefinition("trip_time", "Trip time"),
            GaugeDefinition("distance", "Distance"),
            GaugeDefinition("altitude", "GPS altitude"),
            GaugeDefinition("elevation_gain", "Elevation gain"),
            GaugeDefinition("compass", "GPS course"),
            GaugeDefinition("attitude", "3D pitch + roll"),
            GaugeDefinition("g_force", "Shock peak"),
            GaugeDefinition("battery", "Phone battery"),
            GaugeDefinition("gps_satellites", "GPS satellites"),
            GaugeDefinition("gps_accuracy", "GPS accuracy"),
            GaugeDefinition("coordinates", "Coordinates"),
            GaugeDefinition("pressure", "Station pressure"),
        )

        fun defaultGauges(vehicleId: String): List<String> = listOf("attitude", "speed", "compass")

        fun defaultTripType(vehicleId: String): String = when (
            VEHICLE_ID_ALIASES[vehicleId] ?: vehicleId
        ) {
            "truck" -> "street"
            "snowmobile" -> "snow"
            "mini_jet_boat" -> "water"
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
            return "rear"
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
        private const val KEY_ATTITUDE_CAUTION = "attitude_caution_degrees"
        private const val KEY_ATTITUDE_LIMIT = "attitude_limit_degrees"
        private const val LEGACY_KEY_ROLL_VIEW = "roll_vehicle_view"
    }
}
