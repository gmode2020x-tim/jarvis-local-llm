package ca.gmode.triprecorder

import android.content.pm.ActivityInfo
import android.content.res.Configuration
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import ca.gmode.triprecorder.settings.DashboardConfig
import ca.gmode.triprecorder.settings.DashboardSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MainActivityInstrumentedTest {
    @Test
    fun launchesFullScreenLandscapeCockpit() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val root = activity.findViewById<android.view.ViewGroup>(android.R.id.content)
                assertTrue(root.getChildAt(0) is LandscapeCockpitView)
                assertEquals(Configuration.ORIENTATION_LANDSCAPE, activity.resources.configuration.orientation)
                assertEquals(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE, activity.requestedOrientation)
            }
        }
    }

    @Test
    fun configuredGaugeOrderBecomesLeftAndRightMainInstruments() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val settings = DashboardSettings(context)
        val original = settings.read()
        settings.save(DashboardConfig(vehicleId = "truck", gaugeIds = listOf("speed", "compass")))
        try {
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                scenario.onActivity { activity ->
                    val root = activity.findViewById<android.view.ViewGroup>(android.R.id.content)
                    val cockpit = root.getChildAt(0) as LandscapeCockpitView
                    assertEquals(listOf("Speed", "Compass"), cockpit.activeGaugeTitles())
                }
            }
        } finally {
            settings.save(original)
        }
    }
}
