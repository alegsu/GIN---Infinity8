package it.shinyup.meteoradar.ui.analysis

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.chip.Chip
import androidx.preference.PreferenceManager
import it.shinyup.meteoradar.R
import it.shinyup.meteoradar.databinding.FragmentAnalysisBinding
import it.shinyup.meteoradar.utils.LocationHelper
import it.shinyup.meteoradar.utils.Prefs
import kotlinx.coroutines.launch

class AnalysisFragment : Fragment() {

    private var _binding: FragmentAnalysisBinding? = null
    private val binding get() = _binding!!
    private val viewModel: AnalysisViewModel by viewModels()
    private val pastAdapter = PastWeatherAdapter()

    private val locationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val granted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                      permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (granted) loadWithLocation() else viewModel.loadPastDays(null)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentAnalysisBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.rvPastWeather.layoutManager = LinearLayoutManager(requireContext())
        binding.rvPastWeather.adapter = pastAdapter

        binding.toggleGroup.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener
            val showPast = checkedId == R.id.btnPastWeather
            binding.rvPastWeather.visibility = if (showPast) View.VISIBLE else View.GONE
            binding.layoutEvolution.visibility = if (showPast) View.GONE else View.VISIBLE
            if (!showPast) viewModel.loadEvolution()
        }
        binding.btnEvolution.isChecked = true

        viewModel.isLoading.observe(viewLifecycleOwner) { loading ->
            binding.progressBar.visibility = if (loading) View.VISIBLE else View.GONE
        }

        viewModel.pastDays.observe(viewLifecycleOwner) { items ->
            pastAdapter.submitList(items)
        }

        viewModel.evolution.observe(viewLifecycleOwner) { state ->
            updateEvolutionUI(state)
        }

        checkLocationAndLoad()
    }

    override fun onResume() {
        super.onResume()
        checkLocationAndLoad()
    }

    private fun checkLocationAndLoad() {
        val fine   = ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION)
        val coarse = ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_COARSE_LOCATION)
        if (fine == PackageManager.PERMISSION_GRANTED || coarse == PackageManager.PERMISSION_GRANTED) {
            loadWithLocation()
        } else {
            locationPermissionLauncher.launch(arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ))
        }
    }

    @SuppressLint("MissingPermission")
    private fun loadWithLocation() {
        lifecycleScope.launch {
            val location = LocationHelper.getCurrentLocation(requireContext())
            viewModel.loadPastDays(location)
        }
    }

    private fun updateEvolutionUI(state: EvolutionState) {
        // City chips
        if (state.availableCities.size > 1) {
            binding.cityChipGroup.removeAllViews()
            state.availableCities.forEach { city ->
                val chip = Chip(requireContext()).apply {
                    text = city
                    isCheckable = true
                    isChecked = city == state.selectedCity
                    setOnClickListener { viewModel.selectCity(city) }
                }
                binding.cityChipGroup.addView(chip)
            }
            binding.cityChipGroup.visibility = View.VISIBLE
        } else {
            binding.cityChipGroup.visibility = View.GONE
        }

        // Date chips
        binding.dateChipGroup.removeAllViews()
        state.availableDates.forEach { date ->
            val chip = Chip(requireContext()).apply {
                text = formatShortDate(date)
                isCheckable = true
                isChecked = date == state.selectedDate
                setOnClickListener { viewModel.selectDate(date) }
            }
            binding.dateChipGroup.addView(chip)
        }

        if (state.availableDates.isEmpty()) {
            binding.tvEvolutionEmpty.visibility = View.VISIBLE
            binding.tvEvolutionEmpty.text = getString(R.string.evolution_no_data)
            binding.tvSelectedDateLabel.visibility = View.GONE
            binding.chartView.visibility = View.GONE
            binding.tvChartLegend.visibility = View.GONE
            return
        }

        binding.tvEvolutionEmpty.visibility = if (!state.hasEnoughData) View.VISIBLE else View.GONE
        binding.tvEvolutionEmpty.text = getString(R.string.evolution_need_more)
        binding.tvSelectedDateLabel.visibility = View.VISIBLE
        binding.tvSelectedDateLabel.text = getString(R.string.evolution_selected_date, state.dateLabel)
        binding.chartView.visibility = if (state.hasEnoughData) View.VISIBLE else View.GONE
        binding.tvChartLegend.visibility = if (state.hasEnoughData) View.VISIBLE else View.GONE
        binding.tvModelSources.visibility = if (state.hasEnoughData) View.VISIBLE else View.GONE

        if (state.hasEnoughData) {
            binding.chartView.setScale(state.globalScale)
            binding.chartView.setData(state.points)

            val prefs = PreferenceManager.getDefaultSharedPreferences(requireContext())
            val showApparent = prefs.getBoolean(Prefs.SHOW_APPARENT_TEMP, false)
            val showWind = prefs.getBoolean(Prefs.SHOW_WIND, false)
            val showHumidity = prefs.getBoolean(Prefs.SHOW_HUMIDITY, false)
            binding.chartView.setOverlays(showApparent, showWind, showHumidity)

            binding.legendApparent.visibility = if (showApparent) View.VISIBLE else View.GONE
            binding.legendWind.visibility = if (showWind) View.VISIBLE else View.GONE
            binding.legendHumidity.visibility = if (showHumidity) View.VISIBLE else View.GONE
        }
    }

    private fun formatShortDate(iso: String): String = try {
        val d = java.time.LocalDate.parse(iso)
        val dow = d.dayOfWeek.getDisplayName(
            java.time.format.TextStyle.SHORT, java.util.Locale.getDefault()
        ).replaceFirstChar { it.uppercase() }
        "$dow ${d.dayOfMonth}"
    } catch (e: Exception) { iso }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
