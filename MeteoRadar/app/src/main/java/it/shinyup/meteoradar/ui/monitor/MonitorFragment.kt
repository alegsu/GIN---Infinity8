package it.shinyup.meteoradar.ui.monitor

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.chip.Chip
import it.shinyup.meteoradar.R
import it.shinyup.meteoradar.databinding.FragmentMonitorBinding
import it.shinyup.meteoradar.utils.Prefs

class MonitorFragment : Fragment() {

    private var _binding: FragmentMonitorBinding? = null
    private val binding get() = _binding!!

    private val viewModel: MonitorViewModel by viewModels()
    private val adapter = TemperatureChangeAdapter()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentMonitorBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.rvChanges.layoutManager = LinearLayoutManager(requireContext())
        binding.rvChanges.adapter = adapter

        val prefs = PreferenceManager.getDefaultSharedPreferences(requireContext())
        val notifEnabled = prefs.getBoolean(Prefs.NOTIFICATIONS_ENABLED, true) &&
                           prefs.getBoolean(Prefs.FORECAST_CHANGE_NOTIFICATIONS, true)
        val threshold = prefs.getString(Prefs.FORECAST_CHANGE_THRESHOLD, "1.0") ?: "1.0"

        binding.tvNotifStatus.text = if (notifEnabled) {
            getString(R.string.monitor_notif_on, threshold)
        } else {
            getString(R.string.monitor_notif_off)
        }

        viewModel.isLoading.observe(viewLifecycleOwner) { loading ->
            binding.progressBar.visibility = if (loading) View.VISIBLE else View.GONE
        }

        viewModel.cities.observe(viewLifecycleOwner) { cities ->
            binding.chipGroupCity.removeAllViews()

            val allChip = Chip(requireContext()).apply {
                text = "Tutte"
                isCheckable = true
                isChecked = true
                setOnClickListener { viewModel.selectCity(null) }
            }
            binding.chipGroupCity.addView(allChip)

            for (city in cities) {
                val chip = Chip(requireContext()).apply {
                    text = city
                    isCheckable = true
                    setOnClickListener { viewModel.selectCity(city) }
                }
                binding.chipGroupCity.addView(chip)
            }
        }

        viewModel.changes.observe(viewLifecycleOwner) { changes ->
            adapter.submitList(changes)
            binding.tvEmpty.visibility = if (changes.isEmpty()) View.VISIBLE else View.GONE
            binding.rvChanges.visibility = if (changes.isEmpty()) View.GONE else View.VISIBLE
        }

        viewModel.loadChanges()
    }

    override fun onResume() {
        super.onResume()
        viewModel.loadChanges()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
