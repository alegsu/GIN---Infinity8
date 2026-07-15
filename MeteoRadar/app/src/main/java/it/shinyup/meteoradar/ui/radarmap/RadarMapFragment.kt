package it.shinyup.meteoradar.ui.radarmap

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.google.android.material.chip.Chip
import it.shinyup.meteoradar.databinding.FragmentRadarMapBinding
import it.shinyup.meteoradar.utils.LocationHelper
import kotlinx.coroutines.launch

class RadarMapFragment : Fragment() {

    private var _binding: FragmentRadarMapBinding? = null
    private val binding get() = _binding!!

    private var pageReady = false
    private var selectedProduct = "radar:vmi"

    /** DPC Radar-DPC v2 products. code = GeoServer WMS layer name (radar: workspace). */
    private data class Product(val code: String, val label: String)

    private val products = listOf(
        Product("radar:vmi", "Riflettività"),
        Product("radar:sri", "Pioggia"),
        Product("radar:poh", "Grandine"),
        Product("radar:cum3", "Cum. 3h"),
        Product("radar:cum6", "Cum. 6h"),
        Product("radar:cum24", "Cum. 24h"),
        Product("radar:temp", "Temperatura")
    )

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentRadarMapBinding.inflate(inflater, container, false)
        return binding.root
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        buildProductChips()

        binding.webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            builtInZoomControls = false
        }
        binding.webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                pageReady = true
                binding.radarProgress.visibility = View.GONE
                applyProduct(selectedProduct)
                applyOpacity(binding.opacitySlider.value)
                loadLocation()
            }
        }
        binding.webView.loadUrl("file:///android_asset/radar.html")

        binding.opacitySlider.addOnChangeListener { _, value, _ ->
            applyOpacity(value)
        }

        binding.btnRefreshRadar.setOnClickListener {
            eval("refresh()")
        }
    }

    private fun buildProductChips() {
        products.forEachIndexed { index, product ->
            val chip = Chip(requireContext()).apply {
                text = product.label
                isCheckable = true
                isChecked = index == 0
                setOnClickListener {
                    selectedProduct = product.code
                    applyProduct(product.code)
                }
            }
            binding.chipGroupProduct.addView(chip)
        }
    }

    private fun applyProduct(code: String) {
        if (pageReady) eval("setProduct('$code')")
    }

    private fun applyOpacity(value: Float) {
        if (pageReady) eval("setOpacity($value)")
    }

    private fun loadLocation() {
        val fine = ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION)
        val coarse = ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_COARSE_LOCATION)
        if (fine != PackageManager.PERMISSION_GRANTED && coarse != PackageManager.PERMISSION_GRANTED) return
        loadWithLocation()
    }

    @SuppressLint("MissingPermission")
    private fun loadWithLocation() {
        lifecycleScope.launch {
            val location = LocationHelper.getCurrentLocation(requireContext()) ?: return@launch
            if (pageReady) eval("setMyLocation(${location.latitude}, ${location.longitude}, 8)")
        }
    }

    private fun eval(js: String) {
        _binding?.webView?.evaluateJavascript(js, null)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        binding.webView.destroy()
        _binding = null
    }
}
