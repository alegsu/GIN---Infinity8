package it.shinyup.meteoradar.ui.wind

import android.app.Application
import android.location.Location
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import androidx.preference.PreferenceManager
import it.shinyup.meteoradar.data.WeatherRepository
import it.shinyup.meteoradar.data.models.HourlyWindData
import it.shinyup.meteoradar.utils.GeocoderHelper
import it.shinyup.meteoradar.utils.Prefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.roundToInt

data class WindDaySummary(
    val avgWind: Int,
    val maxGust: Int,
    val tempMin: Int,
    val tempMax: Int
)

class WindViewModel(application: Application) : AndroidViewModel(application) {

    private val prefs = PreferenceManager.getDefaultSharedPreferences(application)
    private val repository = WeatherRepository()

    private val _windData = MutableLiveData<Result<HourlyWindData>>()
    val windData: LiveData<Result<HourlyWindData>> = _windData

    private val _locationName = MutableLiveData<String>()
    val locationName: LiveData<String> = _locationName

    private val _isLoading = MutableLiveData(false)
    val isLoading: LiveData<Boolean> = _isLoading

    private val _days = MutableLiveData<List<String>>()
    val days: LiveData<List<String>> = _days

    private val _selectedDay = MutableLiveData<String>()
    val selectedDay: LiveData<String> = _selectedDay

    private var cachedData: HourlyWindData? = null
    private var lastFetchMs = 0L

    fun loadData(location: Location?) {
        val now = System.currentTimeMillis()
        if (cachedData != null && now - lastFetchMs < 3 * 60 * 1000L) return

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

        viewModelScope.launch {
            _isLoading.value = true

            val city = withContext(Dispatchers.IO) {
                GeocoderHelper.cityName(getApplication(), lat, lon)
            }
            _locationName.value = city

            val result = repository.getHourlyWind(lat, lon)
            result.onSuccess { response ->
                val hourly = response.hourly
                if (hourly != null) {
                    cachedData = hourly
                    lastFetchMs = System.currentTimeMillis()
                    _windData.value = Result.success(hourly)

                    val dayList = hourly.time.map { it.substringBefore("T") }.distinct()
                    _days.value = dayList
                    if (_selectedDay.value == null && dayList.isNotEmpty()) {
                        _selectedDay.value = dayList[0]
                    }
                } else {
                    _windData.value = Result.failure(Exception("Nessun dato vento"))
                }
            }.onFailure {
                _windData.value = Result.failure(it)
            }

            _isLoading.value = false
        }
    }

    fun selectDay(day: String) {
        _selectedDay.value = day
    }

    fun getHoursForDay(day: String): List<WindHourItem> {
        val data = cachedData ?: return emptyList()
        val nowHour = LocalDateTime.now().hour
        val isToday = day == LocalDate.now().toString()
        return data.time.indices.filter { data.time[it].startsWith(day) }.map { i ->
            val hour = data.time[i].substringAfter("T").take(2).toIntOrNull() ?: -1
            WindHourItem(
                time = data.time[i].substringAfter("T").take(5),
                temperature = data.temperature[i],
                windSpeed = data.windSpeed[i],
                windDirection = data.windDirection[i],
                windGusts = data.windGusts[i],
                isCurrentHour = isToday && hour == nowHour
            )
        }
    }

    fun getSummaryForDay(day: String): WindDaySummary? {
        val hours = getHoursForDay(day)
        if (hours.isEmpty()) return null
        return WindDaySummary(
            avgWind = hours.map { it.windSpeed }.average().roundToInt(),
            maxGust = hours.maxOf { it.windGusts }.roundToInt(),
            tempMin = hours.minOf { it.temperature }.roundToInt(),
            tempMax = hours.maxOf { it.temperature }.roundToInt()
        )
    }

    fun formatDayLabel(iso: String): String = try {
        val d = LocalDate.parse(iso)
        val today = LocalDate.now()
        when (d) {
            today -> "Oggi"
            today.plusDays(1) -> "Domani"
            else -> d.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault())
                .replaceFirstChar { it.uppercase() } + " ${d.dayOfMonth}"
        }
    } catch (_: Exception) { iso }
}
