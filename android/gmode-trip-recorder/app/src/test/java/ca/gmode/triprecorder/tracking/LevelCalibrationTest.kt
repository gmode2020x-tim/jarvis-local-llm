package ca.gmode.triprecorder.tracking

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LevelCalibrationTest {
    @Test
    fun acceptsStationarySensorWindow() {
        assertTrue(LevelCalibration.isStationary(accelerationPeakMs2 = 0.12, gyroscopePeakRadS = 0.03))
        assertTrue(LevelCalibration.isStationary(accelerationPeakMs2 = null, gyroscopePeakRadS = null))
    }

    @Test
    fun rejectsAccelerationOrRotation() {
        assertFalse(LevelCalibration.isStationary(accelerationPeakMs2 = 0.8, gyroscopePeakRadS = 0.03))
        assertFalse(LevelCalibration.isStationary(accelerationPeakMs2 = 0.12, gyroscopePeakRadS = 0.2))
    }
}
