package it.shinyup.meteoradar

import android.app.Application
import androidx.work.*
import it.shinyup.meteoradar.utils.NotificationHelper
import it.shinyup.meteoradar.workers.WeatherAlertWorker
import java.util.concurrent.TimeUnit

class MeteoRadarApp : Application() {

    override fun onCreate() {
        super.onCreate()
        NotificationHelper.createChannels(this)
        scheduleAlertWorker()
    }

    private fun scheduleAlertWorker() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val request = PeriodicWorkRequestBuilder<WeatherAlertWorker>(30, TimeUnit.MINUTES)
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
