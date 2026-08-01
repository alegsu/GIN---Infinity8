package it.shinyup.meteoradar.ui.wind

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.chip.Chip
import it.shinyup.meteoradar.R
import it.shinyup.meteoradar.databinding.FragmentWindBinding
import it.shinyup.meteoradar.utils.HumidexUtil
import it.shinyup.meteoradar.utils.LocationHelper
import kotlinx.coroutines.launch

class WindFragment : Fragment() {

    private var _binding: FragmentWindBinding? = null
    private val binding get() = _binding!!

    private val viewModel: WindViewModel by viewModels()
    private val adapter = WindHourAdapter()
    private val humidexAdapter = HumidexHourAdapter()
    private var humidexMode = false

    private val levelLabels = intArrayOf(
        R.string.humidex_lvl_0, R.string.humidex_lvl_1, R.string.humidex_lvl_2,
        R.string.humidex_lvl_3, R.string.humidex_lvl_4
    )

    private val locationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val granted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                      permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (granted) loadWithLocation() else viewModel.loadData(null)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentWindBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.rvWind.layoutManager = LinearLayoutManager(requireContext())
        binding.rvWind.adapter = adapter
        binding.rvHumidex.layoutManager = LinearLayoutManager(requireContext())
        binding.rvHumidex.adapter = humidexAdapter

        binding.toggleMode.check(R.id.btnModeWind)
        binding.toggleMode.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener
            humidexMode = checkedId == R.id.btnModeHumidex
            binding.windSection.visibility = if (humidexMode) View.GONE else View.VISIBLE
            binding.humidexSection.visibility = if (humidexMode) View.VISIBLE else View.GONE
            viewModel.selectedDay.value?.let { updateDayView(it) }
        }

        viewModel.isLoading.observe(viewLifecycleOwner) { loading ->
            binding.progressBar.visibility = if (loading) View.VISIBLE else View.GONE
        }

        viewModel.locationName.observe(viewLifecycleOwner) { city ->
            binding.tvWindLocation.text = "📍 $city"
        }

        viewModel.days.observe(viewLifecycleOwner) { days ->
            binding.chipGroupDay.removeAllViews()
            days.forEachIndexed { index, day ->
                val chip = Chip(requireContext()).apply {
                    text = viewModel.formatDayLabel(day)
                    isCheckable = true
                    isChecked = index == 0
                    tag = day
                    setOnClickListener { viewModel.selectDay(day) }
                }
                binding.chipGroupDay.addView(chip)
            }
        }

        viewModel.selectedDay.observe(viewLifecycleOwner) { day ->
            updateDayView(day)
        }

        viewModel.windData.observe(viewLifecycleOwner) { result ->
            result.onSuccess {
                val day = viewModel.selectedDay.value
                if (day != null) updateDayView(day)
            }.onFailure {
                Toast.makeText(context, "Errore caricamento dati vento", Toast.LENGTH_SHORT).show()
            }
        }

        checkLocationAndLoad()
    }

    private fun updateDayView(day: String) {
        // Wind section
        val hours = viewModel.getHoursForDay(day)
        adapter.submitList(hours)
        val summary = viewModel.getSummaryForDay(day)
        if (summary != null) {
            binding.tvSummaryTitle.text = getString(R.string.wind_summary_title, viewModel.formatDayLabel(day))
            binding.tvAvgWind.text = "${summary.avgWind} km/h"
            binding.tvMaxGust.text = "${summary.maxGust} km/h"
            binding.tvTempRange.text = "${summary.tempMin}°/${summary.tempMax}°"
            binding.cardSummary.visibility = View.VISIBLE
            binding.layoutWindScale.visibility = View.VISIBLE
            binding.layoutHeader.visibility = View.VISIBLE
            binding.cardWindList.visibility = View.VISIBLE
        }

        // Humidex section
        val hxHours = viewModel.getHumidexHoursForDay(day)
        humidexAdapter.submitList(hxHours)
        val hx = viewModel.getHumidexSummaryForDay(day)
        if (hx != null) {
            val color = HumidexUtil.color(hx.maxHumidex.toDouble())
            binding.tvHxSummaryTitle.text = getString(R.string.humidex_summary_title, viewModel.formatDayLabel(day))
            binding.tvHxMax.text = "${hx.maxHumidex}"
            binding.tvHxMax.setTextColor(color)
            binding.tvHxLevel.text = getString(levelLabels[hx.levelIndex])
            binding.tvHxLevel.setTextColor(color)
            binding.tvHxAvgHum.text = "${hx.avgHumidity}%"
        }

        for (i in 0 until binding.chipGroupDay.childCount) {
            val chip = binding.chipGroupDay.getChildAt(i) as? Chip
            chip?.isChecked = chip?.tag == day
        }

        val rv = if (humidexMode) binding.rvHumidex else binding.rvWind
        val currentPos = if (humidexMode) humidexAdapter.currentHourPosition() else adapter.currentHourPosition()
        if (currentPos >= 0) {
            rv.post {
                (rv.layoutManager as? LinearLayoutManager)?.scrollToPositionWithOffset(currentPos, 0)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        checkLocationAndLoad()
    }

    private fun checkLocationAndLoad() {
        val fine = ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION)
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
            viewModel.loadData(location)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
