package it.shinyup.meteoradar

import android.app.Application
import androidx.preference.PreferenceManager
import androidx.work.*
import it.shinyup.meteoradar.utils.NotificationHelper
import it.shinyup.meteoradar.utils.Prefs
import it.shinyup.meteoradar.workers.WeatherAlertWorker
import java.util.concurrent.TimeUnit

class MeteoRadarApp : Application() {

    override fun onCreate() {
        super.onCreate()
        NotificationHelper.createChannels(this)
        scheduleAlertWorker()
    }

    fun scheduleAlertWorker() {
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        val intervalMin = prefs.getString(Prefs.CHECK_INTERVAL, "30")?.toLongOrNull() ?: 30L

        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val request = PeriodicWorkRequestBuilder<WeatherAlertWorker>(intervalMin, TimeUnit.MINUTES)
            .setConstraints(constraints)
            .setBackoffCriteria(BackoffPolicy.LINEAR, 5, TimeUnit.MINUTES)
            .build()

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            WeatherAlertWorker.WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            request
        )
    }
}
