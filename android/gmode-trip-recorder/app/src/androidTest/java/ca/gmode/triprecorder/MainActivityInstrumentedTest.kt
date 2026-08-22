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
    fun everyVehicleCategoryHasThreeTransparentGaugeViews() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val resources = listOf(
            R.drawable.vehicle_dirt_bike_side, R.drawable.vehicle_dirt_bike_front, R.drawable.vehicle_dirt_bike_rear,
            R.drawable.vehicle_sxs_side, R.drawable.vehicle_sxs_front, R.drawable.vehicle_sxs_rear,
            R.drawable.vehicle_quad_side, R.drawable.vehicle_quad_front, R.drawable.vehicle_quad_rear,
            R.drawable.vehicle_snowmobile_side, R.drawable.vehicle_snowmobile_front, R.drawable.vehicle_snowmobile_rear,
            R.drawable.vehicle_three_wheeler_side, R.drawable.vehicle_three_wheeler_front, R.drawable.vehicle_three_wheeler_rear,
            R.drawable.vehicle_truck_side, R.drawable.vehicle_truck_front, R.drawable.vehicle_truck_rear,
            R.drawable.vehicle_car_side, R.drawable.vehicle_car_front, R.drawable.vehicle_car_rear,
            R.drawable.vehicle_boat_side, R.drawable.vehicle_boat_front, R.drawable.vehicle_boat_rear,
            R.drawable.vehicle_seadoo_side, R.drawable.vehicle_seadoo_front, R.drawable.vehicle_seadoo_rear,
        )

        assertEquals(27, resources.size)
        resources.forEach { resourceId ->
            val bitmap = BitmapFactory.decodeResource(context.resources, resourceId)
            assertEquals(512, bitmap.width)
            assertEquals(512, bitmap.height)
            assertTrue(bitmap.hasAlpha())
        }
    }

    @Test
    fun tripTypesSelectTheMatchingVehicleSceneBackground() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val expectedBackgrounds = listOf(
            Triple("OFF ROAD", "sxs", R.drawable.dial_mountain_landscape),
            Triple("STREET", "car", R.drawable.dial_street_landscape),
            Triple("SNOW", "snowmobile", R.drawable.dial_snow_landscape),
            Triple("WATER", "boat", R.drawable.dial_water_landscape),
            Triple("WATER", "seadoo", R.drawable.dial_water_landscape),
        )
        val expectedDimensions = 768 to 512
        val cockpit = LandscapeCockpitView(context)

        expectedBackgrounds.forEach { (tripType, vehicleId, resourceId) ->
            cockpit.setState(CockpitState(tripTypeLabel = tripType, vehicleId = vehicleId))
            assertEquals(resourceId, cockpit.activeBackgroundResourceId())
            BitmapFactory.decodeResource(context.resources, resourceId).also { bitmap ->
                assertEquals(expectedDimensions.first, bitmap.width)
                assertEquals(expectedDimensions.second, bitmap.height)
            }
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
    fun cockpitSettingsExposeForwardMountConventionAndStationaryZeroCalibration() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val content = activity.findViewById<android.view.ViewGroup>(android.R.id.content)
                val cockpit = content.getChildAt(0) as LandscapeCockpitView
                cockpit.onAction?.invoke(CockpitAction.SETTINGS)

                val labels = collectText(content)
                assertTrue(labels.any { it.contains("back of the phone facing forward", ignoreCase = true) })
                assertTrue(labels.any { it == "CALIBRATE PITCH + ROLL ZERO" })
                assertTrue(labels.none { it.contains("ROLL PERSPECTIVE") })
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

    private fun collectText(view: android.view.View): List<String> = buildList {
        if (view is android.widget.TextView) add(view.text.toString())
        if (view is android.view.ViewGroup) {
            repeat(view.childCount) { index -> addAll(collectText(view.getChildAt(index))) }
        }
    }
}
