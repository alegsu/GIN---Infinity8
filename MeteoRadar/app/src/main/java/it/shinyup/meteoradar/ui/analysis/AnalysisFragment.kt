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
import it.shinyup.meteoradar.R
import it.shinyup.meteoradar.databinding.FragmentAnalysisBinding
import it.shinyup.meteoradar.utils.LocationHelper
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

        // Toggle between the two modes
        binding.toggleGroup.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener
            val showPast = checkedId == R.id.btnPastWeather
            binding.rvPastWeather.visibility = if (showPast) View.VISIBLE else View.GONE
            binding.layoutEvolution.visibility = if (showPast) View.GONE else View.VISIBLE
            if (!showPast) viewModel.loadEvolution()
        }
        binding.btnPastWeather.isChecked = true

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
        // Rebuild date chip group
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
            binding.tvEvolutionEmpty.text = "Nessun dato disponibile.\nApri il tab \"7 Giorni\" per iniziare a raccogliere le previsioni.\nLo storico si costruisce con una rilevazione ogni 4 ore."
            binding.tvSelectedDateLabel.visibility = View.GONE
            binding.chartView.visibility = View.GONE
            binding.tvChartLegend.visibility = View.GONE
            return
        }

        binding.tvEvolutionEmpty.visibility = if (!state.hasEnoughData) View.VISIBLE else View.GONE
        binding.tvEvolutionEmpty.text = "Serve almeno una seconda rilevazione (ogni 4 ore) per mostrare il grafico."
        binding.tvSelectedDateLabel.visibility = View.VISIBLE
        binding.tvSelectedDateLabel.text = "Previsioni per: ${state.dateLabel}"
        binding.chartView.visibility = if (state.hasEnoughData) View.VISIBLE else View.GONE
        binding.tvChartLegend.visibility = if (state.hasEnoughData) View.VISIBLE else View.GONE

        if (state.hasEnoughData) {
            binding.chartView.setData(state.points)
        }
    }

    private fun formatShortDate(iso: String): String = try {
        val d = java.time.LocalDate.parse(iso)
        val dow = when (d.dayOfWeek.value) {
            1 -> "Lun"; 2 -> "Mar"; 3 -> "Mer"; 4 -> "Gio"
            5 -> "Ven"; 6 -> "Sab"; else -> "Dom"
        }
        "$dow ${d.dayOfMonth}"
    } catch (e: Exception) { iso }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
