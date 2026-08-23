package ca.gmode.triprecorder.tracking

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import kotlin.math.cos
import kotlin.math.sin

class VehicleOrientationMathTest {
    @Test
    fun uprightLandscapePhoneIsLevel() {
        val orientation = VehicleOrientationMath.fromWorldUp(0.0, 1.0, 0.0)

        assertEquals(0.0, orientation.pitchDegrees!!, 0.001)
        assertEquals(0.0, orientation.rollDegrees!!, 0.001)
    }

    @Test
    fun separatesVehiclePitchFromRoll() {
        val angle = Math.toRadians(12.0)
        val noseUp = VehicleOrientationMath.fromWorldUp(0.0, cos(angle), -sin(angle))
        val rightSideDown = VehicleOrientationMath.fromWorldUp(-sin(angle), cos(angle), 0.0)

        assertEquals(12.0, noseUp.pitchDegrees!!, 0.001)
        assertEquals(0.0, noseUp.rollDegrees!!, 0.001)
        assertEquals(0.0, rightSideDown.pitchDegrees!!, 0.001)
        assertEquals(12.0, rightSideDown.rollDegrees!!, 0.001)
    }

    @Test
    fun vehicleForwardVectorMapsToCompassHeading() {
        assertEquals(0.0, VehicleOrientationMath.headingDegrees(0.0, 1.0)!!, 0.001)
        assertEquals(90.0, VehicleOrientationMath.headingDegrees(1.0, 0.0)!!, 0.001)
        assertEquals(180.0, VehicleOrientationMath.headingDegrees(0.0, -1.0)!!, 0.001)
        assertEquals(270.0, VehicleOrientationMath.headingDegrees(-1.0, 0.0)!!, 0.001)
        assertNull(VehicleOrientationMath.headingDegrees(0.0, 0.0))
    }
}
