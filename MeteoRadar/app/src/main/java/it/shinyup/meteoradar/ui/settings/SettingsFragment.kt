package it.shinyup.meteoradar.ui.settings

import android.os.Bundle
import android.widget.Toast
import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceManager
import it.shinyup.meteoradar.BuildConfig
import it.shinyup.meteoradar.MeteoRadarApp
import it.shinyup.meteoradar.R
import it.shinyup.meteoradar.utils.Prefs

class SettingsFragment : PreferenceFragmentCompat() {

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.preferences, rootKey)

        // Show current version
        findPreference<Preference>("version_info")?.summary =
            "v${BuildConfig.VERSION_NAME} (build ${BuildConfig.VERSION_CODE})"

        // Reschedule worker when interval changes
        findPreference<ListPreference>(Prefs.CHECK_INTERVAL)?.setOnPreferenceChangeListener { _, _ ->
            (requireContext().applicationContext as MeteoRadarApp).scheduleAlertWorker()
            Toast.makeText(requireContext(), "Frequenza aggiornata", Toast.LENGTH_SHORT).show()
            true
        }
    }
}
