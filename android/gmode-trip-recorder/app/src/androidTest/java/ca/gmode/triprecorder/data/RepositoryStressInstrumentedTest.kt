package ca.gmode.triprecorder.data

import android.location.Location
import androidx.room.Room
import androidx.room.withTransaction
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RepositoryStressInstrumentedTest {
    private lateinit var database: AppDatabase
    private lateinit var dao: TripDao

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java,
        ).build()
        dao = database.tripDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun concurrentWritersKeepEverySequenceUniqueAndTripCountAccurate() = runBlocking {
        val repository = RecordingRepository(dao)
        val trip = repository.startTrip("Concurrent stress", "off_road")
        val workers = 8
        val pointsPerWorker = 250

        coroutineScope {
            repeat(workers) { worker ->
                launch(Dispatchers.Default) {
                    repeat(pointsPerWorker) { index ->
                        val sequenceHint = worker * pointsPerWorker + index
                        val location = Location("stress").apply {
                            latitude = 44.0 + sequenceHint * 0.000001
                            longitude = -79.0 - sequenceHint * 0.000001
                            time = 1_700_000_000_000L + sequenceHint * 1_000L
                            accuracy = 4f
                            speed = 8f
                        }
                        repository.recordLocation(
                            trip.id,
                            location,
                            SensorSnapshot(1013.0, 0.1, 0.2, 0.03),
                            PhoneSnapshot(80.0, false, "offline", 9),
                        )
                    }
                }
            }
        }

        val expected = workers * pointsPerWorker
        val saved = dao.getTrip(trip.id)!!
        val points = dao.getPendingPoints(trip.id, expected + 1)
        assertEquals(expected, saved.pointCount)
        assertEquals(expected, repository.pendingPointCount())
        assertEquals((expected - 1).toLong(), dao.getLastSequence(trip.id))
        assertEquals(expected, points.map { it.id }.distinct().size)
        assertEquals((0L until expected.toLong()).toList(), points.map { it.sequence })
        assertTrue(saved.distanceMeters > 0.0)
    }

    @Test
    fun twentyFiveThousandAndOnePointsRemainDurableAndPageInExactUploadBatches() = runBlocking {
        val pointCount = 25_001
        val trip = stressTrip("batch-stress", pointCount)
        database.withTransaction {
            dao.upsertTrip(trip.copy(pointCount = 0))
            repeat(pointCount) { sequence ->
                dao.insertPoint(stressPoint(trip.id, sequence.toLong()))
            }
            dao.upsertTrip(trip)
        }

        assertEquals(pointCount, dao.getPendingPointCount(trip.id))
        assertEquals(-1L, dao.insertPoint(stressPoint(trip.id, 0L)))

        val uploadedIds = mutableSetOf<String>()
        val batchSizes = mutableListOf<Int>()
        while (true) {
            val batch = dao.getPendingPoints(trip.id, 500)
            if (batch.isEmpty()) break
            batchSizes += batch.size
            uploadedIds += batch.map { it.id }
            dao.markPointsSynced(batch.map { it.id })
        }
        dao.markTripSyncedIfUnchanged(trip.id, trip.updatedAtEpochMs)

        assertEquals(51, batchSizes.size)
        assertEquals(List(50) { 500 } + 1, batchSizes)
        assertEquals(pointCount, uploadedIds.size)
        assertEquals(0, dao.getPendingPointCount(trip.id))
        assertEquals(false, dao.getTrip(trip.id)?.needsSync)
    }

    private fun stressTrip(id: String, pointCount: Int) = TripEntity(
        id = id,
        title = "Stress trip",
        tripType = "off_road",
        status = "complete",
        startAt = "2026-08-22T16:00:00Z",
        endAt = "2026-08-22T18:00:00Z",
        needsSync = true,
        updatedAtEpochMs = 42L,
        pointCount = pointCount,
    )

    private fun stressPoint(tripId: String, sequence: Long) = PointEntity(
        id = "$tripId:$sequence",
        tripId = tripId,
        sequence = sequence,
        recordedAt = "2026-08-22T16:00:00Z",
        latitude = 44.0 + sequence * 0.000001,
        longitude = -79.0 - sequence * 0.000001,
        accuracyMeters = 4.0,
        altitudeMeters = 250.0,
        verticalAccuracyMeters = 2.0,
        speedMps = 8.0,
        bearingDegrees = 90.0,
        pressureHpa = 1008.0,
        accelerationRmsMs2 = 0.12,
        accelerationPeakMs2 = 0.3,
        gyroscopePeakRadS = 0.04,
        batteryPercent = 72.0,
        isCharging = false,
        networkType = "offline",
        satelliteCount = 10,
    )
}
