package ca.gmode.triprecorder.auto

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import android.os.Build
import ca.gmode.triprecorder.settings.AutoRecordingConfig
import ca.gmode.triprecorder.settings.AutoRecordingSettings
import ca.gmode.triprecorder.settings.AutoRecordingStateStore

class HomeWifiReader(context: Context) {
    private val appContext = context.applicationContext
    private val connectivity = appContext.getSystemService(ConnectivityManager::class.java)
    private val wifi = appContext.getSystemService(WifiManager::class.java)

    @SuppressLint("MissingPermission")
    fun currentSsid(): String? = runCatching {
        val network = connectivity.activeNetwork ?: return null
        val capabilities = connectivity.getNetworkCapabilities(network) ?: return null
        if (!capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) return null
        val wifiInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            capabilities.transportInfo as? WifiInfo
        } else {
            @Suppress("DEPRECATION")
            wifi.connectionInfo
        }
        normalizeSsid(wifiInfo?.ssid)
    }.getOrNull()

    fun isConnectedTo(homeSsid: String?): Boolean {
        val normalizedHome = normalizeSsid(homeSsid) ?: return false
        return currentSsid()?.equals(normalizedHome, ignoreCase = false) == true
    }

    companion object {
        fun normalizeSsid(value: String?): String? = value
            ?.trim()
            ?.removeSurrounding("\"")
            ?.takeIf { it.isNotBlank() && !it.equals(UNKNOWN_SSID, ignoreCase = true) }

        private const val UNKNOWN_SSID = "<unknown ssid>"
    }
}

class HomeWifiMonitor(context: Context) {
    private val appContext = context.applicationContext
    private val connectivity = appContext.getSystemService(ConnectivityManager::class.java)

    fun refresh(config: AutoRecordingConfig): Boolean {
        unregister()
        if (!config.enabled || !config.hasHomeWifi) {
            WifiDepartureWorker.cancel(appContext)
            return false
        }
        val request = NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
            .build()
        val registered = runCatching { connectivity.registerNetworkCallback(request, pendingIntent()) }
            .onFailure {
                AutoRecordingStateStore(appContext).updateStatus(
                    "Home Wi-Fi monitor unavailable — GPS home-zone detection remains active",
                )
            }
            .isSuccess
        if (HomeWifiReader(appContext).isConnectedTo(config.homeWifiSsid)) {
            WifiDepartureWorker.cancel(appContext)
        } else {
            WifiDepartureWorker.schedule(appContext, config.wifiDepartureDelayMinutes)
        }
        return registered
    }

    fun unregister() {
        runCatching { connectivity.unregisterNetworkCallback(pendingIntent()) }
    }

    private fun pendingIntent(): PendingIntent {
        var flags = PendingIntent.FLAG_UPDATE_CURRENT
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) flags = flags or PendingIntent.FLAG_MUTABLE
        return PendingIntent.getBroadcast(
            appContext,
            WIFI_CALLBACK_REQUEST_CODE,
            Intent(appContext, HomeWifiReceiver::class.java),
            flags,
        )
    }

    private companion object {
        const val WIFI_CALLBACK_REQUEST_CODE = 4102
    }
}

class HomeWifiReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val config = AutoRecordingSettings(context).read()
        if (!config.enabled || !config.hasHomeWifi) {
            WifiDepartureWorker.cancel(context)
            return
        }
        val state = AutoRecordingStateStore(context)
        if (HomeWifiReader(context).isConnectedTo(config.homeWifiSsid)) {
            WifiDepartureWorker.cancel(context)
            state.updateStatus(
                if (state.activeAutoTripId == null) {
                    "At home on ${config.homeWifiSsid} — GPS home zone is armed"
                } else {
                    "Home Wi-Fi detected — waiting for GPS return confirmation"
                },
            )
        } else {
            WifiDepartureWorker.schedule(context, config.wifiDepartureDelayMinutes)
            state.updateStatus(
                "Home Wi-Fi left — GPS confirmation in ${config.wifiDepartureDelayMinutes} min",
            )
        }
    }
}
