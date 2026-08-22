package ca.gmode.triprecorder.settings

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SideButtonSettingsInstrumentedTest {
    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        preferences().edit().clear().commit()
    }

    @After
    fun tearDown() {
        preferences().edit().clear().commit()
    }

    @Test
    fun legacyDecorativeDefaultsMigrateToNewSixButtonLayout() {
        preferences().edit()
            .putString("LEFT_TOP_target", SideButtonSettings.ACTION_START)
            .putString("LEFT_MIDDLE_target", SideButtonSettings.ACTION_TRIP_TYPE)
            .putString("RIGHT_TOP_target", SideButtonSettings.ACTION_STOP)
            .commit()

        val bySlot = SideButtonSettings(context).read().associateBy { it.slot }

        assertPreferredAppOrFallback(
            bySlot.getValue(SideButtonSlot.LEFT_TOP),
            "SPOTIFY",
            "com.spotify.music",
            SideButtonSettings.ACTION_OPEN_MUSIC,
            "app",
        )
        assertPreferredAppOrFallback(
            bySlot.getValue(SideButtonSlot.LEFT_MIDDLE),
            "NAVI",
            "com.google.android.apps.maps",
            SideButtonSettings.ACTION_OPEN_NAVIGATION,
            "app",
        )
        assertEquals("TRIP", bySlot.getValue(SideButtonSlot.RIGHT_TOP).label)
        assertEquals(SideButtonSettings.ACTION_TRIP_TYPE, bySlot.getValue(SideButtonSlot.RIGHT_TOP).target)
        assertEquals("settings", bySlot.getValue(SideButtonSlot.RIGHT_TOP).iconId)
    }

    @Test
    fun previousFactoryDefaultsMigrateButCustomizedButtonsRemain() {
        preferences().edit()
            .putInt("defaults_version", 2)
            .putString("LEFT_TOP_label", "RADIO")
            .putString("LEFT_TOP_target", SideButtonSettings.ACTION_OPEN_RADIO)
            .putString("LEFT_TOP_icon", "radio")
            .putString("LEFT_MIDDLE_label", "TRAILS")
            .putString("LEFT_MIDDLE_target", SideButtonSettings.ACTION_OPEN_NAVIGATION)
            .putString("LEFT_MIDDLE_icon", "navigation")
            .commit()

        val bySlot = SideButtonSettings(context).read().associateBy { it.slot }

        assertPreferredAppOrFallback(
            bySlot.getValue(SideButtonSlot.LEFT_TOP),
            "SPOTIFY",
            "com.spotify.music",
            SideButtonSettings.ACTION_OPEN_MUSIC,
            "app",
        )
        assertEquals("TRAILS", bySlot.getValue(SideButtonSlot.LEFT_MIDDLE).label)
        assertEquals(SideButtonSettings.ACTION_OPEN_NAVIGATION, bySlot.getValue(SideButtonSlot.LEFT_MIDDLE).target)
        assertEquals("navigation", bySlot.getValue(SideButtonSlot.LEFT_MIDDLE).iconId)
    }

    @Test
    fun intentionalCustomRecordingButtonIsNotMigrated() {
        preferences().edit()
            .putString("LEFT_TOP_label", "START TRIP")
            .putString("LEFT_TOP_target", SideButtonSettings.ACTION_START)
            .putString("LEFT_TOP_icon", "play")
            .commit()

        val config = SideButtonSettings(context).read().first { it.slot == SideButtonSlot.LEFT_TOP }

        assertEquals("START TRIP", config.label)
        assertEquals(SideButtonSettings.ACTION_START, config.target)
        assertEquals("play", config.iconId)
    }

    private fun preferences() = context.getSharedPreferences("side_button_settings", Context.MODE_PRIVATE)

    private fun assertPreferredAppOrFallback(
        config: SideButtonConfig,
        expectedLabel: String,
        expectedPackage: String,
        fallbackTarget: String,
        expectedIcon: String,
    ) {
        assertEquals(expectedLabel, config.label)
        val targetMatches = config.target == fallbackTarget ||
            (config.target.startsWith(SideButtonSettings.APP_PREFIX) && config.target.contains(expectedPackage))
        assertEquals(true, targetMatches)
        assertEquals(expectedIcon, config.iconId)
    }
}
