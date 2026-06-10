package it.shinyup.meteoradar.ui.radar

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.graphics.Color
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
import it.shinyup.meteoradar.R
import it.shinyup.meteoradar.data.models.HourlyData
import it.shinyup.meteoradar.data.models.OpenMeteoResponse
import it.shinyup.meteoradar.data.models.WeatherCode
import it.shinyup.meteoradar.databinding.FragmentRadarBinding
import it.shinyup.meteoradar.utils.LocationHelper
import kotlinx.coroutines.launch

class RadarFragment : Fragment() {

    private var _binding: FragmentRadarBinding? = null
    private val binding get() = _binding!!

    private val viewModel: RadarViewModel by viewModels()
    private val adapter = ForecastHourAdapter()

    private val locationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val granted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                      permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (granted) loadWithLocation() else viewModel.loadData(null)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentRadarBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.rvForecast.layoutManager = LinearLayoutManager(requireContext())
        binding.rvForecast.adapter = adapter

        binding.btnRefresh.setOnClickListener { checkLocationAndLoad() }

        binding.radiusChipGroup.setOnCheckedStateChangeListener { _, checkedIds ->
            val km = when (checkedIds.firstOrNull()) {
                R.id.chipRadius25 -> 25
                R.id.chipRadius50 -> 50
                R.id.chipRadius100 -> 100
                else -> 0
            }
            viewModel.setRadius(km)
        }

        viewModel.isLoading.observe(viewLifecycleOwner) { loading ->
            binding.progressBar.visibility = if (loading) View.VISIBLE else View.GONE
        }

        viewModel.forecast.observe(viewLifecycleOwner) { result ->
            result.onSuccess { data ->
                updateCurrentConditions(data)
                updateForecastList(data.hourly)
            }.onFailure {
                Toast.makeText(context, "Errore caricamento dati", Toast.LENGTH_SHORT).show()
            }
        }

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

    private fun updateCurrentConditions(data: OpenMeteoResponse) {
        val code = data.currentWeather?.weathercode ?: 0
        val temp = data.currentWeather?.temperature ?: 0.0

        binding.tvWeatherDesc.text = WeatherCode.description(code)
        binding.tvTemperature.text = "${temp.toInt()}°C"

        val cape = data.hourly?.cape?.take(3)?.maxOrNull() ?: 0.0
        val hasHailCode = data.hourly?.weatherCode?.take(6)?.any { WeatherCode.hasHail(it) } == true

        val (label, color) = when {
            WeatherCode.isHeavyHail(code) ||
            data.hourly?.weatherCode?.take(6)?.any { WeatherCode.isHeavyHail(it) } == true ->
                "ALTO" to Color.parseColor("#F44336")
            hasHailCode || cape > 2000 -> "MODERATO" to Color.parseColor("#FF9800")
            cape > 500 -> "POSSIBILE" to Color.parseColor("#FFC107")
            else -> "BASSO" to Color.parseColor("#4CAF50")
        }

        binding.tvHailRisk.text = "Rischio grandine: $label"
        binding.tvHailRisk.setTextColor(color)
        binding.cardWeatherInfo.visibility = View.VISIBLE
    }

    private fun updateForecastList(hourly: HourlyData?) {
        if (hourly == null) return

        val items = hourly.time.indices.map { i ->
            val timeStr = hourly.time[i].substringAfter("T").take(5)
            val code = hourly.weatherCode.getOrElse(i) { 0 }
            val precip = hourly.precipitation.getOrElse(i) { 0.0 }
            val precipProb = hourly.precipitationProbability.getOrElse(i) { 0 }
            val cape = hourly.cape.getOrElse(i) { 0.0 }
            val (label, color) = hourSeverity(code, cape, precipProb)
            HourForecastItem(timeStr, WeatherCode.description(code), precip, precipProb, cape, label, color)
        }

        adapter.submitList(items)
        binding.cardForecast.visibility = View.VISIBLE

        val radius = viewModel.radiusKm.value ?: 0
        binding.tvForecastTitle.text = if (radius > 0)
            "Prossime 24 ore (peggiore entro $radius km)" else "Prossime 24 ore"
    }

    private fun hourSeverity(code: Int, cape: Double, precipProb: Int): Pair<String, Int> = when {
        WeatherCode.isHeavyHail(code)  -> "ESTREMO"  to Color.parseColor("#9C27B0")
        WeatherCode.hasHail(code)      -> "PERICOLO" to Color.parseColor("#F44336")
        WeatherCode.isThunderstorm(code) -> "MODERATO" to Color.parseColor("#FF9800")
        cape > 2000                    -> "MODERATO" to Color.parseColor("#FF9800")
        cape > 500 && precipProb > 40  -> "POSSIBILE" to Color.parseColor("#FFC107")
        else                           -> "BASSO"    to Color.parseColor("#4CAF50")
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
