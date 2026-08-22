package ca.gmode.triprecorder.settings

import android.content.Context

enum class SideButtonSlot(val label: String) {
    LEFT_TOP("Left top"),
    LEFT_MIDDLE("Left middle"),
    LEFT_BOTTOM("Left bottom"),
    RIGHT_TOP("Right top"),
    RIGHT_MIDDLE("Right middle"),
    RIGHT_BOTTOM("Right bottom"),
}

data class SideButtonConfig(
    val slot: SideButtonSlot,
    val label: String,
    val target: String,
    val iconId: String,
) {
    fun normalized(): SideButtonConfig {
        val fallback = SideButtonSettings.DEFAULTS.getValue(slot)
        val cleanLabel = label.trim().replace(Regex("\\s+"), " ").take(MAX_LABEL_LENGTH)
        val cleanTarget = target.takeIf { it.startsWith("action:") || it.startsWith("app:") } ?: fallback.target
        val cleanIcon = iconId.takeIf { it in SideButtonSettings.ICONS.mapTo(mutableSetOf()) { icon -> icon.id } }
            ?: fallback.iconId
        return copy(
            label = cleanLabel.ifBlank { fallback.label },
            target = cleanTarget,
            iconId = cleanIcon,
        )
    }

    companion object {
        const val MAX_LABEL_LENGTH = 18
    }
}

data class SideButtonTarget(val id: String, val label: String)

data class SideButtonIcon(val id: String, val label: String)

class SideButtonSettings(context: Context) {
    private val preferences = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun read(): List<SideButtonConfig> {
        migrateLegacyDefaults()
        return SideButtonSlot.entries.map { slot ->
            val fallback = DEFAULTS.getValue(slot)
            SideButtonConfig(
                slot = slot,
                label = preferences.getString("${slot.name}_label", fallback.label) ?: fallback.label,
                target = preferences.getString("${slot.name}_target", fallback.target) ?: fallback.target,
                iconId = preferences.getString("${slot.name}_icon", fallback.iconId) ?: fallback.iconId,
            ).normalized()
        }
    }

    fun save(configs: List<SideButtonConfig>) {
        val bySlot = configs.associateBy { it.slot }
        val editor = preferences.edit()
        SideButtonSlot.entries.forEach { slot ->
            val config = (bySlot[slot] ?: DEFAULTS.getValue(slot)).normalized()
            editor
                .putString("${slot.name}_label", config.label)
                .putString("${slot.name}_target", config.target)
                .putString("${slot.name}_icon", config.iconId)
        }
        editor.apply()
    }

    companion object {
        const val ACTION_START = "action:start"
        const val ACTION_STOP = "action:stop"
        const val ACTION_TRIP_TYPE = "action:trip_type"
        const val ACTION_AUTO = "action:auto"
        const val ACTION_SYNC = "action:sync"
        const val ACTION_HOME_ASSISTANT = "action:home_assistant"
        const val ACTION_SETTINGS = "action:settings"
        const val ACTION_OPEN_RADIO = "action:open_radio"
        const val ACTION_OPEN_NAVIGATION = "action:open_navigation"
        const val ACTION_OPEN_MUSIC = "action:open_music"
        const val ACTION_OPEN_PHONE = "action:open_phone"
        const val ACTION_OPEN_BROWSER = "action:open_browser"
        const val ACTION_OPEN_APPS = "action:open_apps"
        const val APP_PREFIX = "app:"

        val BUILT_IN_TARGETS = listOf(
            SideButtonTarget(ACTION_START, "GMODE — Start trip"),
            SideButtonTarget(ACTION_STOP, "GMODE — Stop trip"),
            SideButtonTarget(ACTION_TRIP_TYPE, "GMODE — Change trip type"),
            SideButtonTarget(ACTION_AUTO, "GMODE — Automatic recording settings"),
            SideButtonTarget(ACTION_SYNC, "GMODE — Sync now"),
            SideButtonTarget(ACTION_HOME_ASSISTANT, "GMODE — Home Assistant settings"),
            SideButtonTarget(ACTION_SETTINGS, "GMODE — App settings"),
            SideButtonTarget(ACTION_OPEN_RADIO, "Phone — Radio / audio app"),
            SideButtonTarget(ACTION_OPEN_NAVIGATION, "Phone — Navigation app"),
            SideButtonTarget(ACTION_OPEN_MUSIC, "Phone — Music app"),
            SideButtonTarget(ACTION_OPEN_PHONE, "Phone — Dialer"),
            SideButtonTarget(ACTION_OPEN_BROWSER, "Phone — Web browser"),
            SideButtonTarget(ACTION_OPEN_APPS, "Phone — Installed apps settings"),
        )

        val ICONS = listOf(
            SideButtonIcon("app", "Target app icon"),
            SideButtonIcon("radio", "Radio"),
            SideButtonIcon("navigation", "Navigation"),
            SideButtonIcon("music", "Music"),
            SideButtonIcon("phone", "Phone"),
            SideButtonIcon("internet", "Internet / globe"),
            SideButtonIcon("apps", "Apps grid"),
            SideButtonIcon("play", "Start / play"),
            SideButtonIcon("stop", "Stop"),
            SideButtonIcon("sync", "Sync"),
            SideButtonIcon("home", "Home"),
            SideButtonIcon("settings", "Settings"),
        )

        val DEFAULTS = mapOf(
            SideButtonSlot.LEFT_TOP to SideButtonConfig(SideButtonSlot.LEFT_TOP, "RADIO", ACTION_OPEN_RADIO, "radio"),
            SideButtonSlot.LEFT_MIDDLE to SideButtonConfig(SideButtonSlot.LEFT_MIDDLE, "NAVI", ACTION_OPEN_NAVIGATION, "navigation"),
            SideButtonSlot.LEFT_BOTTOM to SideButtonConfig(SideButtonSlot.LEFT_BOTTOM, "MUSIC", ACTION_OPEN_MUSIC, "music"),
            SideButtonSlot.RIGHT_TOP to SideButtonConfig(SideButtonSlot.RIGHT_TOP, "PHONE", ACTION_OPEN_PHONE, "phone"),
            SideButtonSlot.RIGHT_MIDDLE to SideButtonConfig(SideButtonSlot.RIGHT_MIDDLE, "INTERNET", ACTION_OPEN_BROWSER, "internet"),
            SideButtonSlot.RIGHT_BOTTOM to SideButtonConfig(SideButtonSlot.RIGHT_BOTTOM, "APPS", ACTION_OPEN_APPS, "apps"),
        )

        private const val PREFS = "side_button_settings"
        private const val DEFAULTS_VERSION_KEY = "defaults_version"
        private const val CURRENT_DEFAULTS_VERSION = 2
        private val LEGACY_DEFAULTS = mapOf(
            SideButtonSlot.LEFT_TOP to SideButtonConfig(SideButtonSlot.LEFT_TOP, "RADIO", ACTION_START, "radio"),
            SideButtonSlot.LEFT_MIDDLE to SideButtonConfig(SideButtonSlot.LEFT_MIDDLE, "NAVI", ACTION_TRIP_TYPE, "navigation"),
            SideButtonSlot.LEFT_BOTTOM to SideButtonConfig(SideButtonSlot.LEFT_BOTTOM, "MUSIC", ACTION_AUTO, "music"),
            SideButtonSlot.RIGHT_TOP to SideButtonConfig(SideButtonSlot.RIGHT_TOP, "PHONE", ACTION_STOP, "phone"),
            SideButtonSlot.RIGHT_MIDDLE to SideButtonConfig(SideButtonSlot.RIGHT_MIDDLE, "INTERNET", ACTION_SYNC, "internet"),
            SideButtonSlot.RIGHT_BOTTOM to SideButtonConfig(SideButtonSlot.RIGHT_BOTTOM, "APPS", ACTION_HOME_ASSISTANT, "apps"),
        )
    }

    private fun migrateLegacyDefaults() {
        if (preferences.getInt(DEFAULTS_VERSION_KEY, 0) >= CURRENT_DEFAULTS_VERSION) return
        val editor = preferences.edit()
        SideButtonSlot.entries.forEach { slot ->
            val legacy = LEGACY_DEFAULTS.getValue(slot)
            val targetKey = "${slot.name}_target"
            val savedLabel = preferences.getString("${slot.name}_label", null)
            val savedIcon = preferences.getString("${slot.name}_icon", null)
            val isUnchangedLegacyDefault = preferences.getString(targetKey, null) == legacy.target &&
                (savedLabel == null || savedLabel == legacy.label) &&
                (savedIcon == null || savedIcon == legacy.iconId)
            if (isUnchangedLegacyDefault) {
                editor.remove(targetKey)
            }
        }
        editor.putInt(DEFAULTS_VERSION_KEY, CURRENT_DEFAULTS_VERSION).apply()
    }
}
