package it.shinyup.meteoradar.ui.radar

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.content.res.ColorStateList
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
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.LinearLayoutManager
import it.shinyup.meteoradar.R
import it.shinyup.meteoradar.data.models.HourlyData
import it.shinyup.meteoradar.data.models.OpenMeteoResponse
import it.shinyup.meteoradar.data.models.WeatherCode
import it.shinyup.meteoradar.databinding.FragmentRadarBinding
import it.shinyup.meteoradar.utils.LocationHelper
import kotlin.math.roundToInt
import it.shinyup.meteoradar.utils.Prefs
import kotlinx.coroutines.launch

class RadarFragment : Fragment() {

    private var _binding: FragmentRadarBinding? = null
    private val binding get() = _binding!!

    private val viewModel: RadarViewModel by viewModels()
    private val adapter = ForecastHourAdapter()
    private var lastLocation: android.location.Location? = null

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

        binding.btnRefresh.setOnClickListener { viewModel.loadData(lastLocation, forceRefresh = true) }

        viewModel.isLoading.observe(viewLifecycleOwner) { loading ->
            binding.progressBar.visibility = if (loading) View.VISIBLE else View.GONE
        }

        viewModel.locationName.observe(viewLifecycleOwner) { city ->
            binding.tvLocationName.text = "📍 $city"
        }

        viewModel.forecast.observe(viewLifecycleOwner) { result ->
            result.onSuccess { data ->
                updateCurrentConditions(data)
                updateForecastList(data.hourly)
            }.onFailure {
                Toast.makeText(context, "Errore aggiornamento dati", Toast.LENGTH_SHORT).show()
                // Previous data stays visible — do not clear cards
            }
        }

        checkLocationAndLoad()
    }

    override fun onResume() {
        super.onResume()
        // Refresh settings-dependent UI and reload data on every resume
        val prefs = PreferenceManager.getDefaultSharedPreferences(requireContext())
        adapter.showTechDetails = prefs.getBoolean(Prefs.SHOW_TECH_DETAILS, false)
        adapter.notifyDataSetChanged()
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
            lastLocation = location
            viewModel.loadData(location)
        }
    }

    private fun nowStartIndex(times: List<String>): Int {
        val now = java.time.LocalDateTime.now().minusMinutes(30)
        val idx = times.indexOfFirst {
            try { java.time.LocalDateTime.parse(it).isAfter(now) } catch (e: Exception) { false }
        }
        return if (idx == -1) 0 else idx
    }

    private fun updateCurrentConditions(data: OpenMeteoResponse) {
        val code = data.currentWeather?.weathercode ?: 0
        val temp = data.currentWeather?.temperature ?: 0.0

        binding.tvWeatherEmoji.text = WeatherCode.emoji(code)
        binding.tvWeatherDesc.text  = WeatherCode.description(code)
        binding.tvTemperature.text  = "${temp.roundToInt()}°C"

        val hourly = data.hourly
        val maxScore = if (hourly != null) {
            val start = nowStartIndex(hourly.time)
            (start until minOf(start + 6, hourly.time.size)).maxOfOrNull { i ->
                WeatherCode.computeSeverityScore(
                    code          = hourly.weatherCode.getOrElse(i) { 0 },
                    cape          = hourly.cape.getOrElse(i) { 0.0 },
                    liftedIndex   = hourly.liftedIndex?.getOrElse(i) { 0.0 } ?: 0.0,
                    windGusts     = hourly.windGusts?.getOrElse(i) { 0.0 } ?: 0.0,
                    freezingLevel = hourly.freezingLevelHeight?.getOrElse(i) { 3000.0 } ?: 3000.0,
                    precip        = hourly.precipitation.getOrElse(i) { 0.0 },
                    showers       = hourly.showers?.getOrElse(i) { 0.0 } ?: 0.0,
                    precipProb    = hourly.precipitationProbability.getOrElse(i) { 0 }
                )
            } ?: 0
        } else 0

        val (label, color) = scoreToDisplay(maxScore)
        binding.tvHailRisk.text = label
        binding.tvHailRisk.setTextColor(color)
        binding.tvScore6h.text = "$maxScore/15"
        binding.scoreBar6h.progress = maxScore
        binding.scoreBar6h.progressTintList = ColorStateList.valueOf(color)
        binding.cardWeatherInfo.visibility = View.VISIBLE
    }

    private fun updateForecastList(hourly: HourlyData?) {
        if (hourly == null) return

        val prefs = PreferenceManager.getDefaultSharedPreferences(requireContext())
        adapter.showTechDetails = prefs.getBoolean(Prefs.SHOW_TECH_DETAILS, false)

        val start = nowStartIndex(hourly.time)
        val items = (start until hourly.time.size).map { i ->
            val code       = hourly.weatherCode.getOrElse(i) { 0 }
            val precip     = hourly.precipitation.getOrElse(i) { 0.0 }
            val precipProb = hourly.precipitationProbability.getOrElse(i) { 0 }
            val cape       = hourly.cape.getOrElse(i) { 0.0 }
            val li         = hourly.liftedIndex?.getOrElse(i) { 0.0 } ?: 0.0
            val gusts      = hourly.windGusts?.getOrElse(i) { 0.0 } ?: 0.0
            val fz         = hourly.freezingLevelHeight?.getOrElse(i) { 3000.0 } ?: 3000.0
            val showers    = hourly.showers?.getOrElse(i) { 0.0 } ?: 0.0
            val timeStr    = hourly.time[i].substringAfter("T").take(5)

            val score = WeatherCode.computeSeverityScore(code, cape, li, gusts, fz, precip, showers, precipProb)
            val (label, color) = scoreToDisplay(score)
            HourForecastItem(timeStr, WeatherCode.emoji(code), WeatherCode.description(code),
                precip, precipProb, cape, li, gusts, fz, score, label, color)
        }

        adapter.submitList(items)
        binding.cardForecast.visibility = View.VISIBLE

        val radius = prefs.getString(Prefs.RADIUS_KM, "0")?.toIntOrNull() ?: 0
        binding.tvForecastTitle.text = if (radius > 0)
            getString(R.string.forecast_title_radius, items.size, radius)
        else
            getString(R.string.forecast_title, items.size)
    }

    private fun scoreToDisplay(score: Int): Pair<String, Int> = when {
        score >= 7 -> getString(R.string.risk_extreme)  to Color.parseColor("#9C27B0")
        score >= 5 -> getString(R.string.risk_danger)   to Color.parseColor("#F44336")
        score >= 3 -> getString(R.string.risk_moderate)  to Color.parseColor("#FF9800")
        score >= 1 -> getString(R.string.risk_possible)  to Color.parseColor("#FFC107")
        else       -> getString(R.string.risk_low)       to Color.parseColor("#4CAF50")
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
