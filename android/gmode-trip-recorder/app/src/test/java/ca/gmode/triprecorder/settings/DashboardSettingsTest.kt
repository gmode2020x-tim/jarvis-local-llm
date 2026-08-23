package ca.gmode.triprecorder.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DashboardSettingsTest {
    @Test
    fun normalizedPreservesValidGaugeOrderAndRemovesDuplicates() {
        val config = DashboardConfig(
            waterVehicleId = "boat",
            gaugeIds = listOf("compass", "speed", "compass", "unknown", "battery"),
        ).normalized()

        assertEquals("mini_jet_boat", config.waterVehicleId)
        assertEquals(listOf("compass", "speed", "battery"), config.gaugeIds)
    }

    @Test
    fun normalizedFallsBackToAtvDefaultsForInvalidEmptyConfig() {
        val config = DashboardConfig(vehicleId = "spaceship", gaugeIds = emptyList()).normalized()

        assertEquals(DashboardSettings.DEFAULT_VEHICLE_ID, config.vehicleId)
        assertEquals(DashboardSettings.defaultGauges(DashboardSettings.DEFAULT_VEHICLE_ID), config.gaugeIds)
        assertTrue("attitude" in config.gaugeIds)
    }

    @Test
    fun normalizedPreservesEverySupportedGaugeWithoutALimit() {
        val config = DashboardConfig(
            gaugeIds = DashboardSettings.GAUGES.map { it.id },
        ).normalized()

        assertEquals(DashboardSettings.GAUGES.map { it.id }, config.gaugeIds)
    }

    @Test
    fun normalizedKeepsFiniteMountCalibrationInSensorRange() {
        val config = DashboardConfig(
            pitchOffsetDegrees = 250.0,
            rollOffsetDegrees = Double.NaN,
        ).normalized()

        assertEquals(180.0, config.pitchOffsetDegrees, 0.0)
        assertEquals(0.0, config.rollOffsetDegrees, 0.0)
    }

    @Test
    fun legacyVehicleIdsMigrateToTheSceneVehicle() {
        assertEquals("sxs", DashboardConfig(vehicleId = "atv_utv").normalized().vehicleId)
        assertEquals("sxs", DashboardConfig(vehicleId = "motorcycle").normalized().vehicleId)
    }

    @Test
    fun catalogIsLimitedToTheFiveSceneVehicles() {
        assertEquals(
            setOf("sxs", "sand_rail", "truck", "mini_jet_boat", "snowmobile"),
            DashboardSettings.VEHICLES.map { it.id }.toSet(),
        )
    }

    @Test
    fun sandSceneAndNewVehiclesNormalizeInTheirCategories() {
        val config = DashboardConfig(
            vehicleId = "sand_rail",
            waterVehicleId = "mini_jet_boat",
            offRoadSceneId = "sand",
        ).normalized()

        assertEquals("sand_rail", config.vehicleId)
        assertEquals("mini_jet_boat", config.waterVehicleId)
        assertEquals("sand", config.offRoadSceneId)
        assertEquals("dirt", DashboardConfig(offRoadSceneId = "moon").normalized().offRoadSceneId)
        assertEquals("sand_rail", config.vehicleIdForTripType("off road"))
    }

    @Test
    fun categorySelectionsNormalizeAndSwitchByTripType() {
        val config = DashboardConfig(
            vehicleId = "quad",
            streetVehicleId = "clown_car",
            snowVehicleId = "tracked_utv",
            waterVehicleId = "hovercraft",
        ).normalized()

        assertEquals("sxs", config.vehicleIdForTripType("off road"))
        assertEquals("truck", config.vehicleIdForTripType("street"))
        assertEquals("snowmobile", config.vehicleIdForTripType("snow"))
        assertEquals("mini_jet_boat", config.vehicleIdForTripType("water"))
    }

    @Test
    fun vehicleCannotBeSavedIntoTheWrongCategory() {
        val config = DashboardConfig(
            vehicleId = "boat",
            streetVehicleId = "snowcat",
            snowVehicleId = "car",
            waterVehicleId = "quad",
        ).normalized()

        assertEquals(DashboardSettings.DEFAULT_VEHICLE_ID, config.vehicleId)
        assertEquals(DashboardSettings.DEFAULT_STREET_VEHICLE_ID, config.streetVehicleId)
        assertEquals(DashboardSettings.DEFAULT_SNOW_VEHICLE_ID, config.snowVehicleId)
        assertEquals(DashboardSettings.DEFAULT_WATER_VEHICLE_ID, config.waterVehicleId)
    }

    @Test
    fun allLegacyFlatViewsResolveToTheRearChaseStartingPoint() {
        assertEquals("rear", DashboardSettings.resolveVehicleView("auto", "Pitch", 14.0, 2.0))
        assertEquals("rear", DashboardSettings.resolveVehicleView("auto", "Roll", 2.0, 14.0))
        assertEquals("rear", DashboardSettings.resolveVehicleView("auto", "Speed", 2.0, 14.0))
        assertEquals("rear", DashboardSettings.resolveVehicleView("auto", "Speed", 14.0, 2.0))
    }

    @Test
    fun fixedVehicleViewOverridesSensors() {
        assertEquals("rear", DashboardSettings.resolveVehicleView("front", "Pitch", 30.0, 0.0))
    }

    @Test
    fun legacyPitchAndRollGaugesMergeIntoOneAttitudeGauge() {
        val config = DashboardConfig(gaugeIds = listOf("speed", "pitch", "roll", "attitude")).normalized()
        assertEquals(listOf("speed", "attitude"), config.gaugeIds)
    }

    @Test
    fun warningLimitsAreOrderedAndBounded() {
        val config = DashboardConfig(attitudeCautionDegrees = 44.0, attitudeLimitDegrees = 20.0).normalized()
        assertEquals(40.0, config.attitudeCautionDegrees, 0.0)
        assertEquals(45.0, config.attitudeLimitDegrees, 0.0)
    }
}
