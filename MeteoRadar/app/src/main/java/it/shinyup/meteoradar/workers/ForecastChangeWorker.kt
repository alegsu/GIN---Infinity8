package it.shinyup.meteoradar.workers

import android.content.Context
import androidx.preference.PreferenceManager
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import it.shinyup.meteoradar.data.WeatherRepository
import it.shinyup.meteoradar.data.db.AppDatabase
import it.shinyup.meteoradar.data.db.ForecastSnapshot
import it.shinyup.meteoradar.utils.GeocoderHelper
import it.shinyup.meteoradar.utils.LocationHelper
import it.shinyup.meteoradar.utils.NotificationHelper
import it.shinyup.meteoradar.utils.Prefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

class ForecastChangeWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    companion object {
        const val WORK_NAME = "forecast_change_worker"
    }

    private val repository = WeatherRepository()
    private val snapshotDao = AppDatabase.getInstance(applicationContext).snapshotDao()

    override suspend fun doWork(): Result {
        val prefs = PreferenceManager.getDefaultSharedPreferences(applicationContext)
        if (!prefs.getBoolean(Prefs.NOTIFICATIONS_ENABLED, true)) return Result.success()
        if (!prefs.getBoolean(Prefs.FORECAST_CHANGE_NOTIFICATIONS, true)) return Result.success()

        val tempThreshold = prefs.getString(Prefs.FORECAST_CHANGE_THRESHOLD, "1.0")
            ?.toDoubleOrNull() ?: 1.0

        val useGps = prefs.getBoolean(Prefs.USE_GPS, true)
        val location = if (useGps) LocationHelper.getCurrentLocation(applicationContext) else null
        val lat = if (useGps && location != null) location.latitude
                  else prefs.getString(Prefs.MANUAL_LAT, "41.9028")?.toDoubleOrNull() ?: LocationHelper.DEFAULT_LAT
        val lon = if (useGps && location != null) location.longitude
                  else prefs.getString(Prefs.MANUAL_LON, "12.4964")?.toDoubleOrNull() ?: LocationHelper.DEFAULT_LON

        val city = withContext(Dispatchers.IO) {
            GeocoderHelper.cityName(applicationContext, lat, lon)
        }

        val dailyResult = repository.getDailyForecast(lat, lon)
        val daily = dailyResult.getOrNull()?.daily ?: return Result.retry()

        val lastFetch = snapshotDao.getLastFetchTimeForLocation(city) ?: 0L
        if (System.currentTimeMillis() - lastFetch < 4 * 60 * 60 * 1000L) return Result.success()

        val today = LocalDate.now().toString()
        val oldSnapshots = snapshotDao.getSnapshotsFrom(today)
        val oldByDate = oldSnapshots.groupBy { it.targetDate }
            .mapValues { (_, snaps) -> snaps.maxByOrNull { it.fetchedAt } }

        val fetchedAt = System.currentTimeMillis()
        val newSnapshots = daily.time.indices.mapNotNull { i ->
            val date = daily.time.getOrNull(i) ?: return@mapNotNull null
            ForecastSnapshot(
                fetchedAt = fetchedAt,
                targetDate = date,
                locationName = city,
                minTemp = daily.temperatureMin.getOrElse(i) { 0.0 },
                maxTemp = daily.temperatureMax.getOrElse(i) { 0.0 },
                weatherCode = daily.weatherCode.getOrElse(i) { 0 },
                precipProb = daily.precipitationProbabilityMax.getOrElse(i) { 0 },
                precipSum = daily.precipitationSum.getOrElse(i) { 0.0 }
            )
        }

        if (newSnapshots.isEmpty()) return Result.success()

        val changes = mutableListOf<DayChange>()
        for (snap in newSnapshots) {
            val old = oldByDate[snap.targetDate] ?: continue
            val maxDelta = snap.maxTemp - old.maxTemp
            val minDelta = snap.minTemp - old.minTemp
            if (abs(maxDelta) >= tempThreshold || abs(minDelta) >= tempThreshold) {
                changes.add(DayChange(snap.targetDate, maxDelta, minDelta, snap.maxTemp, snap.minTemp))
            }
        }

        if (changes.isNotEmpty()) {
            sendGroupedNotification(changes, city)
        }

        snapshotDao.insertAll(newSnapshots)
        snapshotDao.deleteOlderThan(System.currentTimeMillis() - 30L * 24 * 60 * 60 * 1000L)

        return Result.success()
    }

    private fun sendGroupedNotification(changes: List<DayChange>, city: String) {
        val title = if (changes.size == 1) {
            "📊 Previsione aggiornata — ${formatDay(changes[0].date)}"
        } else {
            "📊 Previsioni aggiornate — ${changes.size} giorni"
        }

        val body = buildString {
            append("📍 $city\n")
            for (ch in changes) {
                append("\n${formatDay(ch.date)}: ")
                val parts = mutableListOf<String>()
                if (abs(ch.maxDelta) >= 0.1) {
                    val arrow = if (ch.maxDelta > 0) "↑" else "↓"
                    parts.add("Max ${ch.newMax.roundToInt()}° ($arrow${"%+.1f".format(ch.maxDelta)}°)")
                }
                if (abs(ch.minDelta) >= 0.1) {
                    val arrow = if (ch.minDelta > 0) "↑" else "↓"
                    parts.add("Min ${ch.newMin.roundToInt()}° ($arrow${"%+.1f".format(ch.minDelta)}°)")
                }
                append(parts.joinToString(" · "))
            }
        }

        NotificationHelper.sendForecastChangeNotification(applicationContext, title, body)
    }

    private fun formatDay(iso: String): String = try {
        val d = LocalDate.parse(iso)
        val today = LocalDate.now()
        when {
            d == today -> "Oggi"
            d == today.plusDays(1) -> "Domani"
            else -> {
                val dow = d.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault())
                    .replaceFirstChar { it.uppercase() }
                "$dow ${d.dayOfMonth}"
            }
        }
    } catch (_: Exception) { iso }

    private data class DayChange(
        val date: String,
        val maxDelta: Double,
        val minDelta: Double,
        val newMax: Double,
        val newMin: Double
    )
}
