package it.shinyup.meteoradar.utils

import android.content.Context
import it.shinyup.meteoradar.data.WeatherRepository
import it.shinyup.meteoradar.data.db.AppDatabase
import it.shinyup.meteoradar.data.db.ForecastSnapshot
import it.shinyup.meteoradar.data.models.DailyData

/**
 * Centralizes storing daily-forecast snapshots so the Analisi (evolution) and
 * Monitor tabs always have fresh data — including for the CURRENT location, not
 * just favorites. Previously the home city relied solely on the background
 * worker, which Android Doze/battery limits could stall for days, leaving the
 * two tabs "frozen" with only one future day.
 */
object SnapshotHelper {

    private const val MIN_INTERVAL_MS = 60 * 60 * 1000L // 1h

    /** Builds one snapshot row per forecast day. */
    fun build(daily: DailyData, cityName: String, fetchedAt: Long): List<ForecastSnapshot> =
        daily.time.indices.mapNotNull { i ->
            val date = daily.time.getOrNull(i) ?: return@mapNotNull null
            ForecastSnapshot(
                fetchedAt = fetchedAt,
                targetDate = date,
                locationName = cityName,
                minTemp = daily.temperatureMin.getOrElse(i) { 0.0 },
                maxTemp = daily.temperatureMax.getOrElse(i) { 0.0 },
                weatherCode = daily.weatherCode.getOrElse(i) { 0 },
                precipProb = daily.precipitationProbabilityMax.getOrElse(i) { 0 },
                precipSum = daily.precipitationSum.getOrElse(i) { 0.0 },
                apparentTempMax = daily.apparentTemperatureMax?.getOrElse(i) { 0.0 } ?: 0.0,
                apparentTempMin = daily.apparentTemperatureMin?.getOrElse(i) { 0.0 } ?: 0.0,
                windSpeedMax = daily.windSpeedMax?.getOrElse(i) { 0.0 } ?: 0.0,
                humidityMax = daily.humidityMax?.getOrElse(i) { 0 } ?: 0
            )
        }

    /** Persists already-fetched daily data, skipping if a snapshot for this
     *  city was stored within the last hour. */
    suspend fun save(context: Context, daily: DailyData, cityName: String) {
        val dao = AppDatabase.getInstance(context).snapshotDao()
        val lastFetch = dao.getLastFetchTimeForLocation(cityName) ?: 0L
        if (System.currentTimeMillis() - lastFetch < MIN_INTERVAL_MS) return
        val snapshots = build(daily, cityName, System.currentTimeMillis())
        if (snapshots.isNotEmpty()) dao.insertAll(snapshots)
    }

    /** Fetches the 7-day daily forecast for the location and stores a snapshot,
     *  skipping if one was stored within the last hour. */
    suspend fun fetchAndSave(
        context: Context,
        repository: WeatherRepository,
        lat: Double,
        lon: Double,
        cityName: String
    ) {
        val dao = AppDatabase.getInstance(context).snapshotDao()
        val lastFetch = dao.getLastFetchTimeForLocation(cityName) ?: 0L
        if (System.currentTimeMillis() - lastFetch < MIN_INTERVAL_MS) return
        val daily = repository.getDailyForecast(lat, lon).getOrNull()?.daily ?: return
        val snapshots = build(daily, cityName, System.currentTimeMillis())
        if (snapshots.isNotEmpty()) dao.insertAll(snapshots)
    }
}
