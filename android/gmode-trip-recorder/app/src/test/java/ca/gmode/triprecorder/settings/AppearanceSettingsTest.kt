package ca.gmode.triprecorder.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AppearanceSettingsTest {
    @Test
    fun rgbHexParsingAcceptsSixDigitColorsAndRejectsInvalidValues() {
        assertEquals("#12ABEF", AppearanceSettings.colorToHex(AppearanceSettings.parseRgbHex("#12abef")!!))
        assertNull(AppearanceSettings.parseRgbHex("#123"))
        assertNull(AppearanceSettings.parseRgbHex("orange"))
    }

    @Test
    fun customAccentOverridesColorWithoutDiscardingSelectedTheme() {
        val custom = AppearanceSettings.parseRgbHex("#D946EF")!!
        val palette = AppearanceSettings.resolvePalette(
            AppearanceConfig(themeId = "trail_green", customAccent = custom),
        )

        assertEquals("trail_green", palette.id)
        assertEquals(custom, palette.accent)
        assertTrue(palette.activeSurface != custom)
    }

    @Test
    fun unknownThemeFallsBackToGmodeOrange() {
        val palette = AppearanceSettings.resolvePalette(AppearanceConfig(themeId = "missing"))
        assertEquals(AppearanceSettings.DEFAULT_THEME_ID, palette.id)
    }
}
