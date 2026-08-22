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
    fun legacyDecorativeDefaultsMigrateToMatchingPhoneActions() {
        preferences().edit()
            .putString("LEFT_TOP_target", SideButtonSettings.ACTION_START)
            .putString("LEFT_MIDDLE_target", SideButtonSettings.ACTION_TRIP_TYPE)
            .putString("RIGHT_TOP_target", SideButtonSettings.ACTION_STOP)
            .commit()

        val bySlot = SideButtonSettings(context).read().associateBy { it.slot }

        assertEquals(SideButtonSettings.ACTION_OPEN_RADIO, bySlot.getValue(SideButtonSlot.LEFT_TOP).target)
        assertEquals(SideButtonSettings.ACTION_OPEN_NAVIGATION, bySlot.getValue(SideButtonSlot.LEFT_MIDDLE).target)
        assertEquals(SideButtonSettings.ACTION_OPEN_PHONE, bySlot.getValue(SideButtonSlot.RIGHT_TOP).target)
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
}
