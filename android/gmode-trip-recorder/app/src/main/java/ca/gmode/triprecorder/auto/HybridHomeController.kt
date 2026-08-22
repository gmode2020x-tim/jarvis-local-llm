package ca.gmode.triprecorder.auto

import android.content.Context
import ca.gmode.triprecorder.settings.AutoRecordingSettings
import ca.gmode.triprecorder.settings.AutoRecordingStateStore

class HybridHomeController(
    context: Context,
    private val settings: AutoRecordingSettings = AutoRecordingSettings(context),
    private val state: AutoRecordingStateStore = AutoRecordingStateStore(context),
    private val wifiReader: HomeWifiReader = HomeWifiReader(context),
    private val tripController: AutoTripController = AutoTripController(context),
) {
    private val appContext = context.applicationContext

    suspend fun handleGeofenceExit(): Boolean {
        val config = settings.read()
        if (config.hasHomeWifi && wifiReader.isConnectedTo(config.homeWifiSsid)) {
            state.updateStatus("GPS home-zone exit detected — waiting for ${config.homeWifiSsid} to disconnect")
            return false
        }
        WifiDepartureWorker.cancel(appContext)
        return tripController.handleExit()
    }

    fun handleGeofenceEnter() {
        WifiDepartureWorker.cancel(appContext)
        tripController.handleEnter()
    }

    suspend fun handleGeofenceDwell(): Boolean {
        WifiDepartureWorker.cancel(appContext)
        return tripController.handleDwell()
    }
}
