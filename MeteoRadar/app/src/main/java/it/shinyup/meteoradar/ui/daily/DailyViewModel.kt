package it.shinyup.meteoradar.ui.daily

import android.app.Application
import android.location.Location
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import androidx.preference.PreferenceManager
import it.shinyup.meteoradar.data.WeatherRepository
import it.shinyup.meteoradar.data.models.DailyData
import it.shinyup.meteoradar.data.models.DayForecastItem
import it.shinyup.meteoradar.data.models.WeatherCode
import it.shinyup.meteoradar.data.models.ModelComparisonDaily
import it.shinyup.meteoradar.utils.GeocoderHelper
import it.shinyup.meteoradar.utils.Prefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

class DailyViewModel(application: Application) : AndroidViewModel(application) {

    private val prefs = PreferenceManager.getDefaultSharedPreferences(application)
    private val repository = WeatherRepository()

    private val _days = MutableLiveData<Result<List<DayForecastItem>>>()
    val days: LiveData<Result<List<DayForecastItem>>> = _days

    private val _locationName = MutableLiveData<String>()
    val locationName: LiveData<String> = _locationName

    private val _isLoading = MutableLiveData(false)
    val isLoading: LiveData<Boolean> = _isLoading

    private var lastSuccessfulFetchMs = 0L
    private val CACHE_MS = 3 * 60 * 1000L

    fun loadData(location: Location?, forceRefresh: Boolean = false) {
        val now = System.currentTimeMillis()
        if (!forceRefresh && _days.value?.isSuccess == true && now - lastSuccessfulFetchMs < CACHE_MS) return

        val useGps = prefs.getBoolean(Prefs.USE_GPS, true)
        val lat: Double
        val lon: Double
        if (useGps && location != null) {
            lat = location.latitude; lon = location.longitude
        } else {
            lat = prefs.getString(Prefs.MANUAL_LAT, "41.9028")?.toDoubleOrNull() ?: 41.9028
            lon = prefs.getString(Prefs.MANUAL_LON, "12.4964")?.toDoubleOrNull() ?: 12.4964
        }

        viewModelScope.launch {
            _isLoading.value = true

            val city = withContext(Dispatchers.IO) {
                GeocoderHelper.cityName(getApplication(), lat, lon)
            }
            _locationName.value = city

            val dailyDeferred      = async { repository.getDailyForecast(lat, lon) }
            val comparisonDeferred = async { repository.getModelComparison(lat, lon) }
            val result      = dailyDeferred.await()
            val comparison  = comparisonDeferred.await().getOrNull()?.daily

            result.onSuccess { response ->
                val daily = response.daily
                if (daily != null) {
                    _days.value = Result.success(buildItems(daily, comparison))
                    lastSuccessfulFetchMs = System.currentTimeMillis()
                } else if (_days.value?.isSuccess != true) {
                    _days.value = Result.success(emptyList())
                }
            }.onFailure { error ->
                if (_days.value?.isSuccess != true) {
                    _days.value = Result.failure(error)
                }
            }
            _isLoading.value = false
        }
    }

    private fun buildItems(daily: DailyData, comparison: ModelComparisonDaily? = null): List<DayForecastItem> {
        val n = daily.time.size
        val today = LocalDate.now().toString()

        val avgMax = if (n > 0) daily.temperatureMax.take(n).average() else 0.0
        val avgMin = if (n > 0) daily.temperatureMin.take(n).average() else 0.0

        // Real reliability from inter-model spread; fallback to empirical decay if unavailable
        val reliabilities = List(n) { i ->
            val real = comparison?.reliability(i) ?: -1
            Pair(
                if (real >= 0) real else maxOf(50, 95 - i * 7),
                real >= 0
            )
        }

        return List(n) { i ->
            val dateStr   = daily.time[i]
            val isToday   = dateStr == today
            val code      = daily.weatherCode.getOrElse(i) { 0 }
            val currMax   = daily.temperatureMax.getOrElse(i) { 0.0 }
            val currMin   = daily.temperatureMin.getOrElse(i) { 0.0 }
            val prevMax   = if (i > 0) daily.temperatureMax.getOrElse(i - 1) { Double.NaN } else Double.NaN
            val prevMin   = if (i > 0) daily.temperatureMin.getOrElse(i - 1) { Double.NaN } else Double.NaN

            val dateLabel = try {
                val ld  = LocalDate.parse(dateStr)
                val dow = ld.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.ITALIAN).replaceFirstChar { it.uppercase() }
                val day = ld.dayOfMonth
                val mon = ld.month.getDisplayName(TextStyle.SHORT, Locale.ITALIAN)
                if (isToday) "Oggi\n$day $mon" else "$dow\n$day $mon"
            } catch (_: Exception) { dateStr }

            DayForecastItem(
                dateLabel             = dateLabel,
                isToday               = isToday,
                emoji                 = WeatherCode.emoji(code),
                temperatureMax        = currMax,
                temperatureMin        = currMin,
                maxTrend              = trend(prevMax, currMax),
                minTrend              = trend(prevMin, currMin),
                avgMax                = avgMax,
                avgMin                = avgMin,
                precipitationSum      = daily.precipitationSum.getOrElse(i) { 0.0 },
                precipitationProbabilityMax = daily.precipitationProbabilityMax.getOrElse(i) { 0 },
                reliabilityPct        = reliabilities[i].first,
                reliabilityFromModels = reliabilities[i].second
            )
        }
    }

    private fun trend(prev: Double, curr: Double): Int = when {
        prev.isNaN()       -> 0
        curr > prev + 0.5  -> 1
        curr < prev - 0.5  -> -1
        else               -> 0
    }
}
