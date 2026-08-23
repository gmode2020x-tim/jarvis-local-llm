package ca.gmode.triprecorder.tracking

import ca.gmode.triprecorder.GaugeFaceStyle
import ca.gmode.triprecorder.GaugeScaleCatalog
import ca.gmode.triprecorder.GaugeZoneRole
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GaugeScaleCatalogTest {
    @Test
    fun speedRangeMatchesTripType() {
        assertEquals(120.0, GaugeScaleCatalog.forGauge("speed", 0.0, "off_road").maximum, 0.0)
        assertEquals(200.0, GaugeScaleCatalog.forGauge("speed", 0.0, "street").maximum, 0.0)
        assertEquals(160.0, GaugeScaleCatalog.forGauge("speed", 0.0, "snow").maximum, 0.0)
        assertEquals(100.0, GaugeScaleCatalog.forGauge("speed", 0.0, "water").maximum, 0.0)
    }

    @Test
    fun tripScalesGrowWithoutClippingTheReading() {
        assertEquals(10.0, GaugeScaleCatalog.forGauge("distance", 8.0, "street").maximum, 0.0)
        assertEquals(25.0, GaugeScaleCatalog.forGauge("distance", 12.0, "street").maximum, 0.0)
        assertEquals(1_000.0, GaugeScaleCatalog.forGauge("elevation_gain", 900.0, "off_road").maximum, 0.0)
        assertEquals(4_000.0, GaugeScaleCatalog.forGauge("elevation_gain", 2_300.0, "off_road").maximum, 0.0)
    }

    @Test
    fun attitudeScalesAreCenteredAndUseActualDegrees() {
        listOf("pitch", "roll", "attitude").forEach { id ->
            val spec = GaugeScaleCatalog.forGauge(id, 0.0, "off_road")
            assertEquals(-45.0, spec.minimum, 0.0)
            assertEquals(45.0, spec.maximum, 0.0)
            assertEquals(0.5, spec.progress(0.0)!!, 0.0)
            assertEquals(listOf("-45", "-30", "-15", "0", "15", "30", "45"), spec.majorTicks.map { it.label })
        }
        assertEquals(GaugeFaceStyle.ATTITUDE_COMBINED, GaugeScaleCatalog.forGauge("attitude", 0.0, "off_road").faceStyle)
    }

    @Test
    fun courseIsAFullCompassRose() {
        val spec = GaugeScaleCatalog.forGauge("compass", 315.0, "street")
        assertEquals(GaugeFaceStyle.COURSE, spec.faceStyle)
        assertEquals(360f, spec.sweepAngle)
        assertEquals(listOf("N", "NE", "E", "SE", "S", "SW", "W", "NW"), spec.majorTicks.map { it.label })
    }

    @Test
    fun gpsAccuracyUsesNonLinearUsefulResolution() {
        assertEquals(0.0, GaugeScaleCatalog.accuracyProgress(100.0)!!, 0.0)
        assertEquals(0.2, GaugeScaleCatalog.accuracyProgress(50.0)!!, 0.0)
        assertEquals(0.6, GaugeScaleCatalog.accuracyProgress(10.0)!!, 0.0)
        assertEquals(1.0, GaugeScaleCatalog.accuracyProgress(0.0)!!, 0.0)
        assertTrue(GaugeScaleCatalog.accuracyProgress(7.0)!! > GaugeScaleCatalog.accuracyProgress(20.0)!!)
    }

    @Test
    fun qualityGaugesHaveLogicalBands() {
        val battery = GaugeScaleCatalog.forGauge("battery", 50.0, "street")
        assertEquals(GaugeZoneRole.DANGER, battery.zones.first().role)
        assertEquals(15.0, battery.zones.first().endValue, 0.0)
        val satellites = GaugeScaleCatalog.forGauge("gps_satellites", 10.0, "street")
        assertEquals(GaugeZoneRole.GOOD, satellites.zones.last().role)
        assertEquals(8.0, satellites.zones.last().startValue, 0.0)
    }

    @Test
    fun pressureUsesPhoneBarometerRange() {
        val spec = GaugeScaleCatalog.forGauge("pressure", 1_012.0, "street")
        assertEquals(850.0, spec.minimum, 0.0)
        assertEquals(1_050.0, spec.maximum, 0.0)
        assertEquals(10.0, spec.minorStep!!, 0.0)
    }
}
