package ca.gmode.triprecorder.tracking

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.location.GnssStatus
import android.location.Location
import android.location.LocationManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.BatteryManager
import android.os.Handler
import android.os.Looper
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import ca.gmode.triprecorder.MainActivity
import ca.gmode.triprecorder.R
import ca.gmode.triprecorder.data.AppDatabase
import ca.gmode.triprecorder.data.PhoneSnapshot
import ca.gmode.triprecorder.data.RecordingRepository
import ca.gmode.triprecorder.settings.AutoRecordingSettings
import ca.gmode.triprecorder.settings.AutoRecordingStateStore
import ca.gmode.triprecorder.sync.SyncScheduler
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.launch

class TrackingService : LifecycleService() {
    private lateinit var fusedLocation: FusedLocationProviderClient
    private lateinit var repository: RecordingRepository
    private lateinit var sensors: SensorCollector
    private lateinit var locationManager: LocationManager
    private var currentTripId: String? = null
    private var satelliteCount: Int? = null
    private var tracking = false

    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            result.locations.forEach(::recordLocation)
        }
    }

    private val gnssCallback = object : GnssStatus.Callback() {
        override fun onSatelliteStatusChanged(status: GnssStatus) {
            satelliteCount = (0 until status.satelliteCount).count { status.usedInFix(it) }
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(
            NOTIFICATION_ID,
            buildNotification("Preparing GPS…"),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION,
        )
        fusedLocation = LocationServices.getFusedLocationProviderClient(this)
        repository = RecordingRepository(AppDatabase.get(this).tripDao())
        sensors = SensorCollector(this)
        locationManager = getSystemService(LocationManager::class.java)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        when (intent?.action) {
            ACTION_START -> beginTracking(intent.getStringExtra(EXTRA_TRIP_ID))
            ACTION_STOP -> stopTripAndService()
            else -> lifecycleScope.launch { beginTracking(repository.activeTrip()?.id) }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        stopTrackingResources()
        super.onDestroy()
    }

    private fun beginTracking(tripId: String?) {
        if (tripId.isNullOrBlank() || tracking) return
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            stopSelf()
            return
        }
        currentTripId = tripId
        tracking = true
        sensors.start()
        runCatching {
            locationManager.registerGnssStatusCallback(gnssCallback, Handler(Looper.getMainLooper()))
        }
        val recordingConfig = AutoRecordingSettings(this).read()
        val intervalMs = recordingConfig.locationIntervalSeconds * 1_000L
        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, intervalMs)
            .setMinUpdateIntervalMillis((intervalMs / 2).coerceAtLeast(1_000L))
            .setMinUpdateDistanceMeters(recordingConfig.minimumDistanceMeters.toFloat())
            .setMaxUpdateDelayMillis((intervalMs * 2).coerceAtLeast(10_000L))
            .build()
        fusedLocation.requestLocationUpdates(request, locationCallback, Looper.getMainLooper())
        updateNotification("Waiting for a GPS fix")
    }

    private fun recordLocation(location: Location) {
        val tripId = currentTripId ?: return
        lifecycleScope.launch {
            val point = repository.recordLocation(
                tripId = tripId,
                location = location,
                sensors = sensors.snapshotAndReset(),
                phone = phoneSnapshot(),
            ) ?: return@launch
            val speedKmh = (point.speedMps ?: 0.0) * 3.6
            val accuracy = point.accuracyMeters?.let { " ±${it.toInt()} m" }.orEmpty()
            updateNotification("${"%.0f".format(speedKmh)} km/h$accuracy • saved on phone")
            SyncScheduler.enqueue(this@TrackingService)
        }
    }

    private fun stopTripAndService() {
        lifecycleScope.launch {
            val stoppedTripId = currentTripId
            repository.stopTrip()
            AutoRecordingStateStore(this@TrackingService).let { state ->
                if (state.activeAutoTripId == stoppedTripId) {
                    state.activeAutoTripId = null
                    state.updateStatus("Automatic trip stopped manually — waiting for the next departure")
                }
            }
            SyncScheduler.enqueue(this@TrackingService)
            stopTrackingResources()
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    private fun stopTrackingResources() {
        if (!tracking) return
        tracking = false
        fusedLocation.removeLocationUpdates(locationCallback)
        sensors.stop()
        runCatching { locationManager.unregisterGnssStatusCallback(gnssCallback) }
        currentTripId = null
    }

    private fun phoneSnapshot(): PhoneSnapshot {
        val batteryManager = getSystemService(BatteryManager::class.java)
        val batteryPercent = batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
            .takeIf { it in 0..100 }
            ?.toDouble()
        val charging = batteryManager.isCharging
        val connectivity = getSystemService(ConnectivityManager::class.java)
        val capabilities = connectivity.getNetworkCapabilities(connectivity.activeNetwork)
        val networkType = when {
            capabilities == null -> "offline"
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "wifi"
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "cellular"
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "ethernet"
            else -> "other"
        }
        return PhoneSnapshot(batteryPercent, charging, networkType, satelliteCount)
    }

    private fun createNotificationChannel() {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                getString(R.string.tracking_channel_name),
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = "Shows when GPS and telemetry are being recorded"
                setShowBadge(false)
            },
        )
    }

    private fun buildNotification(message: String): Notification {
        val openIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val stopIntent = PendingIntent.getService(
            this,
            1,
            Intent(this, TrackingService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_trip)
            .setContentTitle(getString(R.string.tracking_notification_title))
            .setContentText(message)
            .setContentIntent(openIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .addAction(Notification.Action.Builder(null, "Stop trip", stopIntent).build())
            .build()
    }

    private fun updateNotification(message: String) {
        getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, buildNotification(message))
    }

    companion object {
        private const val CHANNEL_ID = "gmode_trip_recording"
        private const val NOTIFICATION_ID = 2101
        private const val ACTION_START = "ca.gmode.triprecorder.START"
        private const val ACTION_STOP = "ca.gmode.triprecorder.STOP"
        private const val EXTRA_TRIP_ID = "trip_id"

        fun start(context: Context, tripId: String) {
            val intent = Intent(context, TrackingService::class.java)
                .setAction(ACTION_START)
                .putExtra(EXTRA_TRIP_ID, tripId)
            ContextCompat.startForegroundService(context, intent)
        }

        fun stop(context: Context) {
            context.startService(Intent(context, TrackingService::class.java).setAction(ACTION_STOP))
        }

        fun stopImmediately(context: Context) {
            context.stopService(Intent(context, TrackingService::class.java))
        }
    }
}
