package ca.gmode.triprecorder.auto

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import ca.gmode.triprecorder.settings.AutoRecordingConfig
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HomeWifiMonitorInstrumentedTest {
    @Test
    fun backgroundWifiPendingIntentCanBeRegisteredAndRemoved() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val monitor = HomeWifiMonitor(context)
        try {
            assertTrue(
                monitor.refresh(
                    AutoRecordingConfig(
                        enabled = true,
                        homeWifiSsid = "GMODE Test Home",
                        wifiDepartureDelayMinutes = 1,
                    ),
                ),
            )
        } finally {
            monitor.unregister()
            WifiDepartureWorker.cancel(context)
        }
    }
}
