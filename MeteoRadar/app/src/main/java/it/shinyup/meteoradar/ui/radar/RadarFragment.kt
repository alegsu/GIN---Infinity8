package it.shinyup.meteoradar.ui.radar

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.SeekBar
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import it.shinyup.meteoradar.R
import it.shinyup.meteoradar.data.models.RadarFrame
import it.shinyup.meteoradar.data.models.WeatherCode
import it.shinyup.meteoradar.databinding.FragmentRadarBinding
import org.osmdroid.tileprovider.MapTileProviderBasic
import org.osmdroid.tileprovider.tilesource.OnlineTileSourceBase
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.util.MapTileIndex
import org.osmdroid.views.overlay.TilesOverlay
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay
import java.text.SimpleDateFormat
import java.util.*

class RadarFragment : Fragment() {

    private var _binding: FragmentRadarBinding? = null
    private val binding get() = _binding!!

    private val viewModel: RadarViewModel by viewModels()
    private var locationOverlay: MyLocationNewOverlay? = null
    private var radarOverlay: TilesOverlay? = null
    private var allFrames: List<RadarFrame> = emptyList()
    private var radarHost: String = ""

    private val animHandler = Handler(Looper.getMainLooper())
    private var animRunnable: Runnable? = null
    private val animInterval = 500L // ms per frame

    private val timeFormat = SimpleDateFormat("HH:mm", Locale.ITALY)

    private val locationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val granted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                      permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (granted) initLocationAndLoad()
        else loadDataWithDefaultLocation()
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentRadarBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupMap()
        setupButtons()
        setupObservers()
        checkLocationPermission()
    }

    private fun setupMap() {
        binding.mapView.apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            controller.setZoom(7.0)
            controller.setCenter(GeoPoint(41.9028, 12.4964)) // Italia
            minZoomLevel = 4.0
            maxZoomLevel = 18.0
        }
    }

    private fun setupButtons() {
        binding.fabPlayPause.setOnClickListener {
            viewModel.toggleAnimation()
        }

        binding.btnRefresh.setOnClickListener {
            loadCurrentLocation()
        }

        binding.seekBarTimeline.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                if (fromUser && allFrames.isNotEmpty()) {
                    viewModel.stopAnimation()
                    viewModel.setFrameIndex(progress)
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar) {}
            override fun onStopTrackingTouch(seekBar: SeekBar) {}
        })
    }

    private fun setupObservers() {
        viewModel.isLoading.observe(viewLifecycleOwner) { loading ->
            binding.progressBar.visibility = if (loading) View.VISIBLE else View.GONE
        }

        viewModel.radarData.observe(viewLifecycleOwner) { result ->
            result.onSuccess { data ->
                radarHost = data.host
                allFrames = data.radar.past + data.radar.nowcast
                binding.seekBarTimeline.max = (allFrames.size - 1).coerceAtLeast(0)
                binding.seekBarTimeline.progress = allFrames.size - 1
                viewModel.setFrameIndex(allFrames.size - 1)
            }.onFailure {
                Toast.makeText(context, "Errore caricamento radar", Toast.LENGTH_SHORT).show()
            }
        }

        viewModel.forecast.observe(viewLifecycleOwner) { result ->
            result.onSuccess { data ->
                updateWeatherInfo(data)
            }
        }

        viewModel.currentFrameIndex.observe(viewLifecycleOwner) { index ->
            if (allFrames.isNotEmpty() && index in allFrames.indices) {
                updateRadarOverlay(allFrames[index])
                binding.seekBarTimeline.progress = index
                val time = Date(allFrames[index].time * 1000L)
                val nowcastStart = allFrames.indexOfFirst { it.time > System.currentTimeMillis() / 1000 }
                val label = if (nowcastStart > 0 && index >= nowcastStart)
                    "Previsione ${timeFormat.format(time)}"
                else
                    timeFormat.format(time)
                binding.tvFrameTime.text = label
            }
        }

        viewModel.isAnimating.observe(viewLifecycleOwner) { animating ->
            if (animating) {
                binding.fabPlayPause.setImageResource(android.R.drawable.ic_media_pause)
                startAnimation()
            } else {
                binding.fabPlayPause.setImageResource(android.R.drawable.ic_media_play)
                stopAnimation()
            }
        }
    }

    private fun updateWeatherInfo(data: it.shinyup.meteoradar.data.models.OpenMeteoResponse) {
        val code = data.currentWeather?.weathercode ?: 0
        val temp = data.currentWeather?.temperature ?: 0.0

        binding.tvWeatherDesc.text = WeatherCode.description(code)
        binding.tvTemperature.text = "${temp.toInt()}°C"

        // Hail risk from next 6h
        val cape = data.hourly?.cape?.take(6)?.maxOrNull() ?: 0.0
        val hasHailCode = data.hourly?.weatherCode?.take(6)?.any { WeatherCode.hasHail(it) } ?: false

        val hailRisk = when {
            WeatherCode.isHeavyHail(code) || data.hourly?.weatherCode?.take(6)?.any { WeatherCode.isHeavyHail(it) } == true ->
                "ALTO" to Color.parseColor("#F44336")
            hasHailCode || cape > 2000 ->
                "MODERATO" to Color.parseColor("#FF9800")
            cape > 500 ->
                "POSSIBILE" to Color.parseColor("#FFC107")
            else ->
                "BASSO" to Color.parseColor("#4CAF50")
        }

        binding.tvHailRisk.text = "Rischio grandine: ${hailRisk.first}"
        binding.tvHailRisk.setTextColor(hailRisk.second)
        binding.cardWeatherInfo.visibility = View.VISIBLE
    }

    private fun updateRadarOverlay(frame: RadarFrame) {
        val map = binding.mapView
        radarOverlay?.let { map.overlays.remove(it) }

        val host = radarHost.trimEnd('/')
        val tileSource = object : OnlineTileSourceBase(
            "Radar_${frame.time}", 0, 18, 256, ".png", host
        ) {
            override fun getTileURLString(pMapTileIndex: Long): String {
                val z = MapTileIndex.getZoom(pMapTileIndex)
                val x = MapTileIndex.getX(pMapTileIndex)
                val y = MapTileIndex.getY(pMapTileIndex)
                return "$host${frame.path}/256/$z/$x/$y/2/1_1.png"
            }
        }

        radarOverlay = TilesOverlay(
            MapTileProviderBasic(requireContext().applicationContext, tileSource),
            requireContext()
        )

        map.overlays.add(radarOverlay)
        locationOverlay?.let {
            map.overlays.remove(it)
            map.overlays.add(it)
        }
        map.invalidate()
    }

    private fun startAnimation() {
        stopAnimation()
        animRunnable = object : Runnable {
            override fun run() {
                if (allFrames.isEmpty()) return
                val current = viewModel.currentFrameIndex.value ?: 0
                val next = (current + 1) % allFrames.size
                viewModel.setFrameIndex(next)
                animHandler.postDelayed(this, animInterval)
            }
        }
        animHandler.post(animRunnable!!)
    }

    private fun stopAnimation() {
        animRunnable?.let { animHandler.removeCallbacks(it) }
        animRunnable = null
    }

    private fun checkLocationPermission() {
        val fine = ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION)
        val coarse = ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_COARSE_LOCATION)
        if (fine == PackageManager.PERMISSION_GRANTED || coarse == PackageManager.PERMISSION_GRANTED) {
            initLocationAndLoad()
        } else {
            locationPermissionLauncher.launch(arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ))
        }
    }

    @SuppressLint("MissingPermission")
    private fun initLocationAndLoad() {
        locationOverlay = MyLocationNewOverlay(GpsMyLocationProvider(requireContext()), binding.mapView).apply {
            enableMyLocation()
            enableFollowLocation()
        }
        binding.mapView.overlays.add(locationOverlay)
        loadCurrentLocation()
    }

    private fun loadCurrentLocation() {
        binding.progressBar.visibility = View.VISIBLE
        viewModel.loadData(locationOverlay?.myLocation?.let {
            android.location.Location("").apply {
                latitude = it.latitude
                longitude = it.longitude
            }
        })
    }

    private fun loadDataWithDefaultLocation() {
        viewModel.loadData(null)
    }

    override fun onResume() {
        super.onResume()
        binding.mapView.onResume()
    }

    override fun onPause() {
        super.onPause()
        binding.mapView.onPause()
        viewModel.stopAnimation()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        stopAnimation()
        _binding = null
    }
}
