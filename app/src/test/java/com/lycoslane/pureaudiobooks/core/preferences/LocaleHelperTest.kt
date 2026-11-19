package com.lycoslane.pureaudiobooks.core.preferences

import android.app.Application
import android.content.Context
import android.content.res.Configuration
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Locale

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class LocaleHelperTest {

    private lateinit var baseContext: Context

    @Before
    fun setUp() {
        baseContext = ApplicationProvider.getApplicationContext()
        baseContext.getSharedPreferences(AppPreferences.PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
    }

    @Test
    fun freshInstallUsesSystemLocaleConsistentlyInLocaleHelperAndPreferences() {
        val deConfig = Configuration(baseContext.resources.configuration).apply {
            setLocale(Locale.GERMAN)
        }
        val deContext = baseContext.createConfigurationContext(deConfig)
        assertEquals("de", LocaleHelper.getEffectiveLanguage(deContext))
        assertEquals("de", AppPreferences(deContext).settings.value.appLanguage)

        val enConfig = Configuration(baseContext.resources.configuration).apply {
            setLocale(Locale.ENGLISH)
        }
        val enContext = baseContext.createConfigurationContext(enConfig)
        assertEquals("en", LocaleHelper.getEffectiveLanguage(enContext))
        assertEquals("en", AppPreferences(enContext).settings.value.appLanguage)
    }

    @Test
    fun explicitLanguagePreferenceOverridesSystemLocale() {
        val deConfig = Configuration(baseContext.resources.configuration).apply {
            setLocale(Locale.GERMAN)
        }
        val deContext = baseContext.createConfigurationContext(deConfig)
        val prefs = AppPreferences(deContext)
        prefs.setAppLanguage("en")

        assertEquals("en", LocaleHelper.getEffectiveLanguage(deContext))
        val wrapped = LocaleHelper.wrapContext(deContext)
        assertEquals("en", wrapped.resources.configuration.locales[0].language)
    }
}
