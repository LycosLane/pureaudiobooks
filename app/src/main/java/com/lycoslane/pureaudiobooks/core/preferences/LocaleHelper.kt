package com.lycoslane.pureaudiobooks.core.preferences

import android.content.Context
import android.content.res.Configuration
import java.util.Locale

object LocaleHelper {
    fun wrapContext(base: Context): Context {
        val prefs = base.getSharedPreferences(AppPreferences.PREFS_NAME, Context.MODE_PRIVATE)
        val lang = prefs.getString(AppPreferences.KEY_APP_LANGUAGE, null) ?: return base
        val locale = Locale(lang)
        Locale.setDefault(locale)
        val config = Configuration(base.resources.configuration)
        config.setLocale(locale)
        return base.createConfigurationContext(config)
    }
}
