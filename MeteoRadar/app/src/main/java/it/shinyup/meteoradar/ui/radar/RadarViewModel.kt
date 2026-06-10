package it.shinyup.meteoradar.ui.radar

import android.app.Application
import android.content.Context
import android.location.Location
import androidx.lifecycle.*
import it.shinyup.meteoradar.data.WeatherRepository
import it.shinyup.meteoradar.data.models.OpenMeteoResponse
import it.shinyup.meteoradar.utils.Prefs
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
import kotlin.math.cos

class RadarViewModel(application: Application) : AndroidViewModel(application) {

    private val prefs = application.getSharedPreferences(Prefs.NAME, Context.MODE_PRIVATE)
    private val repository = WeatherRepository()

    private val _forecast = MutableLiveData<Result<OpenMeteoResponse>>()
    val forecast: LiveData<Result<OpenMeteoResponse>> = _forecast

    private val _isLoading = MutableLiveData(false)
    val isLoading: LiveData<Boolean> = _isLoading

    private val _userLocation = MutableLiveData<Location?>()

    private val _radiusKm = MutableLiveData(prefs.getInt(Prefs.RADIUS_KM, 0))
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
                else              -> Result.success(repository.mergeForecasts(results))
            }
            _isLoading.value = false
        }
    }

    fun setRadius(km: Int) {
        _radiusKm.value = km
        prefs.edit().putInt(Prefs.RADIUS_KM, km).apply()
        loadData(_userLocation.value)
    }
}
