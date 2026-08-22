package ca.gmode.triprecorder.auto

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import ca.gmode.triprecorder.data.AppDatabase
import ca.gmode.triprecorder.data.RecordingRepository
import ca.gmode.triprecorder.settings.AutoRecordingConfig
import ca.gmode.triprecorder.settings.AutoRecordingSettings
import ca.gmode.triprecorder.settings.AutoRecordingStateStore
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AutoTripControllerInstrumentedTest {
    private lateinit var context: Context
    private lateinit var database: AppDatabase
    private lateinit var repository: RecordingRepository
    private lateinit var settings: AutoRecordingSettings
    private lateinit var state: AutoRecordingStateStore

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences("auto_recording_settings", Context.MODE_PRIVATE).edit().clear().commit()
        context.getSharedPreferences("auto_recording_state", Context.MODE_PRIVATE).edit().clear().commit()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = RecordingRepository(database.tripDao())
        settings = AutoRecordingSettings(context)
        state = AutoRecordingStateStore(context)
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun homeExitStartsAndReturnDwellStopsOnlyAutomaticTrip() = runBlocking {
        settings.save(
            AutoRecordingConfig(
                enabled = true,
                homeLatitude = 44.0,
                homeLongitude = -79.0,
                returnDwellMinutes = 3,
                tripType = "off_road",
            ),
        )
        var startedTripId: String? = null
        var serviceStopped = false
        val controller = AutoTripController(
            context = context,
            repository = repository,
            settings = settings,
            state = state,
            startTracking = { _, id -> startedTripId = id },
            stopTracking = { serviceStopped = true },
            enqueueSync = {},
        )

        assertTrue(controller.handleExit())
        val active = repository.activeTrip()
        assertEquals(startedTripId, active?.id)
        assertEquals("off_road", active?.tripType)
        assertEquals(active?.id, state.activeAutoTripId)

        controller.handleEnter()
        assertTrue(state.status().contains("3 minutes"))
        assertTrue(controller.handleDwell())
        assertNull(repository.activeTrip())
        assertNull(state.activeAutoTripId)
        assertTrue(serviceStopped)
    }

    @Test
    fun homeExitNeverReplacesAnExistingManualTrip() = runBlocking {
        settings.save(
            AutoRecordingConfig(enabled = true, homeLatitude = 44.0, homeLongitude = -79.0),
        )
        repository.startTrip("Manual trip", "street")
        val controller = AutoTripController(
            context = context,
            repository = repository,
            settings = settings,
            state = state,
            startTracking = { _, _ -> throw AssertionError("must not start") },
            stopTracking = {},
            enqueueSync = {},
        )

        assertFalse(controller.handleExit())
        assertEquals("Manual trip", repository.activeTrip()?.title)
        assertNull(state.activeAutoTripId)
    }

    @Test
    fun hybridWifiSettingsPersistWithTheGpsHomeZone() {
        settings.save(
            AutoRecordingConfig(
                enabled = true,
                homeLatitude = 44.25,
                homeLongitude = -79.5,
                homeRadiusMeters = 300,
                homeWifiSsid = "GMODE Home",
                wifiDepartureDelayMinutes = 4,
            ),
        )

        val restored = settings.read()
        assertEquals("GMODE Home", restored.homeWifiSsid)
        assertEquals(4, restored.wifiDepartureDelayMinutes)
        assertEquals(300, restored.homeRadiusMeters)
        assertTrue(restored.hasHomeWifi)
        assertTrue(restored.hasHomeLocation)
    }
}
