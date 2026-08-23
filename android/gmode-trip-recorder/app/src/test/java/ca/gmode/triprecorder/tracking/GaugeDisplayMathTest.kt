package ca.gmode.triprecorder.tracking

import org.junit.Assert.assertEquals
import org.junit.Test

class GaugeDisplayMathTest {
    @Test
    fun rollDisplayMirrorsTheSensorDirectionAroundTheSavedZero() {
        assertEquals(-12.0, GaugeDisplayMath.mirroredRollDegrees(22.0, 10.0), 0.001)
        assertEquals(12.0, GaugeDisplayMath.mirroredRollDegrees(-2.0, 10.0), 0.001)
        assertEquals(0.0, GaugeDisplayMath.mirroredRollDegrees(10.0, 10.0), 0.001)
    }

    @Test
    fun mirroredRollStaysWithinTheSignedAngleRange() {
        assertEquals(179.0, GaugeDisplayMath.mirroredRollDegrees(181.0, 0.0), 0.001)
        assertEquals(-179.0, GaugeDisplayMath.mirroredRollDegrees(-181.0, 0.0), 0.001)
    }
}
