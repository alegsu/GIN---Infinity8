package it.shinyup.meteoradar.ui.daily

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
import it.shinyup.meteoradar.databinding.FragmentDailyBinding
import it.shinyup.meteoradar.utils.LocationHelper
import kotlinx.coroutines.launch

class DailyFragment : Fragment() {

    private var _binding: FragmentDailyBinding? = null
    private val binding get() = _binding!!

    private val viewModel: DailyViewModel by viewModels()
    private val adapter = DailyForecastAdapter()

    private val locationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val granted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                      permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (granted) loadWithLocation() else viewModel.loadData(null)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentDailyBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.rvDaily.layoutManager = LinearLayoutManager(requireContext())
        binding.rvDaily.adapter = adapter

        binding.swipeRefresh.setOnRefreshListener { checkLocationAndLoad() }

        viewModel.isLoading.observe(viewLifecycleOwner) { loading ->
            binding.swipeRefresh.isRefreshing = loading
        }

        viewModel.locationName.observe(viewLifecycleOwner) { city ->
            binding.tvLocationName.text = "📍 $city"
        }

        viewModel.days.observe(viewLifecycleOwner) { result ->
            result.onSuccess { items ->
                adapter.submitList(items)
                binding.cardDaily.visibility = View.VISIBLE
                if (items.isNotEmpty()) {
                    binding.tvAvgSummary.text =
                        "Media 7 giorni: max ${items[0].avgMax.toInt()}° · min ${items[0].avgMin.toInt()}°"
                }
            }.onFailure {
                Toast.makeText(context, "Errore caricamento dati", Toast.LENGTH_SHORT).show()
            }
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
            viewModel.loadData(location)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
