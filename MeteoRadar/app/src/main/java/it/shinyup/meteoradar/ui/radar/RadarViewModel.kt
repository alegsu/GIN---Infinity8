package it.shinyup.meteoradar.ui.radar

import android.location.Location
import androidx.lifecycle.*
import it.shinyup.meteoradar.data.WeatherRepository
import it.shinyup.meteoradar.data.models.HourlyData
import it.shinyup.meteoradar.data.models.OpenMeteoResponse
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
import kotlin.math.cos

class RadarViewModel : ViewModel() {

    private val repository = WeatherRepository()

    private val _forecast = MutableLiveData<Result<OpenMeteoResponse>>()
    val forecast: LiveData<Result<OpenMeteoResponse>> = _forecast

    private val _isLoading = MutableLiveData(false)
    val isLoading: LiveData<Boolean> = _isLoading

    private val _userLocation = MutableLiveData<Location?>()

    private val _radiusKm = MutableLiveData(0)
    val radiusKm: LiveData<Int> = _radiusKm

    fun loadData(location: Location?) {
        _userLocation.value = location
        val lat = location?.latitude ?: 41.9028
        val lon = location?.longitude ?: 12.4964
        val radius = _radiusKm.value ?: 0

        val points = mutableListOf(lat to lon)
        if (radius > 0) {
            val latOff = radius / 111.0
            val lonOff = radius / (111.0 * cos(Math.toRadians(lat)))
            points += listOf(
                (lat + latOff) to lon,
                (lat - latOff) to lon,
                lat to (lon + lonOff),
                lat to (lon - lonOff)
            )
        }

        viewModelScope.launch {
            _isLoading.value = true
            val results = points.map { (la, lo) ->
                async { repository.getForecast(la, lo).getOrNull() }
            }.awaitAll().filterNotNull()

            _forecast.value = when {
                results.isEmpty() -> Result.failure(Exception("Nessun dato"))
                results.size == 1 -> Result.success(results[0])
                else -> Result.success(mergeForecasts(results))
            }
            _isLoading.value = false
        }
    }

    fun setRadius(km: Int) {
        _radiusKm.value = km
        loadData(_userLocation.value)
    }

    private fun mergeForecasts(forecasts: List<OpenMeteoResponse>): OpenMeteoResponse {
        val base = forecasts[0]
        val baseHourly = base.hourly ?: return base
        val n = baseHourly.time.size

        val codes = MutableList(n) { i -> baseHourly.weatherCode.getOrElse(i) { 0 } }
        val precip = MutableList(n) { i -> baseHourly.precipitation.getOrElse(i) { 0.0 } }
        val cape = MutableList(n) { i -> baseHourly.cape.getOrElse(i) { 0.0 } }
        val pp = MutableList(n) { i -> baseHourly.precipitationProbability.getOrElse(i) { 0 } }

        for (f in forecasts.drop(1)) {
            val h = f.hourly ?: continue
            for (i in 0 until n) {
                val c = h.weatherCode.getOrElse(i) { 0 }
                if (c > codes[i]) codes[i] = c
                val pr = h.precipitation.getOrElse(i) { 0.0 }
                if (pr > precip[i]) precip[i] = pr
                val ca = h.cape.getOrElse(i) { 0.0 }
                if (ca > cape[i]) cape[i] = ca
                val p = h.precipitationProbability.getOrElse(i) { 0 }
                if (p > pp[i]) pp[i] = p
            }
        }

        return base.copy(
            hourly = HourlyData(
                time = baseHourly.time,
                weatherCode = codes,
                precipitation = precip,
                cape = cape,
                precipitationProbability = pp
            )
        )
    }
}
