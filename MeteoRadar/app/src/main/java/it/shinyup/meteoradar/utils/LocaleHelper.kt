package it.shinyup.meteoradar.utils

import android.content.Context
import android.content.res.Configuration
import androidx.preference.PreferenceManager
import java.util.Locale

object LocaleHelper {

    private const val KEY = "app_language"

    fun wrap(context: Context): Context {
        val lang = PreferenceManager.getDefaultSharedPreferences(context)
            .getString(KEY, "it") ?: "it"
        val locale = Locale(lang)
        Locale.setDefault(locale)
        val config = Configuration(context.resources.configuration)
        config.setLocale(locale)
        return context.createConfigurationContext(config)
    }
}
