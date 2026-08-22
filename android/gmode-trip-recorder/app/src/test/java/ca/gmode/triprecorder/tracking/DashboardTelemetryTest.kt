package ca.gmode.triprecorder.tracking

import ca.gmode.triprecorder.data.SensorSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DashboardTelemetryTest {
    @Test
    fun foregroundPhoneSensorsRemainLiveBeforeATripStarts() {
        val merged = DashboardTelemetry.merge(
            stored = LiveTelemetry(tripId = "old", pitchDegrees = 99.0, pressureHpa = 800.0),
            activeTripId = null,
            sensors = SensorSnapshot(
                pressureHpa = 1007.5,
                accelerationRmsMs2 = 0.2,
                accelerationPeakMs2 = 1.4,
                gyroscopePeakRadS = 0.1,
            ),
            orientation = OrientationSnapshot(pitchDegrees = 6.5, rollDegrees = -3.0),
            batteryPercent = 74,
        )

        assertEquals(6.5, merged.pitchDegrees!!, 0.001)
        assertEquals(-3.0, merged.rollDegrees!!, 0.001)
        assertEquals(1007.5, merged.pressureHpa!!, 0.001)
        assertEquals(1.4, merged.accelerationPeakMs2!!, 0.001)
        assertEquals(74.0, merged.batteryPercent!!, 0.001)
    }

    @Test
    fun oldTripSensorValuesAreNotShownAsCurrentWhenPhoneHasNoSample() {
        val merged = DashboardTelemetry.merge(
            stored = LiveTelemetry(
                tripId = "old",
                pitchDegrees = 12.0,
                rollDegrees = -8.0,
                pressureHpa = 998.0,
                accelerationPeakMs2 = 4.0,
            ),
            activeTripId = null,
            sensors = null,
            orientation = OrientationSnapshot(null, null),
            batteryPercent = null,
        )

        assertNull(merged.pitchDegrees)
        assertNull(merged.rollDegrees)
        assertNull(merged.pressureHpa)
        assertNull(merged.accelerationPeakMs2)
    }

    @Test
    fun activeTripValuesBridgeShortGapsBetweenForegroundSamples() {
        val stored = LiveTelemetry(
            tripId = "active",
            pitchDegrees = 2.0,
            rollDegrees = 3.0,
            pressureHpa = 1001.0,
        )
        val merged = DashboardTelemetry.merge(
            stored = stored,
            activeTripId = "active",
            sensors = null,
            orientation = OrientationSnapshot(null, null),
            batteryPercent = null,
        )

        assertEquals(2.0, merged.pitchDegrees!!, 0.001)
        assertEquals(3.0, merged.rollDegrees!!, 0.001)
        assertEquals(1001.0, merged.pressureHpa!!, 0.001)
    }
}
