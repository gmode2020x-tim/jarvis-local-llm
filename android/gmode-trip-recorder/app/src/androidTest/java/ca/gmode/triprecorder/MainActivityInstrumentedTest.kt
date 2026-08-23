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
        val cockpit = LandscapeCockpitView(context)
        val resources = DashboardSettings.VEHICLES.flatMap { vehicle ->
            listOf("side", "front", "rear").map { view -> cockpit.vehicleResourceId(vehicle.id, view) }
        }

        assertEquals(DashboardSettings.VEHICLES.size * 3, resources.size)
        assertEquals(resources.size, resources.toSet().size)
        resources.forEach { resourceId ->
            val bitmap = BitmapFactory.decodeResource(context.resources, resourceId)
            assertEquals(512, bitmap.width)
            assertEquals(512, bitmap.height)
            assertTrue(bitmap.hasAlpha())
            assertEquals(0, android.graphics.Color.alpha(bitmap.getPixel(0, 0)))
        }
    }

    @Test
    fun newVehicleViewsDoNotContainABakedCheckerboard() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val cockpit = LandscapeCockpitView(context)
        val resources = listOf("sand_rail", "trophy_truck", "unicycle", "mini_jet_boat")
            .flatMap { vehicleId ->
                listOf("side", "front", "rear").map { view ->
                    cockpit.vehicleResourceId(vehicleId, view)
                }
            }

        resources.forEach { resourceId ->
            val bitmap = BitmapFactory.decodeResource(context.resources, resourceId)
            var transparentPixels = 0
            var brightNeutralPixels = 0
            for (y in 0 until bitmap.height) {
                for (x in 0 until bitmap.width) {
                    val color = bitmap.getPixel(x, y)
                    if (android.graphics.Color.alpha(color) == 0) {
                        transparentPixels++
                    } else {
                        val red = android.graphics.Color.red(color)
                        val green = android.graphics.Color.green(color)
                        val blue = android.graphics.Color.blue(color)
                        val spread = maxOf(red, green, blue) - minOf(red, green, blue)
                        if (minOf(red, green, blue) >= 210 && spread <= 24) {
                            brightNeutralPixels++
                        }
                    }
                }
            }

            val pixelCount = bitmap.width * bitmap.height
            assertTrue(transparentPixels > pixelCount / 2)
            assertTrue(brightNeutralPixels < pixelCount / 4)
        }
    }

    @Test
    fun tripTypesSelectTheMatchingVehicleSceneBackground() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val expectedBackgrounds = listOf(
            Triple("OFF ROAD", "sxs", R.drawable.dial_offroad_landscape),
            Triple("STREET", "car", R.drawable.dial_street_landscape),
            Triple("SNOW", "snowmobile", R.drawable.dial_snow_landscape),
            Triple("WATER", "boat", R.drawable.dial_water_landscape),
            Triple("WATER", "seadoo", R.drawable.dial_water_landscape),
            Triple("OFF ROAD", "sand_rail", R.drawable.dial_sand_landscape),
        )
        val expectedDimensions = 768 to 512
        val cockpit = LandscapeCockpitView(context)

        expectedBackgrounds.forEach { (tripType, vehicleId, resourceId) ->
            cockpit.setState(
                CockpitState(
                    tripTypeLabel = tripType,
                    vehicleId = vehicleId,
                    offRoadSceneId = if (resourceId == R.drawable.dial_sand_landscape) "sand" else "dirt",
                ),
            )
            assertEquals(resourceId, cockpit.activeBackgroundResourceId())
            BitmapFactory.decodeResource(context.resources, resourceId).also { bitmap ->
                assertEquals(expectedDimensions.first, bitmap.width)
                assertEquals(expectedDimensions.second, bitmap.height)
            }
        }
    }

    @Test
    fun eachVehicleStartsWithItsMatchingSceneType() {
        val expectedTypes = mapOf(
            "dirt_bike" to "off_road",
            "sxs" to "off_road",
            "quad" to "off_road",
            "snowmobile" to "snow",
            "three_wheeler" to "off_road",
            "truck" to "off_road",
            "car" to "street",
            "street_motorcycle" to "street",
            "clown_car" to "street",
            "snow_bike" to "snow",
            "snowcat" to "snow",
            "tracked_utv" to "snow",
            "boat" to "water",
            "seadoo" to "water",
            "hovercraft" to "water",
            "kayak" to "water",
            "sand_rail" to "off_road",
            "trophy_truck" to "off_road",
            "unicycle" to "off_road",
            "mini_jet_boat" to "water",
        )

        DashboardSettings.VEHICLES.forEach { vehicle ->
            assertEquals(expectedTypes.getValue(vehicle.id), DashboardSettings.defaultTripType(vehicle.id))
        }
    }

    @Test
    fun launchesFullScreenLandscapeCockpit() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        context.getSharedPreferences("dashboard_settings", android.content.Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()

        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val root = activity.findViewById<android.view.ViewGroup>(android.R.id.content)
                val cockpit = root.getChildAt(0) as LandscapeCockpitView
                assertEquals(Configuration.ORIENTATION_LANDSCAPE, activity.resources.configuration.orientation)
                assertEquals(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE, activity.requestedOrientation)
                assertEquals(R.drawable.dial_offroad_landscape, cockpit.activeBackgroundResourceId())
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
                assertTrue(labels.any { it == "EXPORT RECORDED TRIP" })
                assertTrue(labels.any { it == "EXPORT TRIP FILE" })
                assertTrue(labels.any { it == "USE CURRENT WI-FI" })
                assertTrue(labels.any { it == "CHOOSE WI-FI IN ANDROID" })
                assertTrue(labels.any { it.contains("Hybrid mode uses both signals") })
                assertTrue(labels.any { it == "STREET VEHICLE" })
                assertTrue(labels.any { it == "OFF ROAD VEHICLE" })
                assertTrue(labels.any { it == "OFF ROAD SCENE" })
                assertTrue(labels.any { it == "SNOW VEHICLE" })
                assertTrue(labels.any { it == "WATER VEHICLE" })
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
    fun tripTypeCyclesSceneAndUserSelectedVehicleTogether() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val settings = DashboardSettings(context)
        val original = settings.read()
        settings.save(
            DashboardConfig(
                vehicleId = "quad",
                streetVehicleId = "clown_car",
                snowVehicleId = "tracked_utv",
                waterVehicleId = "hovercraft",
                offRoadSceneId = "sand",
            ),
        )
        try {
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                fun assertVehicleAndCycle(expectedVehicleId: String, expectedBackground: Int) {
                    scenario.onActivity { activity ->
                        val root = activity.findViewById<android.view.ViewGroup>(android.R.id.content)
                        val cockpit = root.getChildAt(0) as LandscapeCockpitView
                        assertEquals(expectedVehicleId, cockpit.activeVehicleId())
                        assertEquals(expectedBackground, cockpit.activeBackgroundResourceId())
                        cockpit.onAction?.invoke(CockpitAction.TRIP_TYPE)
                    }
                    android.os.SystemClock.sleep(1_200)
                }

                assertVehicleAndCycle("quad", R.drawable.dial_sand_landscape)
                assertVehicleAndCycle("tracked_utv", R.drawable.dial_snow_landscape)
                assertVehicleAndCycle("hovercraft", R.drawable.dial_water_landscape)
                assertVehicleAndCycle("clown_car", R.drawable.dial_street_landscape)
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
