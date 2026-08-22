package ca.gmode.triprecorder.auto

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import ca.gmode.triprecorder.settings.AutoRecordingStateStore
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class GeofenceTransitionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val event = GeofencingEvent.fromIntent(intent) ?: return
        val state = AutoRecordingStateStore(context)
        if (event.hasError()) {
            state.updateStatus("Home zone error: ${event.errorCode}")
            return
        }
        if (event.triggeringGeofences?.none { it.requestId == AutoRecordingManager.HOME_GEOFENCE_ID } != false) return
        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val controller = HybridHomeController(context.applicationContext)
                when (event.geofenceTransition) {
                    Geofence.GEOFENCE_TRANSITION_EXIT -> controller.handleGeofenceExit()
                    Geofence.GEOFENCE_TRANSITION_ENTER -> controller.handleGeofenceEnter()
                    Geofence.GEOFENCE_TRANSITION_DWELL -> controller.handleGeofenceDwell()
                }
            } finally {
                pendingResult.finish()
            }
        }
    }
}
