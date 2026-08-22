package ca.gmode.triprecorder.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SideButtonSettingsTest {
    @Test
    fun normalizedKeepsInstalledAppTargetAndEditableIcon() {
        val config = SideButtonConfig(
            SideButtonSlot.LEFT_TOP,
            "  Trail   Map  ",
            "app:com.example.maps/.MainActivity",
            "navigation",
        ).normalized()

        assertEquals("Trail Map", config.label)
        assertEquals("app:com.example.maps/.MainActivity", config.target)
        assertEquals("navigation", config.iconId)
    }

    @Test
    fun normalizedLimitsLabelAndRepairsUnsupportedValues() {
        val config = SideButtonConfig(
            SideButtonSlot.RIGHT_BOTTOM,
            "A very long custom dashboard button label",
            "unsupported",
            "unsupported",
        ).normalized()

        assertTrue(config.label.length <= SideButtonConfig.MAX_LABEL_LENGTH)
        assertEquals(SideButtonSettings.DEFAULTS.getValue(SideButtonSlot.RIGHT_BOTTOM).target, config.target)
        assertEquals(SideButtonSettings.DEFAULTS.getValue(SideButtonSlot.RIGHT_BOTTOM).iconId, config.iconId)
    }

    @Test
    fun everyDashboardSlotHasADefault() {
        assertEquals(SideButtonSlot.entries.toSet(), SideButtonSettings.DEFAULTS.keys)
    }
}
