package ca.gmode.triprecorder.auto

import android.Manifest
import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import ca.gmode.triprecorder.settings.AutoRecordingSettings
import ca.gmode.triprecorder.settings.AutoRecordingStateStore
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingRequest
import com.google.android.gms.location.LocationServices

class AutoRecordingManager(private val context: Context) {
    private val appContext = context.applicationContext
    private val settings = AutoRecordingSettings(appContext)
    private val state = AutoRecordingStateStore(appContext)
    private val client = LocationServices.getGeofencingClient(appContext)

    fun refreshRegistration(callback: ((Boolean, String) -> Unit)? = null) {
        val config = settings.read()
        if (!config.enabled) {
            client.removeGeofences(pendingIntent())
            complete(false, "Automatic recording is off", callback)
            return
        }
        if (!config.hasHomeLocation) {
            complete(false, "Set the home location to arm automatic recording", callback)
            return
        }
        if (!hasFineLocation()) {
            complete(false, "Precise location permission is required", callback)
            return
        }
        if (!hasBackgroundLocation()) {
            complete(false, "Choose Allow all the time in Android location settings", callback)
            return
        }
        register(config, callback)
    }

    @SuppressLint("MissingPermission")
    private fun register(
        config: ca.gmode.triprecorder.settings.AutoRecordingConfig,
        callback: ((Boolean, String) -> Unit)?,
    ) {
        val geofence = Geofence.Builder()
            .setRequestId(HOME_GEOFENCE_ID)
            .setCircularRegion(
                requireNotNull(config.homeLatitude),
                requireNotNull(config.homeLongitude),
                config.homeRadiusMeters.toFloat(),
            )
            .setExpirationDuration(Geofence.NEVER_EXPIRE)
            .setTransitionTypes(
                Geofence.GEOFENCE_TRANSITION_ENTER or
                    Geofence.GEOFENCE_TRANSITION_EXIT or
                    Geofence.GEOFENCE_TRANSITION_DWELL,
            )
            .setLoiteringDelay(config.returnDwellMinutes * 60_000)
            .setNotificationResponsiveness(NOTIFICATION_RESPONSIVENESS_MS)
            .build()
        val request = GeofencingRequest.Builder()
            .setInitialTrigger(GeofencingRequest.INITIAL_TRIGGER_ENTER)
            .addGeofence(geofence)
            .build()
        val pendingIntent = pendingIntent()
        client.removeGeofences(pendingIntent).addOnCompleteListener {
            client.addGeofences(request, pendingIntent)
                .addOnSuccessListener {
                    val message = if (state.activeAutoTripId == null) {
                        "Armed — starts after leaving the ${config.homeRadiusMeters} m home zone"
                    } else {
                        "Away from home — automatic trip is recording"
                    }
                    complete(
                        true,
                        message,
                        callback,
                    )
                }
                .addOnFailureListener { error ->
                    complete(false, "Could not arm home zone: ${error.message ?: error.javaClass.simpleName}", callback)
                }
        }
    }

    fun hasFineLocation(): Boolean = ContextCompat.checkSelfPermission(
        appContext,
        Manifest.permission.ACCESS_FINE_LOCATION,
    ) == PackageManager.PERMISSION_GRANTED

    fun hasBackgroundLocation(): Boolean = Build.VERSION.SDK_INT < Build.VERSION_CODES.Q ||
        ContextCompat.checkSelfPermission(
            appContext,
            Manifest.permission.ACCESS_BACKGROUND_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED

    private fun pendingIntent(): PendingIntent {
        var flags = PendingIntent.FLAG_UPDATE_CURRENT
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) flags = flags or PendingIntent.FLAG_MUTABLE
        return PendingIntent.getBroadcast(
            appContext,
            GEOFENCE_REQUEST_CODE,
            Intent(appContext, GeofenceTransitionReceiver::class.java),
            flags,
        )
    }

    private fun complete(success: Boolean, message: String, callback: ((Boolean, String) -> Unit)?) {
        state.updateStatus(message)
        callback?.invoke(success, message)
    }

    companion object {
        const val HOME_GEOFENCE_ID = "gmode_home"
        private const val GEOFENCE_REQUEST_CODE = 4101
        private const val NOTIFICATION_RESPONSIVENESS_MS = 60_000
    }
}
