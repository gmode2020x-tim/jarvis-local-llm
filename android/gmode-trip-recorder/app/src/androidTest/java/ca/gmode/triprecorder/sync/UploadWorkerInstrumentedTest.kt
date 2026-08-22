package ca.gmode.triprecorder.sync

import android.content.Context
import androidx.room.withTransaction
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.ListenableWorker
import androidx.work.testing.TestListenableWorkerBuilder
import ca.gmode.triprecorder.data.AppDatabase
import ca.gmode.triprecorder.data.PointEntity
import ca.gmode.triprecorder.data.TripEntity
import ca.gmode.triprecorder.settings.SecureSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class UploadWorkerInstrumentedTest {
    private lateinit var context: Context
    private lateinit var database: AppDatabase
    private lateinit var settings: SecureSettings
    private var server: MockWebServer? = null

    @Before
    fun setUp() = runBlocking {
        context = ApplicationProvider.getApplicationContext()
        database = AppDatabase.get(context)
        withContext(Dispatchers.IO) { database.clearAllTables() }
        settings = SecureSettings(context)
        settings.saveToken(TOKEN)
    }

    @After
    fun tearDown() {
        server?.shutdown()
    }

    @Test
    fun authenticatedWorkerUploadsTwelveHundredFivePointsInThreeIdempotentBatches() = runBlocking {
        seedTrip(1_205)
        val receivedIds = mutableListOf<String>()
        val receivedBatchSizes = mutableListOf<Int>()
        server = MockWebServer().apply {
            dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse {
                    val payload = JSONObject(request.body.readUtf8())
                    val points = payload.getJSONArray("points")
                    val ids = (0 until points.length()).map { points.getJSONObject(it).getString("pointId") }
                    synchronized(receivedIds) {
                        receivedIds += ids
                        receivedBatchSizes += ids.size
                    }
                    return MockResponse()
                        .setResponseCode(if (request.getHeader("Authorization") == "Bearer $TOKEN") 200 else 401)
                        .setHeader("Content-Type", "application/json")
                        .setBody(JSONObject().put("acknowledgedPointIds", JSONArray(ids)).toString())
                }
            }
            start()
        }
        settings.baseUrl = server!!.url("/").toString()

        val result = runWorker()

        assertResultType(ListenableWorker.Result.success(), result)
        assertEquals(listOf(500, 500, 205), receivedBatchSizes)
        assertEquals(1_205, receivedIds.distinct().size)
        assertEquals(0, database.tripDao().getTotalPendingPointCount())
        assertEquals(false, database.tripDao().getTrip(TRIP_ID)?.needsSync)
    }

    @Test
    fun authorizationFailureLeavesEveryPointPendingAndFailsWithoutRetryLoop() = runBlocking {
        seedTrip(3)
        server = MockWebServer().apply {
            enqueue(MockResponse().setResponseCode(401).setBody("{\"error\":\"Unauthorized\"}"))
            start()
        }
        settings.baseUrl = server!!.url("/").toString()

        val result = runWorker()

        assertResultType(ListenableWorker.Result.failure(), result)
        assertEquals(3, database.tripDao().getTotalPendingPointCount())
        assertEquals(1, server!!.requestCount)
    }

    @Test
    fun connectionLossLeavesEveryPointPendingAndRequestsRetry() = runBlocking {
        seedTrip(3)
        server = MockWebServer().apply { start() }
        settings.baseUrl = server!!.url("/").toString()
        server!!.shutdown()
        server = null

        val result = runWorker()

        assertResultType(ListenableWorker.Result.retry(), result)
        assertEquals(3, database.tripDao().getTotalPendingPointCount())
        assertTrue(SyncStatusStore(context).read().state.contains("connection", ignoreCase = true))
    }

    private suspend fun seedTrip(pointCount: Int) {
        val dao = database.tripDao()
        val trip = TripEntity(
            id = TRIP_ID,
            title = "Upload stress",
            tripType = "off_road",
            status = "complete",
            startAt = "2026-08-22T16:00:00Z",
            endAt = "2026-08-22T17:00:00Z",
            needsSync = true,
            updatedAtEpochMs = 100L,
            pointCount = pointCount,
        )
        database.withTransaction {
            dao.upsertTrip(trip)
            repeat(pointCount) { sequence -> dao.insertPoint(point(sequence.toLong())) }
        }
    }

    private fun point(sequence: Long) = PointEntity(
        id = "$TRIP_ID:$sequence",
        tripId = TRIP_ID,
        sequence = sequence,
        recordedAt = "2026-08-22T16:00:00Z",
        latitude = 44.0 + sequence * 0.000001,
        longitude = -79.0,
        accuracyMeters = 4.0,
        altitudeMeters = 250.0,
        verticalAccuracyMeters = 2.0,
        speedMps = 7.0,
        bearingDegrees = 90.0,
        pressureHpa = 1008.0,
        accelerationRmsMs2 = 0.1,
        accelerationPeakMs2 = 0.2,
        gyroscopePeakRadS = 0.03,
        batteryPercent = 75.0,
        isCharging = false,
        networkType = "offline",
        satelliteCount = 10,
    )

    private suspend fun runWorker(): ListenableWorker.Result =
        TestListenableWorkerBuilder<UploadWorker>(context).build().doWork()

    private fun assertResultType(expected: ListenableWorker.Result, actual: ListenableWorker.Result) {
        assertEquals(expected.javaClass, actual.javaClass)
    }

    companion object {
        private const val TOKEN = "gmode-stress-token"
        private const val TRIP_ID = "upload-stress-trip"
    }
}
