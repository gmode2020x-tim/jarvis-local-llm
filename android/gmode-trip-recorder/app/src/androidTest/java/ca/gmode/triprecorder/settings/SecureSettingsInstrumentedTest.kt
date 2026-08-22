package ca.gmode.triprecorder.settings

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SecureSettingsInstrumentedTest {
    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()

    @Before
    fun clearSettings() {
        context.getSharedPreferences("secure_settings", android.content.Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
    }

    @Test
    fun accessTokenIsEncryptedAtRestAndCanBeRestored() {
        val token = "gmode-secret-token-for-encryption-test"
        val settings = SecureSettings(context)

        settings.saveToken(token)

        val raw = context.getSharedPreferences("secure_settings", android.content.Context.MODE_PRIVATE)
        val encrypted = raw.getString("token_data", "").orEmpty()
        val iv = raw.getString("token_iv", "").orEmpty()

        assertTrue(settings.hasToken())
        assertEquals(token, settings.token())
        assertTrue(encrypted.isNotBlank())
        assertTrue(iv.isNotBlank())
        assertNotEquals(token, encrypted)
        assertFalse(raw.all.toString().contains(token))
    }

    @Test
    fun blankTokenDoesNotReplaceSavedTokenAndUrlIsNormalized() {
        val settings = SecureSettings(context)
        settings.baseUrl = "  http://10.0.2.2:18123///  "
        settings.saveToken("saved-token")

        settings.saveToken("   ")

        assertEquals("http://10.0.2.2:18123", settings.baseUrl)
        assertEquals("saved-token", settings.token())
    }
}
