package ca.gmode.triprecorder.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TripDaoInstrumentedTest {
    private lateinit var database: AppDatabase
    private lateinit var dao: TripDao

    @Before
    fun createDatabase() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java,
        ).allowMainThreadQueries().build()
        dao = database.tripDao()
    }

    @After
    fun closeDatabase() {
        database.close()
    }

    @Test
    fun updatingTripDoesNotCascadeDeleteItsRecordedPoint() = runBlocking {
        val trip = TripEntity(
            id = "trip-1",
            title = "Persistence test",
            tripType = "street",
            status = "active",
            startAt = "2026-08-21T16:00:00Z",
            updatedAtEpochMs = 1L,
        )
        dao.upsertTrip(trip)

        val point = PointEntity(
            id = "trip-1:0",
            tripId = trip.id,
            sequence = 0,
            recordedAt = "2026-08-21T16:00:05Z",
            latitude = 44.03,
            longitude = -79.38,
            accuracyMeters = 5.0,
            altitudeMeters = 100.0,
            verticalAccuracyMeters = 1.0,
            speedMps = 2.0,
            bearingDegrees = 90.0,
            pressureHpa = 1013.25,
            accelerationRmsMs2 = 0.1,
            accelerationPeakMs2 = 0.2,
            gyroscopePeakRadS = 0.05,
            batteryPercent = 75.0,
            isCharging = false,
            networkType = "offline",
            satelliteCount = 7,
        )
        dao.insertPointAndTrip(point, trip.copy(pointCount = 1, updatedAtEpochMs = 2L))

        assertEquals(1, dao.getPendingPointCount(trip.id))
        assertEquals(1, dao.getTrip(trip.id)?.pointCount)
    }
}
