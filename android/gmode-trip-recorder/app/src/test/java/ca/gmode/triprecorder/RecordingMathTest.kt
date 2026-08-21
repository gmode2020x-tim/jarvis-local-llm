package ca.gmode.triprecorder

import ca.gmode.triprecorder.data.distanceMeters
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RecordingMathTest {
    @Test
    fun zeroDistanceIsZero() {
        assertEquals(0.0, distanceMeters(43.0, -80.0, 43.0, -80.0), 0.001)
    }

    @Test
    fun knownShortDistanceIsPlausible() {
        val distance = distanceMeters(43.0, -80.0, 43.001, -80.0)
        assertTrue(distance in 110.0..112.5)
    }
}
