package ca.gmode.triprecorder

import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.graphics.BitmapFactory
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import ca.gmode.triprecorder.settings.DashboardConfig
import ca.gmode.triprecorder.settings.DashboardSettings
import ca.gmode.triprecorder.settings.SideButtonConfig
import ca.gmode.triprecorder.settings.SideButtonSettings
import ca.gmode.triprecorder.settings.SideButtonSlot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MainActivityInstrumentedTest {
    @Test
    fun referenceArtworkPiecesRetainExactDesignDimensions() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val expected = listOf(
            R.drawable.reference_dashboard_top to (1280 to 98),
            R.drawable.reference_dashboard_middle_left to (428 to 368),
            R.drawable.reference_dashboard_middle_center to (424 to 368),
            R.drawable.reference_dashboard_middle_right to (428 to 368),
            R.drawable.reference_dashboard_footer to (1280 to 126),
        )

        expected.forEach { (resourceId, dimensions) ->
            val bitmap = BitmapFactory.decodeResource(context.resources, resourceId)
            assertEquals(dimensions.first, bitmap.width)
            assertEquals(dimensions.second, bitmap.height)
        }
    }

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

    @Test
    fun cornerIndicatorsReflectLatestLiveState() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val root = activity.findViewById<android.view.ViewGroup>(android.R.id.content)
                val cockpit = root.getChildAt(0) as LandscapeCockpitView
                cockpit.setState(
                    CockpitState(
                        wifiConnected = true,
                        networkConnected = true,
                        bluetoothEnabled = false,
                        gpsReady = true,
                        pendingCount = 7,
                        batteryPercent = 84,
                        batteryCharging = true,
                        recording = true,
                        tripDurationLabel = "1:23",
                    ),
                )

                assertEquals(
                    CornerIndicatorSnapshot(
                        wifiConnected = true,
                        networkConnected = true,
                        bluetoothEnabled = false,
                        gpsReady = true,
                        pendingCount = 7,
                        batteryPercent = 84,
                        batteryCharging = true,
                        recording = true,
                        tripDurationLabel = "1:23",
                    ),
                    cockpit.cornerIndicatorSnapshot(),
                )
            }
        }
    }

    @Test
    fun configuredLabelsTargetsAndIconsReachAllSixDashboardButtons() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val settings = SideButtonSettings(context)
        val original = settings.read()
        val custom = SideButtonSlot.entries.mapIndexed { index, slot ->
            SideButtonConfig(
                slot = slot,
                label = "BUTTON ${index + 1}",
                target = if (index == 0) {
                    "app:ca.gmode.triprecorder/.MainActivity"
                } else {
                    SideButtonSettings.BUILT_IN_TARGETS[index].id
                },
                iconId = SideButtonSettings.ICONS[index].id,
            )
        }
        settings.save(custom)
        try {
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                scenario.onActivity { activity ->
                    val root = activity.findViewById<android.view.ViewGroup>(android.R.id.content)
                    val cockpit = root.getChildAt(0) as LandscapeCockpitView
                    assertEquals(custom.map { it.normalized() }, cockpit.activeSideButtons())
                }
            }
        } finally {
            settings.save(original)
        }
    }
}
