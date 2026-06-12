package it.shinyup.meteoradar.ui.radar

import android.app.Application
import android.location.Location
import androidx.lifecycle.*
import androidx.preference.PreferenceManager
import it.shinyup.meteoradar.data.WeatherRepository
import it.shinyup.meteoradar.data.models.OpenMeteoResponse
import it.shinyup.meteoradar.utils.GeocoderHelper
import it.shinyup.meteoradar.utils.Prefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.cos

class RadarViewModel(application: Application) : AndroidViewModel(application) {

    private val prefs = PreferenceManager.getDefaultSharedPreferences(application)
    private val repository = WeatherRepository()

    private val _forecast = MutableLiveData<Result<OpenMeteoResponse>>()
    val forecast: LiveData<Result<OpenMeteoResponse>> = _forecast

    private val _locationName = MutableLiveData<String>()
    val locationName: LiveData<String> = _locationName

    private val _isLoading = MutableLiveData(false)
    val isLoading: LiveData<Boolean> = _isLoading

    private var lastSuccessfulFetchMs = 0L
    private val CACHE_MS = 3 * 60 * 1000L

    fun loadData(location: Location?, forceRefresh: Boolean = false) {
        val now = System.currentTimeMillis()
        if (!forceRefresh && _forecast.value?.isSuccess == true && now - lastSuccessfulFetchMs < CACHE_MS) return

        val useGps = prefs.getBoolean(Prefs.USE_GPS, true)
        val lat: Double
        val lon: Double
        if (useGps && location != null) {
            lat = location.latitude
            lon = location.longitude
        } else {
            lat = prefs.getString(Prefs.MANUAL_LAT, "41.9028")?.toDoubleOrNull() ?: 41.9028
            lon = prefs.getString(Prefs.MANUAL_LON, "12.4964")?.toDoubleOrNull() ?: 12.4964
        }

        val radius = prefs.getString(Prefs.RADIUS_KM, "0")?.toIntOrNull() ?: 0
        val forecastHours = prefs.getString(Prefs.FORECAST_HOURS, "24")?.toIntOrNull() ?: 24

        val points = mutableListOf(lat to lon)
        if (radius > 0) {
            val latOff = radius / 111.0
            val lonOff = radius / (111.0 * cos(Math.toRadians(lat)))
            points += listOf(
                (lat + latOff) to lon, (lat - latOff) to lon,
                lat to (lon + lonOff), lat to (lon - lonOff)
            )
        }

        viewModelScope.launch {
            _isLoading.value = true

            val city = withContext(Dispatchers.IO) {
                GeocoderHelper.cityName(getApplication(), lat, lon)
            }
            _locationName.value = city

            val results = points.map { (la, lo) ->
                async { repository.getForecast(la, lo, forecastHours).getOrNull() }
            }.awaitAll().filterNotNull()

            if (results.isEmpty()) {
                if (_forecast.value?.isSuccess != true) {
                    _forecast.value = Result.failure(Exception("Nessun dato"))
                }
            } else {
                _forecast.value = when {
                    results.size == 1 -> Result.success(results[0])
                    else              -> Result.success(repository.mergeForecasts(results))
                }
                lastSuccessfulFetchMs = System.currentTimeMillis()
            }
            _isLoading.value = false
        }
    }
}
