package ca.gmode.triprecorder.tracking

import kotlin.math.atan2

object VehicleOrientationMath {
    fun fromWorldUp(upX: Double, upY: Double, upZ: Double): OrientationSnapshot = OrientationSnapshot(
        pitchDegrees = Math.toDegrees(atan2(-upZ, upY)),
        rollDegrees = Math.toDegrees(atan2(-upX, upY)),
    )
}
