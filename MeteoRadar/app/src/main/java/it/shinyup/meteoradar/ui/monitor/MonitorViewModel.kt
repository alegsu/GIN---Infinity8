package it.shinyup.meteoradar.ui.monitor

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import it.shinyup.meteoradar.data.db.AppDatabase
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.abs

class MonitorViewModel(application: Application) : AndroidViewModel(application) {

    private val snapshotDao = AppDatabase.getInstance(application).snapshotDao()

    private val _changes = MutableLiveData<List<TemperatureChangeItem>>()
    val changes: LiveData<List<TemperatureChangeItem>> = _changes

    private val _cities = MutableLiveData<List<String>>()
    val cities: LiveData<List<String>> = _cities

    private val _isLoading = MutableLiveData(false)
    val isLoading: LiveData<Boolean> = _isLoading

    private var selectedCity: String? = null

    fun loadChanges(minDelta: Double = 0.5) {
        viewModelScope.launch {
            _isLoading.value = true

            val today = LocalDate.now().toString()
            val allSnapshots = snapshotDao.getSnapshotsFrom(today)

            val citySet = allSnapshots.map { it.locationName }.distinct().sorted()
            _cities.value = citySet

            val grouped = allSnapshots.groupBy { "${it.targetDate}|${it.locationName}" }

            val result = mutableListOf<TemperatureChangeItem>()

            for ((_, snapshots) in grouped) {
                if (snapshots.size < 2) continue

                val sorted = snapshots.sortedBy { it.fetchedAt }
                val latest = sorted.last()
                val previous = sorted[sorted.size - 2]

                if (selectedCity != null && latest.locationName != selectedCity) continue

                val maxDelta = latest.maxTemp - previous.maxTemp
                val minDelta2 = latest.minTemp - previous.minTemp

                if (abs(maxDelta) >= minDelta || abs(minDelta2) >= minDelta) {
                    result.add(
                        TemperatureChangeItem(
                            city = latest.locationName,
                            date = latest.targetDate,
                            dateLabel = formatDay(latest.targetDate),
                            oldMax = previous.maxTemp,
                            newMax = latest.maxTemp,
                            oldMin = previous.minTemp,
                            newMin = latest.minTemp,
                            maxDelta = maxDelta,
                            minDelta = minDelta2,
                            detectedAt = latest.fetchedAt,
                            ageLabel = formatAge(latest.fetchedAt)
                        )
                    )
                }
            }

            _changes.value = result.sortedByDescending { it.detectedAt }
            _isLoading.value = false
        }
    }

    fun selectCity(city: String?) {
        selectedCity = city
        loadChanges()
    }

    private fun formatDay(iso: String): String = try {
        val d = LocalDate.parse(iso)
        val today = LocalDate.now()
        when {
            d == today -> "Oggi"
            d == today.plusDays(1) -> "Domani"
            d == today.plusDays(2) -> "Dopodomani"
            else -> {
                val dow = d.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault())
                    .replaceFirstChar { it.uppercase() }
                "$dow ${d.dayOfMonth}"
            }
        }
    } catch (_: Exception) { iso }

    private fun formatAge(fetchedAt: Long): String {
        val diffMin = (System.currentTimeMillis() - fetchedAt) / 60_000
        return when {
            diffMin < 5   -> "ora"
            diffMin < 60  -> "${diffMin}min fa"
            diffMin < 1440 -> "${diffMin / 60}h fa"
            else           -> "${diffMin / 1440}g fa"
        }
    }
}
