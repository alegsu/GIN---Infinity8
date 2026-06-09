package it.shinyup.meteoradar

import android.app.Application
import android.content.Context
import androidx.work.*
import it.shinyup.meteoradar.utils.NotificationHelper
import it.shinyup.meteoradar.workers.WeatherAlertWorker
import org.osmdroid.config.Configuration
import java.util.concurrent.TimeUnit

class MeteoRadarApp : Application() {

    override fun onCreate() {
        super.onCreate()
        Configuration.getInstance().load(this, getSharedPreferences("osmdroid", Context.MODE_PRIVATE))
        Configuration.getInstance().userAgentValue = packageName
        NotificationHelper.createChannels(this)
        scheduleAlertWorker()
    }

    private fun scheduleAlertWorker() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val request = PeriodicWorkRequestBuilder<WeatherAlertWorker>(15, TimeUnit.MINUTES)
            .setConstraints(constraints)
            .setBackoffCriteria(BackoffPolicy.LINEAR, 5, TimeUnit.MINUTES)
            .build()

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            WeatherAlertWorker.WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )
    }
}
