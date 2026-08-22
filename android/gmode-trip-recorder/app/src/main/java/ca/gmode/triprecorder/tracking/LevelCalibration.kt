package ca.gmode.triprecorder.tracking

object LevelCalibration {
    const val SETTLE_MS = 600L
    const val SAMPLE_MS = 2_000L
    const val MAX_ACCELERATION_MS2 = 0.5
    const val MAX_GYROSCOPE_RAD_S = 0.1

    fun isStationary(accelerationPeakMs2: Double?, gyroscopePeakRadS: Double?): Boolean =
        (accelerationPeakMs2 ?: 0.0) <= MAX_ACCELERATION_MS2 &&
            (gyroscopePeakRadS ?: 0.0) <= MAX_GYROSCOPE_RAD_S
}
