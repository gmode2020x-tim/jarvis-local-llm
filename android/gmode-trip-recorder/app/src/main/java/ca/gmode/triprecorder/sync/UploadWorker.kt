package ca.gmode.triprecorder.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import ca.gmode.triprecorder.data.AppDatabase
import ca.gmode.triprecorder.settings.SecureSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

class UploadWorker(
    appContext: Context,
    workerParameters: WorkerParameters,
) : CoroutineWorker(appContext, workerParameters) {
    private val dao = AppDatabase.get(appContext).tripDao()
    private val settings = SecureSettings(appContext)
    private val statusStore = SyncStatusStore(appContext)
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val token = settings.token()
        val baseUrl = settings.baseUrl
        if (token.isBlank() || baseUrl.isBlank()) {
            statusStore.update("Setup required", "Save the Home Assistant URL and access token.")
            return@withContext Result.failure()
        }

        statusStore.update("Synchronizing", "Uploading locally saved trip data.")
        try {
            repeat(MAX_BATCHES_PER_RUN) {
                val trip = dao.getOldestDirtyTrip() ?: run {
                    statusStore.update("Up to date", "All recorded points are stored in Home Assistant.")
                    return@withContext Result.success()
                }
                val points = dao.getPendingPoints(trip.id, BATCH_SIZE)
                val body = UploadPayloadFactory.build(settings.deviceId, trip, points)
                val request = Request.Builder()
                    .url("$baseUrl/api/gmode_trip_recorder/mobile/upload")
                    .header("Authorization", "Bearer $token")
                    .header("Accept", "application/json")
                    .post(body.toRequestBody(JSON_MEDIA_TYPE))
                    .build()

                client.newCall(request).execute().use { response ->
                    val responseText = response.body?.string().orEmpty()
                    if (!response.isSuccessful) {
                        val message = runCatching {
                            JSONObject(responseText).optString("error")
                        }.getOrNull().orEmpty().ifBlank { "HTTP ${response.code}" }
                        statusStore.update("Sync failed", message)
                        return@withContext if (response.code >= 500 || response.code == 408 || response.code == 429) {
                            Result.retry()
                        } else {
                            Result.failure()
                        }
                    }

                    val acknowledged = JSONObject(responseText)
                        .optJSONArray("acknowledgedPointIds")
                        ?.let { array ->
                            buildList {
                                for (index in 0 until array.length()) add(array.getString(index))
                            }
                        }
                        .orEmpty()
                    if (acknowledged.isNotEmpty()) dao.markPointsSynced(acknowledged)
                    if (dao.getPendingPointCount(trip.id) == 0) {
                        dao.markTripSyncedIfUnchanged(trip.id, trip.updatedAtEpochMs)
                    }
                }
            }
            statusStore.update("Sync queued", "More locally saved points remain; synchronization will continue.")
            Result.retry()
        } catch (error: IOException) {
            statusStore.update("Waiting for connection", error.message ?: "Home Assistant is unreachable.")
            Result.retry()
        } catch (error: Exception) {
            statusStore.update("Sync failed", error.message ?: error.javaClass.simpleName)
            Result.retry()
        }
    }

    companion object {
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
        private const val BATCH_SIZE = 500
        private const val MAX_BATCHES_PER_RUN = 50
    }
}
