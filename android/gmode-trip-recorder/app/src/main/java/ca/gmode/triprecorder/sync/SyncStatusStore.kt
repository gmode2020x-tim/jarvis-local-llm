package ca.gmode.triprecorder.sync

import android.content.Context
import java.time.Instant

data class SyncStatus(val state: String, val message: String, val updatedAt: String)

class SyncStatusStore(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences("sync_status", Context.MODE_PRIVATE)

    fun update(state: String, message: String) {
        preferences.edit()
            .putString("state", state)
            .putString("message", message.take(240))
            .putString("updated_at", Instant.now().toString())
            .apply()
    }

    fun read(): SyncStatus = SyncStatus(
        state = preferences.getString("state", "Not synchronized yet") ?: "Not synchronized yet",
        message = preferences.getString("message", "") ?: "",
        updatedAt = preferences.getString("updated_at", "") ?: "",
    )
}
