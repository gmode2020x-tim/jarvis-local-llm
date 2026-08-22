package ca.gmode.triprecorder.settings

import android.content.Context

data class DashboardPalette(
    val id: String,
    val label: String,
    val accent: Int,
    val background: Int,
    val panel: Int,
    val outline: Int,
    val muted: Int,
    val inactiveSurface: Int,
    val activeSurface: Int,
    val dialCenter: Int,
    val dialMiddle: Int,
)

data class AppearanceConfig(
    val themeId: String = AppearanceSettings.DEFAULT_THEME_ID,
    val customAccent: Int? = null,
)

class AppearanceSettings(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)

    fun read(): AppearanceConfig = AppearanceConfig(
        themeId = preferences.getString(KEY_THEME_ID, DEFAULT_THEME_ID) ?: DEFAULT_THEME_ID,
        customAccent = preferences.getString(KEY_CUSTOM_ACCENT, null)?.let(::parseRgbHex),
    ).normalized()

    fun save(config: AppearanceConfig) {
        val normalized = config.normalized()
        preferences.edit()
            .putString(KEY_THEME_ID, normalized.themeId)
            .apply {
                if (normalized.customAccent == null) remove(KEY_CUSTOM_ACCENT)
                else putString(KEY_CUSTOM_ACCENT, colorToHex(normalized.customAccent))
            }
            .apply()
    }

    fun palette(config: AppearanceConfig = read()): DashboardPalette = resolvePalette(config)

    companion object {
        const val DEFAULT_THEME_ID = "reference_red"

        val PRESETS = listOf(
            DashboardPalette(
                id = DEFAULT_THEME_ID,
                label = "Reference Red",
                accent = color("E20B17"),
                background = color("030303"),
                panel = color("111111"),
                outline = color("303030"),
                muted = color("B9B9B9"),
                inactiveSurface = color("121212"),
                activeSurface = color("42080C"),
                dialCenter = color("111111"),
                dialMiddle = color("050505"),
            ),
            DashboardPalette(
                id = "gmode_orange",
                label = "GMODE Orange",
                accent = color("FF7900"),
                background = color("070707"),
                panel = color("151515"),
                outline = color("393939"),
                muted = color("AAAAAA"),
                inactiveSurface = color("171717"),
                activeSurface = color("4A2105"),
                dialCenter = color("1B1B1B"),
                dialMiddle = color("0A0A0A"),
            ),
            DashboardPalette(
                id = "electric_blue",
                label = "Electric Blue",
                accent = color("00A8FF"),
                background = color("04090E"),
                panel = color("101820"),
                outline = color("294354"),
                muted = color("A6B4BF"),
                inactiveSurface = color("111B23"),
                activeSurface = color("073653"),
                dialCenter = color("15232E"),
                dialMiddle = color("071016"),
            ),
            DashboardPalette(
                id = "trail_green",
                label = "Trail Green",
                accent = color("42D67A"),
                background = color("040A06"),
                panel = color("101A13"),
                outline = color("2C4A35"),
                muted = color("A8B8AC"),
                inactiveSurface = color("111C14"),
                activeSurface = color("123C20"),
                dialCenter = color("17261B"),
                dialMiddle = color("071009"),
            ),
            DashboardPalette(
                id = "water_cyan",
                label = "Water Cyan",
                accent = color("00D4E8"),
                background = color("030C10"),
                panel = color("0C191E"),
                outline = color("20505A"),
                muted = color("A4B8BC"),
                inactiveSurface = color("0E1B20"),
                activeSurface = color("07434B"),
                dialCenter = color("12262C"),
                dialMiddle = color("051116"),
            ),
            DashboardPalette(
                id = "snow_white",
                label = "Snow White",
                accent = color("E8F1FF"),
                background = color("070A0F"),
                panel = color("151A21"),
                outline = color("46505E"),
                muted = color("AEB8C5"),
                inactiveSurface = color("181E26"),
                activeSurface = color("384454"),
                dialCenter = color("222A35"),
                dialMiddle = color("0A0E14"),
            ),
        )

        fun parseRgbHex(value: String): Int? {
            val normalized = value.trim().removePrefix("#")
            if (normalized.length != 6 || normalized.any { it !in "0123456789abcdefABCDEF" }) return null
            return (0xFF000000L or normalized.toLong(16)).toInt()
        }

        fun colorToHex(color: Int): String = "#%06X".format(color and 0xFFFFFF)

        fun resolvePalette(config: AppearanceConfig): DashboardPalette {
            val normalized = config.normalized()
            val preset = PRESETS.first { it.id == normalized.themeId }
            return normalized.customAccent?.let { preset.copy(accent = it, activeSurface = tintedSurface(it)) } ?: preset
        }

        private fun AppearanceConfig.normalized(): AppearanceConfig = copy(
            themeId = themeId.takeIf { requested -> PRESETS.any { it.id == requested } } ?: DEFAULT_THEME_ID,
        )

        private fun color(rgb: String): Int = (0xFF000000L or rgb.toLong(16)).toInt()

        private fun tintedSurface(accent: Int): Int {
            val red = (accent shr 16) and 0xFF
            val green = (accent shr 8) and 0xFF
            val blue = accent and 0xFF
            return (0xFF shl 24) or ((red * 30 / 100) shl 16) or ((green * 30 / 100) shl 8) or (blue * 30 / 100)
        }

        private const val PREFERENCES = "appearance_settings"
        private const val KEY_THEME_ID = "theme_id"
        private const val KEY_CUSTOM_ACCENT = "custom_accent"
    }
}
