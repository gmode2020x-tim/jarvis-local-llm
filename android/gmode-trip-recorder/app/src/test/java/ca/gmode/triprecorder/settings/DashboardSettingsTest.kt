package ca.gmode.triprecorder.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DashboardSettingsTest {
    @Test
    fun normalizedPreservesValidGaugeOrderAndRemovesDuplicates() {
        val config = DashboardConfig(
            vehicleId = "boat",
            gaugeIds = listOf("compass", "speed", "compass", "unknown", "battery"),
        ).normalized()

        assertEquals("boat", config.vehicleId)
        assertEquals(listOf("compass", "speed", "battery"), config.gaugeIds)
    }

    @Test
    fun normalizedFallsBackToAtvDefaultsForInvalidEmptyConfig() {
        val config = DashboardConfig(vehicleId = "spaceship", gaugeIds = emptyList()).normalized()

        assertEquals(DashboardSettings.DEFAULT_VEHICLE_ID, config.vehicleId)
        assertEquals(DashboardSettings.defaultGauges(DashboardSettings.DEFAULT_VEHICLE_ID), config.gaugeIds)
        assertTrue("pitch" in config.gaugeIds)
        assertTrue("roll" in config.gaugeIds)
    }

    @Test
    fun normalizedCapsDashboardAtTenGauges() {
        val config = DashboardConfig(
            gaugeIds = DashboardSettings.GAUGES.map { it.id },
        ).normalized()

        assertEquals(DashboardSettings.MAX_GAUGES, config.gaugeIds.size)
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
}
