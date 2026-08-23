package ca.gmode.triprecorder.tracking

data class CourseSnapshot(val degrees: Double, val source: String)

object GaugeDisplayMath {
    fun mirroredRollDegrees(sensorDegrees: Double, zeroOffsetDegrees: Double): Double =
        normalizeAngle(zeroOffsetDegrees - sensorDegrees)

    fun hybridCourse(
        gpsCourseDegrees: Double?,
        speedKph: Double?,
        magneticHeadingDegrees: Double?,
    ): CourseSnapshot? {
        val movingGps = gpsCourseDegrees?.takeIf { it.isFinite() && (speedKph ?: 0.0) >= GPS_COURSE_MIN_KPH }
        if (movingGps != null) return CourseSnapshot(normalizeBearing(movingGps), "GPS")
        magneticHeadingDegrees?.takeIf { it.isFinite() }?.let {
            return CourseSnapshot(normalizeBearing(it), "MAG")
        }
        return gpsCourseDegrees?.takeIf { it.isFinite() }?.let { CourseSnapshot(normalizeBearing(it), "GPS") }
    }

    fun signedBearingDelta(from: Double, to: Double): Double =
        ((normalizeBearing(to) - normalizeBearing(from) + 540.0) % 360.0) - 180.0

    fun smoothBearing(current: Double, target: Double, amount: Double): Double =
        normalizeBearing(current + signedBearingDelta(current, target) * amount.coerceIn(0.0, 1.0))

    private fun normalizeAngle(value: Double): Double =
        ((value + 180.0) % 360.0 + 360.0) % 360.0 - 180.0

    private fun normalizeBearing(value: Double): Double = ((value % 360.0) + 360.0) % 360.0

    private const val GPS_COURSE_MIN_KPH = 5.0
}
