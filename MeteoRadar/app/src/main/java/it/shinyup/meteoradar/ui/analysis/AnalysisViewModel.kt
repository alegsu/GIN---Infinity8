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
import it.shinyup.meteoradar.utils.Prefs
import kotlinx.coroutines.launch
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
    val weatherCode: Int
)

data class EvolutionState(
    val availableDates: List<String>,     // ISO dates of future days that have snapshots
    val selectedDate: String?,
    val dateLabel: String,
    val points: List<ForecastEvolutionChartView.DataPoint>,
    val hasEnoughData: Boolean            // need >= 2 points to draw chart
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

    fun loadPastDays(location: Location?) {
        val useGps = prefs.getBoolean(Prefs.USE_GPS, true)
        val lat = if (useGps && location != null) location.latitude
                  else prefs.getString(Prefs.MANUAL_LAT, "41.9028")?.toDoubleOrNull() ?: 41.9028
        val lon = if (useGps && location != null) location.longitude
                  else prefs.getString(Prefs.MANUAL_LON, "12.4964")?.toDoubleOrNull() ?: 12.4964

        viewModelScope.launch {
            _isLoading.value = true
            repository.getPastDaysData(lat, lon).onSuccess { response ->
                val daily = response.daily ?: return@onSuccess
                _pastDays.value = buildPastItems(daily)
            }
            _isLoading.value = false
        }
    }

    fun loadEvolution() {
        viewModelScope.launch {
            val dao = AppDatabase.getInstance(getApplication()).snapshotDao()
            val today = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)
            val all = dao.getSnapshotsFrom(today)

            // Get future dates that have at least 1 snapshot
            val grouped = all.groupBy { it.targetDate }
            val validDates = grouped.keys.filter { (grouped[it]?.size ?: 0) >= 1 }.sorted()

            if (validDates.isEmpty()) {
                _evolution.value = EvolutionState(emptyList(), null, "", emptyList(), false)
                return@launch
            }

            // Default: first available date
            val current = _evolution.value?.selectedDate?.takeIf { it in validDates } ?: validDates.first()
            loadEvolutionForDate(current, grouped)
        }
    }

    fun selectDate(date: String) {
        viewModelScope.launch {
            val dao = AppDatabase.getInstance(getApplication()).snapshotDao()
            val today = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)
            val all = dao.getSnapshotsFrom(today)
            val grouped = all.groupBy { it.targetDate }
            loadEvolutionForDate(date, grouped)
        }
    }

    private fun loadEvolutionForDate(date: String, grouped: Map<String, List<ForecastSnapshot>>) {
        val validDates = grouped.keys.filter { (grouped[it]?.size ?: 0) >= 1 }.sorted()
        val snapshots = grouped[date]?.sortedBy { it.fetchedAt } ?: emptyList()

        val points = snapshots.map { snap ->
            ForecastEvolutionChartView.DataPoint(
                xLabel = formatFetchAge(snap.fetchedAt),
                tempMax = snap.maxTemp.toFloat(),
                tempMin = snap.minTemp.toFloat(),
                location = snap.locationName
            )
        }

        val label = try {
            val d = LocalDate.parse(date)
            val dow = d.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.ITALIAN).replaceFirstChar { it.uppercase() }
            val day = d.dayOfMonth
            val mon = d.month.getDisplayName(TextStyle.FULL, Locale.ITALIAN)
            "$dow $day $mon"
        } catch (e: Exception) { date }

        _evolution.value = EvolutionState(
            availableDates = validDates,
            selectedDate = date,
            dateLabel = label,
            points = points,
            hasEnoughData = points.size >= 2
        )
    }

    private fun buildPastItems(daily: DailyData): List<PastDayItem> {
        val yesterday = LocalDate.now().minusDays(1).toString()
        return daily.time.indices.mapNotNull { i ->
            val dateStr = daily.time.getOrNull(i) ?: return@mapNotNull null
            val code = daily.weatherCode.getOrElse(i) { 0 }
            val dateLabel = try {
                val d = LocalDate.parse(dateStr)
                val dow = d.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.ITALIAN).replaceFirstChar { it.uppercase() }
                "$dow ${d.dayOfMonth} ${d.month.getDisplayName(TextStyle.SHORT, Locale.ITALIAN)}"
            } catch (e: Exception) { dateStr }

            PastDayItem(
                dateLabel = dateLabel,
                isYesterday = dateStr == yesterday,
                emoji = WeatherCode.emoji(code),
                description = WeatherCode.description(code),
                tempMax = daily.temperatureMax.getOrElse(i) { 0.0 },
                tempMin = daily.temperatureMin.getOrElse(i) { 0.0 },
                precipSum = daily.precipitationSum.getOrElse(i) { 0.0 },
                weatherCode = code
            )
        }.reversed() // most recent first
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
