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

    @Test
    fun courseUsesGpsWhileMovingAndMagneticWhileStopped() {
        assertEquals(CourseSnapshot(318.0, "GPS"), GaugeDisplayMath.hybridCourse(318.0, 12.0, 302.0))
        assertEquals(CourseSnapshot(302.0, "MAG"), GaugeDisplayMath.hybridCourse(318.0, 1.0, 302.0))
        assertEquals(CourseSnapshot(318.0, "GPS"), GaugeDisplayMath.hybridCourse(318.0, 1.0, null))
        assertEquals(CourseSnapshot(359.0, "MAG"), GaugeDisplayMath.hybridCourse(null, null, -1.0))
        assertEquals(null, GaugeDisplayMath.hybridCourse(null, null, null))
    }
}
