package ca.gmode.triprecorder.auto

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import ca.gmode.triprecorder.settings.AutoRecordingSettings
import ca.gmode.triprecorder.settings.AutoRecordingStateStore
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import com.google.android.gms.tasks.Tasks
import java.util.concurrent.TimeUnit

class WifiDepartureWorker(
    appContext: Context,
    workerParameters: WorkerParameters,
) : CoroutineWorker(appContext, workerParameters) {
    override suspend fun doWork(): Result {
        val config = AutoRecordingSettings(applicationContext).read()
        if (!config.enabled || !config.hasHomeLocation || !config.hasHomeWifi) return Result.success()
        val state = AutoRecordingStateStore(applicationContext)
        if (HomeWifiReader(applicationContext).isConnectedTo(config.homeWifiSsid)) {
            state.updateStatus("At home on ${config.homeWifiSsid} — departure cancelled")
            return Result.success()
        }
        val location = currentLocation()
        if (location == null) {
            state.updateStatus("Home Wi-Fi left — waiting for GPS home-zone confirmation")
            return Result.success()
        }
        val results = FloatArray(1)
        Location.distanceBetween(
            requireNotNull(config.homeLatitude),
            requireNotNull(config.homeLongitude),
            location.latitude,
            location.longitude,
            results,
        )
        val safelyOutside = isSafelyOutsideHome(
            distanceMeters = results[0],
            accuracyMeters = location.accuracy,
            radiusMeters = config.homeRadiusMeters.toFloat(),
        )
        if (!safelyOutside) {
            state.updateStatus("Home Wi-Fi left, but GPS still places the phone inside the home zone")
            return Result.success()
        }
        AutoTripController(applicationContext).handleExit()
        return Result.success()
    }

    @SuppressLint("MissingPermission")
    private fun currentLocation(): Location? {
        if (ContextCompat.checkSelfPermission(
                applicationContext,
                Manifest.permission.ACCESS_FINE_LOCATION,
            ) != PackageManager.PERMISSION_GRANTED
        ) return null
        return runCatching {
            val token = CancellationTokenSource()
            Tasks.await(
                LocationServices.getFusedLocationProviderClient(applicationContext)
                    .getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, token.token),
                LOCATION_TIMEOUT_SECONDS,
                TimeUnit.SECONDS,
            )
        }.getOrNull()
    }

    companion object {
        const val UNIQUE_WORK_NAME = "home_wifi_departure_confirmation"
        private const val LOCATION_TIMEOUT_SECONDS = 25L

        fun schedule(context: Context, delayMinutes: Int) {
            val request = OneTimeWorkRequestBuilder<WifiDepartureWorker>()
                .setInitialDelay(delayMinutes.coerceIn(1, 30).toLong(), TimeUnit.MINUTES)
                .build()
            WorkManager.getInstance(context.applicationContext).enqueueUniqueWork(
                UNIQUE_WORK_NAME,
                ExistingWorkPolicy.KEEP,
                request,
            )
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context.applicationContext).cancelUniqueWork(UNIQUE_WORK_NAME)
        }

        fun isSafelyOutsideHome(
            distanceMeters: Float,
            accuracyMeters: Float,
            radiusMeters: Float,
        ): Boolean = distanceMeters > radiusMeters + accuracyMeters.coerceAtLeast(0f)
    }
}
