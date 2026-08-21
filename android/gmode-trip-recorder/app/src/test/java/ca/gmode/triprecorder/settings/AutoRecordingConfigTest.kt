package ca.gmode.triprecorder.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AutoRecordingConfigTest {
    @Test
    fun valuesAreClampedToSafeSupportedRanges() {
        val normalized = AutoRecordingConfig(
            homeLatitude = 120.0,
            homeLongitude = -220.0,
            homeRadiusMeters = 1,
            returnDwellMinutes = 999,
            locationIntervalSeconds = 1,
            minimumDistanceMeters = 999,
            tripType = "unknown",
        ).normalized()

        assertNull(normalized.homeLatitude)
        assertNull(normalized.homeLongitude)
        assertEquals(100, normalized.homeRadiusMeters)
        assertEquals(120, normalized.returnDwellMinutes)
        assertEquals(2, normalized.locationIntervalSeconds)
        assertEquals(500, normalized.minimumDistanceMeters)
        assertEquals("street", normalized.tripType)
    }
}
