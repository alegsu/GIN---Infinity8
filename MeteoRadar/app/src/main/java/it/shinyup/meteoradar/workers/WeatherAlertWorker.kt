package it.shinyup.meteoradar.workers

import android.content.Context
import androidx.preference.PreferenceManager
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

class WeatherAlertWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    companion object {
        const val WORK_NAME = "weather_alert_worker"
    }

    private val repository = WeatherRepository()
    private val dao = AppDatabase.getInstance(applicationContext).alertDao()

    override suspend fun doWork(): Result {
        val prefs = PreferenceManager.getDefaultSharedPreferences(applicationContext)

        if (!prefs.getBoolean(Prefs.NOTIFICATIONS_ENABLED, true)) return Result.success()

        val radius = prefs.getString(Prefs.RADIUS_KM, "0")?.toIntOrNull() ?: 0
        val threshold = prefs.getString(Prefs.ALERT_THRESHOLD, "3")?.toIntOrNull() ?: 3

        val useGps = prefs.getBoolean(Prefs.USE_GPS, true)
        val location = if (useGps) LocationHelper.getCurrentLocation(applicationContext) else null

        if (useGps && location == null) {
            checkFavoriteCityAlerts(prefs, when {
                threshold >= 7 -> AlertLevel.EXTREME
                threshold >= 5 -> AlertLevel.DANGER
                threshold >= 3 -> AlertLevel.WARNING
                else           -> AlertLevel.INFO
            })
            return Result.success()
        }

        val lat = if (useGps && location != null) location.latitude
                  else prefs.getString(Prefs.MANUAL_LAT, "41.9028")?.toDoubleOrNull() ?: LocationHelper.DEFAULT_LAT
        val lon = if (useGps && location != null) location.longitude
                  else prefs.getString(Prefs.MANUAL_LON, "12.4964")?.toDoubleOrNull() ?: LocationHelper.DEFAULT_LON

        val points = mutableListOf(lat to lon)
        if (radius > 0) {
            val latOff = radius / 111.0
            val lonOff = radius / (111.0 * cos(Math.toRadians(lat)))
            points += listOf(
                (lat + latOff) to lon, (lat - latOff) to lon,
                lat to (lon + lonOff), lat to (lon - lonOff)
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

        // Only notify if score-derived level meets user's threshold
        val thresholdLevel = when {
            threshold >= 7 -> AlertLevel.EXTREME
            threshold >= 5 -> AlertLevel.DANGER
            threshold >= 3 -> AlertLevel.WARNING
            else           -> AlertLevel.INFO
        }

        val cutoff = System.currentTimeMillis() - 2 * 60 * 60 * 1000L
        val recentAlerts = dao.getAlertsSince(cutoff)

        for (alert in alerts) {
            dao.insert(alert)
            val meetsThreshold = alert.level.ordinal >= thresholdLevel.ordinal
            val notRecentlySent = recentAlerts.none { it.level.ordinal >= alert.level.ordinal }
            if (meetsThreshold && notRecentlySent) {
                NotificationHelper.sendAlertNotification(applicationContext, alert)
            }
        }

        dao.deleteOlderThan(System.currentTimeMillis() - 48 * 60 * 60 * 1000L)

        // Check favorite cities for alerts
        checkFavoriteCityAlerts(prefs, thresholdLevel)

        return Result.success()
    }

    private suspend fun checkFavoriteCityAlerts(
        prefs: android.content.SharedPreferences,
        thresholdLevel: AlertLevel
    ) {
        val favorites = listOf(
            Triple(Prefs.FAVORITE_1_LAT, Prefs.FAVORITE_1_LON, Prefs.FAVORITE_1_NAME),
            Triple(Prefs.FAVORITE_2_LAT, Prefs.FAVORITE_2_LON, Prefs.FAVORITE_2_NAME)
        )

        val cutoff = System.currentTimeMillis() - 2 * 60 * 60 * 1000L

        for ((latKey, lonKey, nameKey) in favorites) {
            val favLat = prefs.getString(latKey, "")?.toDoubleOrNull() ?: continue
            val favLon = prefs.getString(lonKey, "")?.toDoubleOrNull() ?: continue
            val cityName = prefs.getString(nameKey, null)?.takeIf { it.isNotBlank() } ?: "Preferita"

            val forecast = repository.getForecast(favLat, favLon).getOrNull() ?: continue
            val alerts = repository.assessAlerts(forecast, favLat, favLon)

            val recentAlerts = dao.getAlertsSince(cutoff)

            for (alert in alerts) {
                val prefixedAlert = alert.copy(
                    title = "$cityName: ${alert.title}"
                )
                dao.insert(prefixedAlert)
                val meetsThreshold = prefixedAlert.level.ordinal >= thresholdLevel.ordinal
                val notRecentlySent = recentAlerts.none { it.level.ordinal >= prefixedAlert.level.ordinal }
                if (meetsThreshold && notRecentlySent) {
                    NotificationHelper.sendAlertNotification(applicationContext, prefixedAlert)
                }
            }
        }
    }
}
