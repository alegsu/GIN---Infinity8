package it.shinyup.meteoradar.ui.settings

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.navigation.fragment.findNavController
import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceManager
import it.shinyup.meteoradar.BuildConfig
import it.shinyup.meteoradar.MeteoRadarApp
import it.shinyup.meteoradar.R
import it.shinyup.meteoradar.data.models.AlertLevel
import it.shinyup.meteoradar.data.models.AlertType
import it.shinyup.meteoradar.data.models.WeatherAlert
import it.shinyup.meteoradar.utils.NotificationHelper
import it.shinyup.meteoradar.utils.Prefs

class SettingsFragment : PreferenceFragmentCompat(), android.content.SharedPreferences.OnSharedPreferenceChangeListener {

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) sendTestNotification()
        else Toast.makeText(requireContext(),
            "Abilita le notifiche nelle impostazioni di sistema per ricevere le allerte",
            Toast.LENGTH_LONG).show()
    }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.preferences, rootKey)

        findPreference<Preference>("version_info")?.summary =
            "v${BuildConfig.VERSION_NAME} (build ${BuildConfig.VERSION_CODE})"

        findPreference<ListPreference>(Prefs.CHECK_INTERVAL)?.setOnPreferenceChangeListener { _, _ ->
            (requireContext().applicationContext as MeteoRadarApp).scheduleAlertWorker()
            Toast.makeText(requireContext(), "Frequenza aggiornata", Toast.LENGTH_SHORT).show()
            true
        }

        findPreference<Preference>("test_notification")?.setOnPreferenceClickListener {
            checkAndSendTestNotification()
            true
        }

        findPreference<Preference>("open_help")?.setOnPreferenceClickListener {
            findNavController().navigate(R.id.action_settings_to_help)
            true
        }

        findPreference<ListPreference>("app_language")?.setOnPreferenceChangeListener { _, newValue ->
            // ListPreference uses apply() (async) — commit() before recreate() ensures
            // LocaleHelper.wrap() in attachBaseContext reads the updated value
            PreferenceManager.getDefaultSharedPreferences(requireContext())
                .edit().putString("app_language", newValue as String).commit()
            requireActivity().recreate()
            true
        }

        // Favorite city 1 — search
        findPreference<Preference>("search_favorite_1")?.setOnPreferenceClickListener {
            val dialog = CitySearchDialogFragment.newInstance("favorite_1")
            dialog.show(childFragmentManager, "city_search_1")
            true
        }

        // Favorite city 1 — delete
        findPreference<Preference>("delete_favorite_1")?.setOnPreferenceClickListener {
            PreferenceManager.getDefaultSharedPreferences(requireContext()).edit()
                .remove(Prefs.FAVORITE_1_NAME)
                .remove(Prefs.FAVORITE_1_LAT)
                .remove(Prefs.FAVORITE_1_LON)
                .apply()
            Toast.makeText(requireContext(), getString(R.string.city_deleted), Toast.LENGTH_SHORT).show()
            updateFavoriteSummaries()
            true
        }

        // Favorite city 2 — search
        findPreference<Preference>("search_favorite_2")?.setOnPreferenceClickListener {
            val dialog = CitySearchDialogFragment.newInstance("favorite_2")
            dialog.show(childFragmentManager, "city_search_2")
            true
        }

        // Favorite city 2 — delete
        findPreference<Preference>("delete_favorite_2")?.setOnPreferenceClickListener {
            PreferenceManager.getDefaultSharedPreferences(requireContext()).edit()
                .remove(Prefs.FAVORITE_2_NAME)
                .remove(Prefs.FAVORITE_2_LAT)
                .remove(Prefs.FAVORITE_2_LON)
                .apply()
            Toast.makeText(requireContext(), getString(R.string.city_deleted), Toast.LENGTH_SHORT).show()
            updateFavoriteSummaries()
            true
        }

        updateFavoriteSummaries()
    }

    override fun onResume() {
        super.onResume()
        PreferenceManager.getDefaultSharedPreferences(requireContext())
            .registerOnSharedPreferenceChangeListener(this)
        updateFavoriteSummaries()
    }

    override fun onPause() {
        super.onPause()
        PreferenceManager.getDefaultSharedPreferences(requireContext())
            .unregisterOnSharedPreferenceChangeListener(this)
    }

    override fun onSharedPreferenceChanged(sharedPreferences: android.content.SharedPreferences?, key: String?) {
        if (key in listOf(Prefs.FAVORITE_1_NAME, Prefs.FAVORITE_2_NAME)) {
            updateFavoriteSummaries()
        }
    }

    private fun updateFavoriteSummaries() {
        val prefs = PreferenceManager.getDefaultSharedPreferences(requireContext())
        val name1 = prefs.getString(Prefs.FAVORITE_1_NAME, null)
        val name2 = prefs.getString(Prefs.FAVORITE_2_NAME, null)

        findPreference<Preference>("search_favorite_1")?.summary = if (!name1.isNullOrBlank()) {
            getString(R.string.favorite_set_to, name1)
        } else {
            getString(R.string.pref_favorite_1_summary)
        }
        findPreference<Preference>("delete_favorite_1")?.isVisible = !name1.isNullOrBlank()

        findPreference<Preference>("search_favorite_2")?.summary = if (!name2.isNullOrBlank()) {
            getString(R.string.favorite_set_to, name2)
        } else {
            getString(R.string.pref_favorite_2_summary)
        }
        findPreference<Preference>("delete_favorite_2")?.isVisible = !name2.isNullOrBlank()
    }

    private fun checkAndSendTestNotification() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                requireContext(), Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                return
            }
        }
        sendTestNotification()
    }

    private fun sendTestNotification() {
        val alert = WeatherAlert(
            id        = 9999,
            type      = AlertType.HAIL,
            level     = AlertLevel.WARNING,
            title     = "⛈ Test — Rischio grandine",
            description = "Temporale con possibile grandine previsto tra 2–3h. " +
                "CAPE: 1850 J/kg · LI: −3.2 · Livello zero: 2100 m · " +
                "Raffiche: 68 km/h · Precip: 8.5 mm. (score 5/15) — Notifica di test.",
            latitude  = 0.0,
            longitude = 0.0
        )
        NotificationHelper.sendAlertNotification(requireContext(), alert)
        Toast.makeText(requireContext(), "Notifica di test inviata", Toast.LENGTH_SHORT).show()
    }
}
