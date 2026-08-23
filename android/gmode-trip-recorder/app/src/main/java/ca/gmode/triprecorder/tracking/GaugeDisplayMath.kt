package ca.gmode.triprecorder.tracking

object GaugeDisplayMath {
    fun mirroredRollDegrees(sensorDegrees: Double, zeroOffsetDegrees: Double): Double =
        normalizeAngle(zeroOffsetDegrees - sensorDegrees)

    private fun normalizeAngle(value: Double): Double =
        ((value + 180.0) % 360.0 + 360.0) % 360.0 - 180.0
}
