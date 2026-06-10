package it.shinyup.meteoradar.workers

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import it.shinyup.meteoradar.data.WeatherRepository
import it.shinyup.meteoradar.data.db.AppDatabase
import it.shinyup.meteoradar.data.models.AlertLevel
import it.shinyup.meteoradar.utils.LocationHelper
import it.shinyup.meteoradar.utils.NotificationHelper
import it.shinyup.meteoradar.utils.Prefs
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlin.math.cos

class WeatherAlertWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    companion object {
        const val WORK_NAME = "weather_alert_worker"
    }

    private val repository = WeatherRepository()
    private val dao = AppDatabase.getInstance(applicationContext).alertDao()

    override suspend fun doWork(): Result {
        val prefs = applicationContext.getSharedPreferences(Prefs.NAME, Context.MODE_PRIVATE)
        val radius = prefs.getInt(Prefs.RADIUS_KM, 0)

        val location = LocationHelper.getCurrentLocation(applicationContext)
        val lat = location?.latitude ?: LocationHelper.DEFAULT_LAT
        val lon = location?.longitude ?: LocationHelper.DEFAULT_LON

        // Build sampling points: centre + cardinal N/S/E/W if radius > 0
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

        val forecasts = coroutineScope {
            points.map { (la, lo) ->
                async { repository.getForecast(la, lo).getOrNull() }
            }.awaitAll().filterNotNull()
        }

        if (forecasts.isEmpty()) return Result.retry()

        val merged = if (forecasts.size == 1) forecasts[0] else repository.mergeForecasts(forecasts)
        val alerts = repository.assessAlerts(merged, lat, lon)

        // Notify only WARNING or higher; suppress duplicates within 2 hours
        val cutoff = System.currentTimeMillis() - 2 * 60 * 60 * 1000L
        val recentAlerts = dao.getAlertsSince(cutoff)

        for (alert in alerts) {
            dao.insert(alert)
            val alreadyNotified = recentAlerts.any { it.level.ordinal >= alert.level.ordinal }
            if (alert.level.ordinal >= AlertLevel.WARNING.ordinal && !alreadyNotified) {
                NotificationHelper.sendAlertNotification(applicationContext, alert)
            }
        }

        dao.deleteOlderThan(System.currentTimeMillis() - 48 * 60 * 60 * 1000L)

        return Result.success()
    }
}
