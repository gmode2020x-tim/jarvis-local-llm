package ca.gmode.triprecorder.tracking

import kotlin.math.atan2
import kotlin.math.hypot

object VehicleOrientationMath {
    fun fromWorldUp(upX: Double, upY: Double, upZ: Double): OrientationSnapshot = OrientationSnapshot(
        pitchDegrees = Math.toDegrees(atan2(-upZ, upY)),
        rollDegrees = Math.toDegrees(atan2(-upX, upY)),
    )

    fun headingDegrees(east: Double, north: Double): Double? {
        if (!east.isFinite() || !north.isFinite() || hypot(east, north) < 0.08) return null
        return (Math.toDegrees(atan2(east, north)) + 360.0) % 360.0
    }
}
