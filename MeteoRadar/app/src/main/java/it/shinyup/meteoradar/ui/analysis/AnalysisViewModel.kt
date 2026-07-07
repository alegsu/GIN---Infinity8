package it.shinyup.meteoradar.ui.analysis

import android.app.Application
import android.location.Location
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import androidx.preference.PreferenceManager
import it.shinyup.meteoradar.data.WeatherRepository
import it.shinyup.meteoradar.data.db.AppDatabase
import it.shinyup.meteoradar.data.db.ForecastSnapshot
import it.shinyup.meteoradar.data.models.DailyData
import it.shinyup.meteoradar.data.models.WeatherCode
import it.shinyup.meteoradar.utils.GeocoderHelper
import it.shinyup.meteoradar.utils.Prefs
import it.shinyup.meteoradar.utils.SnapshotHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

data class PastDayItem(
    val dateLabel: String,
    val isYesterday: Boolean,
    val emoji: String,
    val description: String,
    val tempMax: Double,
    val tempMin: Double,
    val precipSum: Double,
    val weatherCode: Int,
    val forecastMax: Double? = null,
    val forecastMin: Double? = null,
    val accuracyPct: Int? = null
)

data class EvolutionState(
    val availableDates: List<String>,
    val selectedDate: String?,
    val dateLabel: String,
    val points: List<ForecastEvolutionChartView.DataPoint>,
    val hasEnoughData: Boolean,
    val globalScale: ForecastEvolutionChartView.ScaleRange? = null,
    val availableCities: List<String> = emptyList(),
    val selectedCity: String? = null
)

class AnalysisViewModel(application: Application) : AndroidViewModel(application) {

    private val prefs = PreferenceManager.getDefaultSharedPreferences(application)
    private val repository = WeatherRepository()

    private val _pastDays = MutableLiveData<List<PastDayItem>>()
    val pastDays: LiveData<List<PastDayItem>> = _pastDays

    private val _evolution = MutableLiveData<EvolutionState>()
    val evolution: LiveData<EvolutionState> = _evolution

    private val _isLoading = MutableLiveData(false)
    val isLoading: LiveData<Boolean> = _isLoading

    private var cachedGrouped: Map<String, List<ForecastSnapshot>> = emptyMap()
    private var cachedGlobalScale: ForecastEvolutionChartView.ScaleRange? = null

    fun loadPastDays(location: Location?) {
        val useGps = prefs.getBoolean(Prefs.USE_GPS, true)
        val lat = if (useGps && location != null) location.latitude
                  else prefs.getString(Prefs.MANUAL_LAT, "41.9028")?.toDoubleOrNull() ?: 41.9028
        val lon = if (useGps && location != null) location.longitude
                  else prefs.getString(Prefs.MANUAL_LON, "12.4964")?.toDoubleOrNull() ?: 12.4964

        viewModelScope.launch {
            _isLoading.value = true

            // Keep the CURRENT location fresh so evolution/monitor don't stall
            val city = withContext(Dispatchers.IO) {
                GeocoderHelper.cityName(getApplication(), lat, lon)
            }
            SnapshotHelper.fetchAndSave(getApplication(), repository, lat, lon, city)

            saveFavoriteCitySnapshots()

            // Reload evolution now that fresh snapshots (incl. current location) exist
            loadEvolution()

            repository.getPastDaysData(lat, lon).onSuccess { response ->
                val daily = response.daily ?: return@onSuccess
                val dao = AppDatabase.getInstance(getApplication()).snapshotDao()
                val pastDates = daily.time.toList()
                val snapshots = if (pastDates.isNotEmpty()) dao.getSnapshotsForDates(pastDates) else emptyList()
                val lastForecastByDate = snapshots
                    .groupBy { it.targetDate }
                    .mapValues { (_, snaps) -> snaps.maxByOrNull { it.fetchedAt } }
                _pastDays.value = buildPastItems(daily, lastForecastByDate)
            }
            _isLoading.value = false
        }
    }

    private suspend fun saveFavoriteCitySnapshots() {
        val favorites = listOf(
            Triple(Prefs.FAVORITE_1_LAT, Prefs.FAVORITE_1_LON, Prefs.FAVORITE_1_NAME),
            Triple(Prefs.FAVORITE_2_LAT, Prefs.FAVORITE_2_LON, Prefs.FAVORITE_2_NAME)
        )

        for ((latKey, lonKey, nameKey) in favorites) {
            val favLat = prefs.getString(latKey, "")?.toDoubleOrNull() ?: continue
            val favLon = prefs.getString(lonKey, "")?.toDoubleOrNull() ?: continue

            val cityName = prefs.getString(nameKey, null)?.takeIf { it.isNotBlank() }
                ?: withContext(Dispatchers.IO) {
                    GeocoderHelper.cityName(getApplication(), favLat, favLon)
                }

            SnapshotHelper.fetchAndSave(getApplication(), repository, favLat, favLon, cityName)
        }
    }

    private fun normalizeCity(name: String): String = name.substringBefore(",").trim()

    fun loadEvolution() {
        viewModelScope.launch {
            val dao = AppDatabase.getInstance(getApplication()).snapshotDao()
            val today = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)
            val rawAll = dao.getSnapshotsFrom(today)

            val all = rawAll.map { it.copy(locationName = normalizeCity(it.locationName)) }

            val allCities = all.map { it.locationName }.distinct().sorted()
            val selectedCity = _evolution.value?.selectedCity?.takeIf { it in allCities }
                ?: allCities.firstOrNull()

            val filtered = if (selectedCity != null) all.filter { it.locationName == selectedCity } else all
            val grouped = filtered.groupBy { it.targetDate }
            cachedGrouped = grouped

            cachedGlobalScale = computeGlobalScale(grouped)

            val validDates = grouped.keys.filter { (grouped[it]?.size ?: 0) >= 1 }.sorted()

            if (validDates.isEmpty()) {
                _evolution.value = EvolutionState(
                    emptyList(), null, "", emptyList(), false,
                    availableCities = allCities, selectedCity = selectedCity
                )
                return@launch
            }

            val current = _evolution.value?.selectedDate?.takeIf { it in validDates } ?: validDates.first()
            loadEvolutionForDate(current, grouped, allCities, selectedCity)
        }
    }

    fun selectDate(date: String) {
        val grouped = cachedGrouped
        val state = _evolution.value ?: return
        loadEvolutionForDate(date, grouped, state.availableCities, state.selectedCity)
    }

    fun selectCity(city: String) {
        viewModelScope.launch {
            val dao = AppDatabase.getInstance(getApplication()).snapshotDao()
            val today = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)
            val rawAll = dao.getSnapshotsFrom(today)
            val all = rawAll.map { it.copy(locationName = normalizeCity(it.locationName)) }

            val allCities = all.map { it.locationName }.distinct().sorted()
            val filtered = all.filter { it.locationName == city }
            val grouped = filtered.groupBy { it.targetDate }
            cachedGrouped = grouped
            cachedGlobalScale = computeGlobalScale(grouped)

            val validDates = grouped.keys.filter { (grouped[it]?.size ?: 0) >= 1 }.sorted()

            if (validDates.isEmpty()) {
                _evolution.value = EvolutionState(
                    emptyList(), null, "", emptyList(), false,
                    globalScale = cachedGlobalScale,
                    availableCities = allCities, selectedCity = city
                )
                return@launch
            }

            val current = validDates.first()
            loadEvolutionForDate(current, grouped, allCities, city)
        }
    }

    private fun computeGlobalScale(grouped: Map<String, List<ForecastSnapshot>>): ForecastEvolutionChartView.ScaleRange? {
        val allSnapshots = grouped.values.flatten()
        if (allSnapshots.isEmpty()) return null

        val allMax = allSnapshots.map { it.maxTemp.toFloat() }
        val allMin = allSnapshots.map { it.minTemp.toFloat() }

        val maxFloor = allMax.min() - 1f
        val maxCeil = allMax.max() + 1f
        val minFloor = allMin.min() - 1f
        val minCeil = allMin.max() + 1f

        val maxRange = maxOf(maxCeil - maxFloor, 4f)
        val minRange = maxOf(minCeil - minFloor, 4f)
        val maxCenter = (maxFloor + maxCeil) / 2f
        val minCenter = (minFloor + minCeil) / 2f

        return ForecastEvolutionChartView.ScaleRange(
            maxFloor = maxCenter - maxRange / 2f,
            maxCeil = maxCenter + maxRange / 2f,
            minFloor = minCenter - minRange / 2f,
            minCeil = minCenter + minRange / 2f
        )
    }

    private fun loadEvolutionForDate(
        date: String,
        grouped: Map<String, List<ForecastSnapshot>>,
        allCities: List<String>,
        selectedCity: String?
    ) {
        val validDates = grouped.keys.filter { (grouped[it]?.size ?: 0) >= 1 }.sorted()
        val snapshots = grouped[date]?.sortedBy { it.fetchedAt } ?: emptyList()

        val points = snapshots.map { snap ->
            ForecastEvolutionChartView.DataPoint(
                xLabel = formatFetchAge(snap.fetchedAt),
                tempMax = snap.maxTemp.toFloat(),
                tempMin = snap.minTemp.toFloat(),
                location = snap.locationName,
                apparentMax = snap.apparentTempMax.toFloat(),
                apparentMin = snap.apparentTempMin.toFloat(),
                windSpeed = snap.windSpeedMax.toFloat(),
                humidity = snap.humidityMax
            )
        }

        val label = try {
            val d = LocalDate.parse(date)
            val dow = d.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.getDefault()).replaceFirstChar { it.uppercase() }
            val day = d.dayOfMonth
            val mon = d.month.getDisplayName(TextStyle.FULL, Locale.getDefault())
            "$dow $day $mon"
        } catch (e: Exception) { date }

        _evolution.value = EvolutionState(
            availableDates = validDates,
            selectedDate = date,
            dateLabel = label,
            points = points,
            hasEnoughData = points.size >= 2,
            globalScale = cachedGlobalScale,
            availableCities = allCities,
            selectedCity = selectedCity
        )
    }

    private fun buildPastItems(
        daily: DailyData,
        forecasts: Map<String, ForecastSnapshot?> = emptyMap()
    ): List<PastDayItem> {
        val yesterday = LocalDate.now().minusDays(1).toString()
        return daily.time.indices.mapNotNull { i ->
            val dateStr = daily.time.getOrNull(i) ?: return@mapNotNull null
            val code = daily.weatherCode.getOrElse(i) { 0 }
            val dateLabel = try {
                val d = LocalDate.parse(dateStr)
                val dow = d.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault()).replaceFirstChar { it.uppercase() }
                "$dow ${d.dayOfMonth} ${d.month.getDisplayName(TextStyle.SHORT, Locale.getDefault())}"
            } catch (e: Exception) { dateStr }

            val actualMax = daily.temperatureMax.getOrElse(i) { 0.0 }
            val actualMin = daily.temperatureMin.getOrElse(i) { 0.0 }
            val forecast = forecasts[dateStr]

            val accuracy = if (forecast != null) {
                val errMax = kotlin.math.abs(forecast.maxTemp - actualMax)
                val errMin = kotlin.math.abs(forecast.minTemp - actualMin)
                val avgErr = (errMax + errMin) / 2.0
                (100 - avgErr * 10).coerceIn(0.0, 100.0).toInt()
            } else null

            PastDayItem(
                dateLabel = dateLabel,
                isYesterday = dateStr == yesterday,
                emoji = WeatherCode.emoji(code),
                description = WeatherCode.description(code),
                tempMax = actualMax,
                tempMin = actualMin,
                precipSum = daily.precipitationSum.getOrElse(i) { 0.0 },
                weatherCode = code,
                forecastMax = forecast?.maxTemp,
                forecastMin = forecast?.minTemp,
                accuracyPct = accuracy
            )
        }.reversed()
    }

    private fun formatFetchAge(fetchedAt: Long): String {
        val diffH = (System.currentTimeMillis() - fetchedAt) / (60 * 60 * 1000L)
        return when {
            diffH < 2  -> "Ora"
            diffH < 24 -> "${diffH}h fa"
            else -> "${diffH / 24}g fa"
        }
    }
}
