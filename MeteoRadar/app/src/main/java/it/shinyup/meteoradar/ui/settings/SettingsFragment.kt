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
import it.shinyup.meteoradar.BuildConfig
import it.shinyup.meteoradar.MeteoRadarApp
import it.shinyup.meteoradar.R
import it.shinyup.meteoradar.data.models.AlertLevel
import it.shinyup.meteoradar.data.models.AlertType
import it.shinyup.meteoradar.data.models.WeatherAlert
import it.shinyup.meteoradar.utils.NotificationHelper
import it.shinyup.meteoradar.utils.Prefs

class SettingsFragment : PreferenceFragmentCompat() {

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

        findPreference<ListPreference>("app_language")?.setOnPreferenceChangeListener { _, _ ->
            requireActivity().recreate()
            true
        }
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
