package com.lycoslane.pureaudiobooks.core.preferences

import android.content.Context
import android.content.res.Configuration
import java.util.Locale

object LocaleHelper {

    fun getEffectiveLanguage(context: Context): String {
        val prefs = context.getSharedPreferences(AppPreferences.PREFS_NAME, Context.MODE_PRIVATE)
        val saved = prefs.getString(AppPreferences.KEY_APP_LANGUAGE, null)
        if (saved == "de" || saved == "en") return saved
        val locales = context.resources.configuration.locales
        val sysLang = if (!locales.isEmpty) locales[0]?.language else Locale.getDefault().language
        return if (sysLang.equals("de", ignoreCase = true)) "de" else "en"
    }

    fun wrapContext(base: Context): Context {
        val lang = getEffectiveLanguage(base)
        val locale = Locale.forLanguageTag(lang)
        Locale.setDefault(locale)
        val config = Configuration(base.resources.configuration).apply {
            setLocale(locale)
            setLayoutDirection(locale)
        }
        return base.createConfigurationContext(config)
    }
}
