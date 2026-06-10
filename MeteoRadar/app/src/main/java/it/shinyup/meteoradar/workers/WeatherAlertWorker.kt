package it.shinyup.meteoradar.workers

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import it.shinyup.meteoradar.data.WeatherRepository
import it.shinyup.meteoradar.data.db.AppDatabase
import it.shinyup.meteoradar.utils.LocationHelper
import it.shinyup.meteoradar.utils.NotificationHelper

class WeatherAlertWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    companion object {
        const val WORK_NAME = "weather_alert_worker"
    }

    private val repository = WeatherRepository()
    private val dao = AppDatabase.getInstance(context).alertDao()

    override suspend fun doWork(): Result {
        val location = LocationHelper.getCurrentLocation(applicationContext)
        val lat = location?.latitude ?: LocationHelper.DEFAULT_LAT
        val lon = location?.longitude ?: LocationHelper.DEFAULT_LON

        val forecastResult = repository.getForecast(lat, lon)
        val forecast = forecastResult.getOrNull() ?: return Result.retry()

        val alerts = repository.assessAlerts(forecast, lat, lon)

        // Deduplicate per type: don't re-notify the same alert type within 1 hour
        val cutoff = System.currentTimeMillis() - 60 * 60 * 1000
        val recentAlerts = dao.getAlertsSince(cutoff)

        for (alert in alerts) {
            dao.insert(alert)
            if (recentAlerts.none { it.type == alert.type }) {
                NotificationHelper.sendAlertNotification(applicationContext, alert)
            }
        }

        // Clean up alerts older than 48 hours
        dao.deleteOlderThan(System.currentTimeMillis() - 48 * 60 * 60 * 1000)

        return Result.success()
    }
}
