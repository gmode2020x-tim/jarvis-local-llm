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
    fun zeroAttitude3DPreviewRendersInsideTheGauge() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val cockpit = LandscapeCockpitView(context)
        cockpit.setState(
            CockpitState(
                vehicleId = "sxs",
                pitchDegrees = 0.0,
                rollDegrees = 0.0,
                readings = listOf(CockpitReading("Attitude", "P +0°  R +0°", "", subtitle = "LIVE 3D", gaugeId = "attitude")),
            ),
        )
        cockpit.measure(
            android.view.View.MeasureSpec.makeMeasureSpec(1280, android.view.View.MeasureSpec.EXACTLY),
            android.view.View.MeasureSpec.makeMeasureSpec(592, android.view.View.MeasureSpec.EXACTLY),
        )
        cockpit.layout(0, 0, 1280, 592)
        val bitmap = android.graphics.Bitmap.createBitmap(1280, 592, android.graphics.Bitmap.Config.ARGB_8888)
        cockpit.draw(android.graphics.Canvas(bitmap))
        assertTrue(android.graphics.Color.alpha(bitmap.getPixel(640, 278)) > 0)
        val output = java.io.File(context.getExternalFilesDir(null), "gmode-3d-attitude-preview.png")
        output.outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }

    @Test
    fun allFiveSceneVehiclesRenderAcrossSafeAndLimitAttitudes() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val cockpit = LandscapeCockpitView(context)
        cockpit.measure(
            android.view.View.MeasureSpec.makeMeasureSpec(1280, android.view.View.MeasureSpec.EXACTLY),
            android.view.View.MeasureSpec.makeMeasureSpec(592, android.view.View.MeasureSpec.EXACTLY),
        )
        cockpit.layout(0, 0, 1280, 592)
        val bitmap = android.graphics.Bitmap.createBitmap(1280, 592, android.graphics.Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(bitmap)
        DashboardSettings.VEHICLES.forEach { vehicle ->
            listOf(-35.0 to 28.0, 0.0 to 0.0, 32.0 to -38.0).forEach { (pitch, roll) ->
                cockpit.setState(
                    CockpitState(
                        vehicleId = vehicle.id,
                        pitchDegrees = pitch,
                        rollDegrees = roll,
                        readings = listOf(CockpitReading("Attitude", "", "", gaugeId = "attitude")),
                    ),
                )
                cockpit.draw(canvas)
                assertTrue("${vehicle.id} should render an opaque gauge centre", android.graphics.Color.alpha(bitmap.getPixel(640, 278)) > 0)
            }
        }
        bitmap.recycle()
    }

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
    fun everySceneVehicleHasAProcedural3DModel() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val cockpit = LandscapeCockpitView(context)
        assertEquals(5, DashboardSettings.VEHICLES.size)
        DashboardSettings.VEHICLES.forEach { assertTrue(cockpit.supports3DVehicle(it.id)) }
    }

    @Test
    fun freeOrbitChangesThe3DCameraViewpoint() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val cockpit = LandscapeCockpitView(context)
        cockpit.setState(CockpitState(vehicleViewModeId = "free", readings = listOf(CockpitReading("Attitude", "", "", gaugeId = "attitude"))))
        cockpit.measure(
            android.view.View.MeasureSpec.makeMeasureSpec(1280, android.view.View.MeasureSpec.EXACTLY),
            android.view.View.MeasureSpec.makeMeasureSpec(592, android.view.View.MeasureSpec.EXACTLY),
        )
        cockpit.layout(0, 0, 1280, 592)
        val before = cockpit.cameraSnapshot()
        cockpit.onTouchEvent(android.view.MotionEvent.obtain(0, 0, android.view.MotionEvent.ACTION_DOWN, 640f, 278f, 0))
        cockpit.onTouchEvent(android.view.MotionEvent.obtain(0, 20, android.view.MotionEvent.ACTION_MOVE, 710f, 245f, 0))
        cockpit.onTouchEvent(android.view.MotionEvent.obtain(0, 30, android.view.MotionEvent.ACTION_UP, 710f, 245f, 0))
        val after = cockpit.cameraSnapshot()
        assertTrue(kotlin.math.abs(after.first - before.first) > 5f)
        assertTrue(kotlin.math.abs(after.second - before.second) > 2f)
    }

    @Test
    fun tripTypesSelectTheMatchingVehicleSceneBackground() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val expectedBackgrounds = listOf(
            Triple("OFF ROAD", "sxs", R.drawable.dial_offroad_landscape),
            Triple("STREET", "truck", R.drawable.dial_street_landscape),
            Triple("SNOW", "snowmobile", R.drawable.dial_snow_landscape),
            Triple("WATER", "mini_jet_boat", R.drawable.dial_water_landscape),
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
            "sxs" to "off_road",
            "snowmobile" to "snow",
            "truck" to "street",
            "sand_rail" to "off_road",
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
                assertTrue(labels.any { it.contains("back facing forward", ignoreCase = true) })
                assertTrue(labels.any { it == "CALIBRATE PITCH + ROLL ZERO" })
                assertTrue(labels.any { it == "EXPORT RECORDED TRIP" })
                assertTrue(labels.any { it == "EXPORT TRIP FILE" })
                assertTrue(labels.any { it == "USE CURRENT WI-FI" })
                assertTrue(labels.any { it == "CHOOSE WI-FI IN ANDROID" })
                assertTrue(labels.any { it.contains("Hybrid mode uses both signals") })
                assertTrue(labels.any { it == "OFF ROAD SCENE" })
                assertTrue(labels.any { it == "3D CAMERA" })
                assertTrue(labels.any { it == "CAUTION START" })
                assertTrue(labels.any { it == "LIMIT START" })
                assertTrue(labels.none { it.contains("ROLL PERSPECTIVE") })
            }
        }
    }

    @Test
    fun configuredGaugeOrderPreservesEverySelectedInstrument() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val settings = DashboardSettings(context)
        val original = settings.read()
        val selected = DashboardSettings.GAUGES.map { it.id }
        settings.save(DashboardConfig(vehicleId = "truck", gaugeIds = selected))
        try {
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                scenario.onActivity { activity ->
                    val root = activity.findViewById<android.view.ViewGroup>(android.R.id.content)
                    val cockpit = root.getChildAt(0) as LandscapeCockpitView
                    assertEquals(
                        listOf(
                            "Speed", "Trip time", "Distance", "GPS altitude", "Elevation gain", "GPS course", "Attitude",
                            "Shock peak", "Phone battery", "GPS satellites", "GPS accuracy", "Coordinates", "Station pressure",
                        ),
                        cockpit.activeGaugeTitles(),
                    )
                }
            }
        } finally {
            settings.save(original)
        }
    }

    @Test
    fun footerArrowCyclesThroughAllGaugesAndWrapsToTheFirst() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val cockpit = LandscapeCockpitView(context)
        val readings = DashboardSettings.GAUGES.map { CockpitReading(it.label, "--", "") }
        cockpit.setState(CockpitState(readings = readings))
        cockpit.measure(
            android.view.View.MeasureSpec.makeMeasureSpec(1280, android.view.View.MeasureSpec.EXACTLY),
            android.view.View.MeasureSpec.makeMeasureSpec(592, android.view.View.MeasureSpec.EXACTLY),
        )
        cockpit.layout(0, 0, 1280, 592)
        cockpit.draw(android.graphics.Canvas(android.graphics.Bitmap.createBitmap(1280, 592, android.graphics.Bitmap.Config.ARGB_8888)))

        readings.forEach { reading ->
            assertEquals(reading.title, cockpit.activeGaugeTitle())
            val tap = android.view.MotionEvent.obtain(0L, 0L, android.view.MotionEvent.ACTION_UP, 810f, 529f, 0)
            cockpit.onTouchEvent(tap)
            tap.recycle()
        }

        assertEquals(readings.first().title, cockpit.activeGaugeTitle())
    }

    @Test
    fun everyGaugeFaceRendersAtReferenceResolution() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val cockpit = LandscapeCockpitView(context)
        val samples = listOf(
            CockpitReading("Speed", "58", "km/h", 58.0 / 120.0, "GPS SPEED", gaugeId = "speed", numericValue = 58.0),
            CockpitReading("Trip time", "1:42", "h:mm", 42.0 / 60.0, "RECORDING", gaugeId = "trip_time", numericValue = 102.0),
            CockpitReading("Distance", "42.7", "km", 42.7 / 50.0, "TRIP", gaugeId = "distance", numericValue = 42.7),
            CockpitReading("GPS altitude", "543", "m", 0.58, "WGS84", gaugeId = "altitude", numericValue = 543.0),
            CockpitReading("Elevation gain", "386", "m", 0.77, "ASCENT", gaugeId = "elevation_gain", numericValue = 386.0),
            CockpitReading("GPS course", "NW", "315°", 315.0 / 360.0, "COURSE OVER GROUND", 315.0, "compass", 315.0),
            CockpitReading("Attitude", "P +12°  R -8°", "", subtitle = "LIVE 3D", gaugeId = "attitude"),
            CockpitReading("Shock peak", "1.25", "g", 0.42, "LINEAR ACCELERATION", gaugeId = "g_force", numericValue = 1.25),
            CockpitReading("Phone battery", "74", "%", 0.74, "S24", gaugeId = "battery", numericValue = 74.0),
            CockpitReading("GPS satellites", "12", "used in fix", 0.4, "GNSS", gaugeId = "gps_satellites", numericValue = 12.0),
            CockpitReading("GPS accuracy", "7", "± m", 0.72, "FIX QUALITY", gaugeId = "gps_accuracy", numericValue = 7.0),
            CockpitReading("Coordinates", "43.6532  -79.3832", "lat / lon", 1.0, "GPS POSITION", gaugeId = "coordinates", numericValue = 1.0),
            CockpitReading("Station pressure", "1012", "hPa", 0.81, "S24 BAROMETER", gaugeId = "pressure", numericValue = 1012.0),
        )
        cockpit.measure(
            android.view.View.MeasureSpec.makeMeasureSpec(1280, android.view.View.MeasureSpec.EXACTLY),
            android.view.View.MeasureSpec.makeMeasureSpec(592, android.view.View.MeasureSpec.EXACTLY),
        )
        cockpit.layout(0, 0, 1280, 592)
        samples.forEach { sample ->
            cockpit.setState(CockpitState(readings = listOf(sample)))
            val bitmap = android.graphics.Bitmap.createBitmap(1280, 592, android.graphics.Bitmap.Config.ARGB_8888)
            cockpit.draw(android.graphics.Canvas(bitmap))
            assertTrue("${sample.title} face should draw an opaque centre", android.graphics.Color.alpha(bitmap.getPixel(640, 278)) > 0)
            bitmap.recycle()
        }
    }

    @Test
    fun attitudeLineKeepsAndExpiresAShortRotationHistory() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val cockpit = LandscapeCockpitView(context)
        cockpit.measure(
            android.view.View.MeasureSpec.makeMeasureSpec(1280, android.view.View.MeasureSpec.EXACTLY),
            android.view.View.MeasureSpec.makeMeasureSpec(592, android.view.View.MeasureSpec.EXACTLY),
        )
        cockpit.layout(0, 0, 1280, 592)
        val bitmap = android.graphics.Bitmap.createBitmap(1280, 592, android.graphics.Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(bitmap)
        fun render(angle: Double) {
            cockpit.setState(
                CockpitState(
                    readings = listOf(CockpitReading("Attitude", "", "", gaugeId = "attitude")),
                    rollDegrees = angle,
                ),
            )
            cockpit.draw(canvas)
        }

        render(0.0)
        render(30.0)
        repeat(6) {
            Thread.sleep(55)
            render(30.0)
        }
        assertTrue("rotation should leave multiple fading line positions", cockpit.attitudeTrailAngles().distinct().size >= 3)

        Thread.sleep(1_000)
        render(30.0)
        assertTrue("expired history should be pruned", cockpit.attitudeTrailAngles().size <= 1)
        bitmap.recycle()
    }

    @Test
    fun tripTypeCyclesSceneAndUserSelectedVehicleTogether() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val settings = DashboardSettings(context)
        val original = settings.read()
        settings.save(
            DashboardConfig(
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

                assertVehicleAndCycle("sand_rail", R.drawable.dial_sand_landscape)
                assertVehicleAndCycle("snowmobile", R.drawable.dial_snow_landscape)
                assertVehicleAndCycle("mini_jet_boat", R.drawable.dial_water_landscape)
                assertVehicleAndCycle("truck", R.drawable.dial_street_landscape)
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
