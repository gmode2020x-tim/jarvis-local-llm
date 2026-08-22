package ca.gmode.triprecorder.auto

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeWifiLogicTest {
    @Test
    fun ssidNormalizationHandlesAndroidQuotedAndUnknownValues() {
        assertEquals("GMODE Home", HomeWifiReader.normalizeSsid("\"GMODE Home\""))
        assertNull(HomeWifiReader.normalizeSsid("<unknown ssid>"))
        assertNull(HomeWifiReader.normalizeSsid("  "))
    }

    @Test
    fun gpsConfirmationAccountsForHomeRadiusAndFixAccuracy() {
        assertFalse(WifiDepartureWorker.isSafelyOutsideHome(260f, 20f, 250f))
        assertTrue(WifiDepartureWorker.isSafelyOutsideHome(281f, 20f, 250f))
        assertFalse(WifiDepartureWorker.isSafelyOutsideHome(250f, 0f, 250f))
    }
}
